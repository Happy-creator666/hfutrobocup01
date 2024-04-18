package hfutinvincible.tool.targetprovider;

import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.WorldInfo;
import adf.core.agent.precompute.PrecomputeData;
import hfutinvincible.Tuple2;
import hfutinvincible.anno.AgentType;
import hfutinvincible.anno.Autowired;
import hfutinvincible.anno.HfutTool;
import hfutinvincible.tool.*;
import hfutinvincible.tool.building.RefugeConditionTool;
import hfutinvincible.tool.comm.MessageManageTool;
import hfutinvincible.tool.criterion.*;
import hfutinvincible.tool.edge.AreaPassableTool;
import hfutinvincible.tool.edge.HFUTEdge;
import hfutinvincible.tool.human.BuriednessTool;
import hfutinvincible.tool.human.HumanPositionTool;
import hfutinvincible.tool.human.TimeToDeadTool;
import hfutinvincible.tool.misc.OptionsTool;
import hfutinvincible.tool.partition.Partition;
import hfutinvincible.tool.partition.StaticPartitionTool;
import hfutinvincible.tool.path.InPartitionSearchPathTool;
import org.apache.log4j.Logger;
import org.jblas.DoubleMatrix;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static hfutinvincible.tool.misc.MatrixUtils.linearMinMaxNormalization;
import static hfutinvincible.tool.misc.MatrixUtils.maxPositivization;

@HfutTool
@AgentType({AmbulanceTeam.class})
public class AtTargetProvider extends CriteriaBasedTargetProvider<Area> implements LifeCycle {


    // 防止因为在两个分区间移动导致前一个周期选中的目标下一个周期都不会作为候选，
    // 记录前一个周期的目标，并添加到这个周期的候选中
    private Area prevTarget;

    @Autowired
    AgentInfo agentInfo;
    @Autowired
    WorldInfo worldInfo;
    @Autowired
    Logger logger;
    //Tools
    @Autowired
    StaticPartitionTool staticPartitionTool;
    @Autowired
    HumanPositionTool humanPositionTool;
    @Autowired
    MessageManageTool messageTool;
    @Autowired
    InPartitionSearchPathTool inPartitionSearchPathTool;
    @Autowired
    AreaPassableTool areaPassableTool;
    @Autowired
    TimeToDeadTool timeToDeadTool;
    @Autowired
    UnavailableTargetTool unavailableTargetTool;
    //Params
    private Params params = new Params(getClass().getSimpleName());
    /** Criterion*/
    @Autowired
    PathToTargetCriterion pathToTargetCriterion;
    @Autowired
    AgentsSelectHereCriterion agentsSelectHereCriterion;
    @Autowired
    HumanCountCriterion humanCountCriterion;
    @Autowired
    HurtHumanConditionCriterion hurtHumanConditionCriterion;
    @Autowired
    PartitionCriterion partitionCriterion;
    @Autowired
    EntityTypeCriterion entityTypeCriterion;
    @Autowired
    RefugeConditionCriterion refugeConditionCriterion;
    @Autowired
    PrevScoreCriterion prevScoreCriterion;
    @Autowired
    HelpOuchCriterion helpOuchCriterion;

    @Override
    public void resume(PrecomputeData precomputeData) {
        setCriteria(
                this.pathToTargetCriterion,
                this.agentsSelectHereCriterion,
                this.hurtHumanConditionCriterion,
                this.partitionCriterion,
                this.humanCountCriterion,
                this.entityTypeCriterion,
                this.refugeConditionCriterion,
                this.prevScoreCriterion,
                this.helpOuchCriterion
        );
    }

    @Nonnull
    @Override
    protected List<Area> generateCandidates() {
        boolean withHumanLoaded = agentInfo.someoneOnBoard() != null;
        Set<Area> candidates = new HashSet<>();
        if(withHumanLoaded){
            // 如果装了人，则将所有 refuges 作为目标
            // 无法准确的获取refuges的剩余容量
            for (StandardEntity e : worldInfo.getEntitiesOfType(StandardEntityURN.REFUGE))
                if (e instanceof Refuge refuge)
                    candidates.add(refuge);
        } else {
            // 被分派到的分区内所有未被标记为 unavailable 的 building 加入
            Partition<EntityID> myAssignedPartition = staticPartitionTool.getAssignedPartition();
            myAssignedPartition.getElements()
                    .filter(id -> !unavailableTargetTool.isUnavailable(
                            id,
                            UnavailableType.block,
                            UnavailableType.noHurtNotSure,
                            UnavailableType.noHurtDefinitely,
                            UnavailableType.buried))
                    .map(worldInfo::getEntity)
                    .filter(Building.class::isInstance)
                    .map(Building.class::cast)
                    .filter(b -> !(b instanceof Refuge)) // 先不考虑 refuge
                    .forEach(candidates::add);

            // 指定周期内能到达的有受伤的人的 buildings 加入
            Stream.concat(timeToDeadTool.getHurtHumanIds(),
                            worldInfo.getEntityIDsOfType(StandardEntityURN.CIVILIAN).stream())
                    .map(humanPositionTool::getPositionAreaId)
                    .filter(Objects::nonNull)
                    .filter(id -> !unavailableTargetTool.isUnavailable(
                            id,
                            UnavailableType.block,
                            UnavailableType.noHurtNotSure,
                            UnavailableType.noHurtDefinitely,
                            UnavailableType.buried))
                    .map(worldInfo::getEntity)
                    .filter(Building.class::isInstance)
                    .map(Building.class::cast)
                    .filter(b -> !(b instanceof Refuge))
                    .filter(building -> {
                        List<HFUTEdge> edges = areaPassableTool.getHfutEdgesOf(building.getID());
                        if (edges == null || edges.isEmpty())
                            return false;
                        double minPassCost = edges.stream()
                                .mapToDouble(inPartitionSearchPathTool::getCostOrDefault)
                                .min()
                                .orElse(Double.MAX_VALUE);
                        int minPassSteps = (int) (minPassCost / OptionsTool.MOVING_DISTANCE);
                        return minPassSteps < params.getInt("hurtHumanConsiderRange");
                    })
                    .forEach(candidates::add);

            // 上一周期的目标（如果有，且不是 unavailable 的）也加入
            if (prevTarget != null
                    && !(prevTarget instanceof Refuge)
                    && !unavailableTargetTool.isUnavailable(
                    prevTarget.getID(),
                    UnavailableType.block,
                    UnavailableType.noHurtNotSure,
                    UnavailableType.noHurtDefinitely,
                    UnavailableType.buried))
                candidates.add(prevTarget);

            // 如果目标不够，再将以我为中心，通行代价递增，逐步加入目标
            List<Building> allBuildings = worldInfo.getAllEntities()
                    .stream()
                    .filter(Building.class::isInstance)
                    .map(Building.class::cast)
                    .toList();
            int start = params.getInt("additionalCandidatesStartRadius");
            int max = params.getInt("additionalCandidatesMaxRadius");
            int delta = params.getInt("additionalCandidatesDeltaRadius");
            List<Building> additionalCandidates = new ArrayList<>();
            for (int i = start; i < max; i += delta) {
                additionalCandidates.clear();
                for (Building b : allBuildings) {
                    if (b instanceof Refuge) // 忽略 refuge
                        continue;
                    if (unavailableTargetTool.isUnavailable(
                            b.getID(),
                            UnavailableType.block,
                            UnavailableType.noHurtNotSure,
                            UnavailableType.noHurtDefinitely,
                            UnavailableType.buried))
                        continue;
                    List<HFUTEdge> edges = areaPassableTool.getHfutEdgesOf(b.getID());
                    if (edges == null || edges.isEmpty())
                        continue;
                    double minPassCost = edges.stream()
                            .mapToDouble(inPartitionSearchPathTool::getCostOrDefault)
                            .min()
                            .orElse(Double.MAX_VALUE);
                    int minPassSteps = (int) (minPassCost / OptionsTool.MOVING_DISTANCE);
                    if (minPassSteps < i)
                        additionalCandidates.add(b);
                }
                int enoughTargetsThreshold = params.getInt("enoughTargetsThreshold");
                if (candidates.size() + additionalCandidates.size() > enoughTargetsThreshold)
                    break;
            }
            candidates.addAll(additionalCandidates);
        }
        logger.info("the time is "+agentInfo.getTime()+"candidates is"+candidates);
        return new ArrayList<>(candidates);
    }

    @Nonnull
    @Override
    protected DoubleMatrix evaluate(@Nonnull ColumnIndexedDoubleMatrix m) {
        if (agentInfo.someoneOnBoard() == null)
            return evaluateWithoutHumanLoaded(m);
        else return evaluateWithHumanLoaded(m);
    }

    private DoubleMatrix evaluateWithHumanLoaded(@Nonnull ColumnIndexedDoubleMatrix m) {

        final DoubleMatrix stepsByPassCostToTarget = m.getColumn(CriterionEnum.stepsByPassCostToTarget);
        final DoubleMatrix isRefuge = m.getColumn(CriterionEnum.isRefuge);
        final DoubleMatrix freeBedNumOfRefuge = m.getColumn(CriterionEnum.freeBedsCondition);
        final DoubleMatrix isInMyAssignedPartition = m.getColumn(CriterionEnum.isInMyAssignedPartition);
        final DoubleMatrix prevScore = m.getColumn(CriterionEnum.prevScore);
        final DoubleMatrix isPrevFirstLevelTarget = prevScore.ge(3.0); // 是否为前一个周期第一层级目标
        final DoubleMatrix isPrevOthersTarget = prevScore.gt(0.0); // 是否为前一个周期其他层级目标
        final DoubleMatrix helpOuchCount = m.getColumn(CriterionEnum.helpOuchCount);

        // 选出第一层最优目标
        final DoubleMatrix isFirstLevel = isRefuge;

        // 第一层中目标排序
        // 1. 空床位数越多越好
        // 2. 通行代价越小越好
        final DoubleMatrix firstLevelScore = isFirstLevel
                .mul(linearMinMaxNormalization(freeBedNumOfRefuge)
                        .mul(params.getDouble("withHumanOnLoad.1stw-freeBedNumOfRefuge"))
                        .add(linearMinMaxNormalization(maxPositivization(stepsByPassCostToTarget))
                                .mul(params.getDouble("withHumanOnLoad.1stw-stepsByPassCostToTarget")))
                        .add(3.0) // 保证第一层的目标比其他目标得分高
                        .add(isPrevFirstLevelTarget)); // 保证其得分比其他同层级目标得分高，以防止反复换目标

        // 剩下的目标
        // 1. 通行代价越小越好
        // 2. 在我分派到的分区上的优先
        // 3. helpOuchCount 越大越好
        final DoubleMatrix othersScore = isFirstLevel.not()
                .mul(linearMinMaxNormalization(maxPositivization(stepsByPassCostToTarget))
                        .mul(params.getDouble("withHumanOnLoad.3stw-stepsByPassCostToTarget"))
                        .add(linearMinMaxNormalization(helpOuchCount)
                                .mul(params.getDouble("withHumanOnLoad.3stw-helpOuchCount")))
                        .mul(isInMyAssignedPartition.add(isInMyAssignedPartition.not().mul(0.1))) // 不在我目标分区中的值 *0.1
                        .add(isPrevOthersTarget));  // 保证其得分比其他同层级目标得分高，以防止反复换目标

        final DoubleMatrix finalScore = firstLevelScore.add(othersScore);

        m.addColumn("isFirstLevel", isFirstLevel);
        m.addColumn("firstLevelScore", firstLevelScore);
        m.addColumn("othersScore", othersScore);
        m.addColumn("finalScore", finalScore);

        return finalScore;
    }

    private DoubleMatrix evaluateWithoutHumanLoaded(@Nonnull ColumnIndexedDoubleMatrix m) {

        final DoubleMatrix priorityOfHurtHuman = m.getColumn(CriterionEnum.priorityOfHurtHuman);
        final DoubleMatrix stepsByPassCostToTarget = m.getColumn(CriterionEnum.stepsByPassCostToTarget);
        final DoubleMatrix isInMyAssignedPartition = m.getColumn(CriterionEnum.isInMyAssignedPartition);
        final DoubleMatrix prevScore = m.getColumn(CriterionEnum.prevScore);
        final DoubleMatrix isPrevFirstLevelTarget = prevScore.ge(2.0); // 是否为前一个周期第一层级目标
        final DoubleMatrix isPrevOthersTarget = prevScore.gt(0.0); // 是否为前一个周期其他层级目标
        final DoubleMatrix helpOuchCount = m.getColumn(CriterionEnum.helpOuchCount);

        // 选出第一层最优目标
        final DoubleMatrix isFirstLevel = priorityOfHurtHuman; // 而且上面有受伤的人

        // 第一层中目标排序：
        // 1. 通行代价越小越好
        // 2. 上面的人越重要越好
        final DoubleMatrix firstLevelScore = isFirstLevel
                .mul(linearMinMaxNormalization(maxPositivization(stepsByPassCostToTarget))
                        .mul(params.getDouble("withoutHumanOnLoad.1stw-stepsByPassCostToTarget"))
                        .add(linearMinMaxNormalization(priorityOfHurtHuman)
                                .mul(params.getDouble("withoutHumanOnLoad.1stw-priorityOfHurtHuman")))
                        .add(3.0) // 保证第一层的目标比其他目标得分高
                        .add(isPrevFirstLevelTarget)); // 保证其得分比其他同层级目标得分高，以防止反复换目标

        // 剩下的目标排序（相比警察，救护被堵住就走不了了，因此这里不显式筛去我所在分区内的）
        // 1. 通行代价越小越好
        // 2. 在我分派到的分区上的优先
        // 3. helpOuchCount 越大越好
        final DoubleMatrix othersScore = isFirstLevel.not()
                .mul(linearMinMaxNormalization(maxPositivization(stepsByPassCostToTarget))
                        .mul(params.getDouble("withoutHumanOnLoad.3stw-stepsByPassCostToTarget"))
                        .add(linearMinMaxNormalization(helpOuchCount)
                                .mul(params.getDouble("withoutHumanOnLoad.3stw-helpOuchCount")))
                        .mul(isInMyAssignedPartition.add(isInMyAssignedPartition.not().mul(0.1))) // 不在我目标分区中的值 *0.1
                        .add(isPrevOthersTarget)); // 保证其得分比其他同层级目标得分高，以防止反复换目标

        final DoubleMatrix finalScore = firstLevelScore.add(othersScore);

        m.addColumn("isFirstLevel", isFirstLevel);
        m.addColumn("firstLevelScore", firstLevelScore);
        m.addColumn("othersScore", othersScore);
        m.addColumn("finalScore", finalScore);

        return finalScore;
    }

    @Override
    public void afterCalc(CalcContext context) {
        if(context.getTarget()!=null){
            prevTarget =context.getTarget();
        }
    }
}

