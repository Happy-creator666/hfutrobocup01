package hfutinvincible.tool.targetprovider;

import adf.core.agent.develop.DevelopData;
import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.ScenarioInfo;
import adf.core.agent.info.WorldInfo;
import adf.core.agent.module.ModuleManager;
import hfutinvincible.anno.AgentType;
import hfutinvincible.anno.Autowired;
import hfutinvincible.anno.HfutTool;
import hfutinvincible.tool.CalcContext;
import hfutinvincible.tool.Params;
import hfutinvincible.tool.UnavailableTargetTool;
import hfutinvincible.tool.UnavailableType;
import hfutinvincible.tool.criterion.*;
import hfutinvincible.tool.edge.AreaPassableTool;
import hfutinvincible.tool.edge.HFUTEdge;
import hfutinvincible.tool.human.BuriednessTool;
import hfutinvincible.tool.human.HumanPositionTool;
import hfutinvincible.tool.misc.OptionsTool;
import hfutinvincible.tool.partition.Partition;
import hfutinvincible.tool.partition.StaticPartitionTool;
import hfutinvincible.tool.path.InPartitionSearchPathTool;
import org.jblas.DoubleMatrix;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.stream.Stream;

import static hfutinvincible.tool.misc.MatrixUtils.linearMinMaxNormalization;
import static hfutinvincible.tool.misc.MatrixUtils.maxPositivization;

//@HfutTool
//@AgentType({AmbulanceTeam.class, FireBrigade.class, PoliceForce.class})
public class FbCriteriaBasedTargetProvider extends CriteriaBasedTargetProvider<Area> {
    @Autowired
    private WorldInfo worldInfo;

    // 防止因为在两个分区间移动导致前一个周期选中的目标下一个周期都不会作为候选，
    // 记录前一个周期的目标，并添加到这个周期的候选中
    private Area prevTarget;

    // Tools
    @Autowired
    private StaticPartitionTool staticPartitionTool;
    @Autowired
    private InPartitionSearchPathTool inPartitionSearchPathTool;
    @Autowired
    private AreaPassableTool areaPassableTool;
    @Autowired
    private BuriednessTool buriednessTool;

    // Criteria
    @Autowired
    private PathToTargetCriterion pathToTargetCriterion;
    @Autowired
    private HumanCountCriterion humanCountCriterion;
    @Autowired
    private BuriedHumanConditionCriterion buriedHumanConditionCriterion;
    @Autowired
    private AgentsSelectHereCriterion agentsSelectHereCriterion;
    @Autowired
    private PartitionCriterion partitionCriterion;
    @Autowired
    private HumanPositionTool humanPositionTool;
    @Autowired
    private UnavailableTargetTool unavailableTargetTool;
    @Autowired
    private PrevScoreCriterion prevScoreCriterion;
    @Autowired
    private HelpOuchCriterion helpOuchCriterion;

    protected final Params params;

    public FbCriteriaBasedTargetProvider() {
        this.params = new Params(getClass().getSimpleName());


        onInitialize(toolProvider -> {
            this.staticPartitionTool = toolProvider.get("StaticPartitionTool");
            this.pathToTargetCriterion = toolProvider.get("PathToTargetCriterion");
            this.humanCountCriterion = toolProvider.get("HumanCountCriterion");
            this.buriedHumanConditionCriterion = toolProvider.get("BuriedHumanConditionCriterion");
            this.agentsSelectHereCriterion = toolProvider.get("AgentsSelectHereCriterion");
            this.partitionCriterion = toolProvider.get("PartitionCriterion");
            this.unavailableTargetTool = toolProvider.get("UnavailableTargetTool");
            this.humanPositionTool = toolProvider.get("HumanPositionTool");
            this.prevScoreCriterion = toolProvider.get("PrevScoreCriterion");
            this.inPartitionSearchPathTool = toolProvider.get("InPartitionSearchPathTool");
            this.areaPassableTool = toolProvider.get("AreaPassableTool");
            this.buriednessTool = toolProvider.get("BuriednessTool");
            this.helpOuchCriterion = toolProvider.get("HelpOuchCriterion");

            setCriteria(
                    this.pathToTargetCriterion,
                    this.humanCountCriterion,
                    this.buriedHumanConditionCriterion,
                    this.agentsSelectHereCriterion,
                    this.pathToTargetCriterion,
                    this.partitionCriterion,
                    this.prevScoreCriterion,
                    this.helpOuchCriterion
            );
        });
    }

    @Override
    public void afterCalc(CalcContext context) {
        if (context.getTarget() != null)
            prevTarget = context.getTarget();
    }

    @Nonnull
    @Override
    protected List<Area> generateCandidates() {
        Set<Area> candidates = new HashSet<>();

        // 被分派到的分区内所有未被标记为 unavailable 的 building 加入
        Partition<EntityID> myAssignedPartition = staticPartitionTool.getAssignedPartition();
        myAssignedPartition.getElements()
                .filter(id -> !unavailableTargetTool.isUnavailable(
                        id,
                        UnavailableType.block,
                        UnavailableType.noBuried))
                .map(worldInfo::getEntity)
                .filter(Building.class::isInstance)
                .map(Building.class::cast)
                .forEach(candidates::add);

        // 指定周期内能到达的有被埋的人 / 市民的 buildings 加入
        Stream.concat(buriednessTool.getBuriedHumanIds(),
                        worldInfo.getEntityIDsOfType(StandardEntityURN.CIVILIAN).stream())
                .map(humanPositionTool::getPositionAreaId)
                .filter(Objects::nonNull)
                .filter(id -> !unavailableTargetTool.isUnavailable(
                        id,
                        UnavailableType.block,
                        UnavailableType.noBuried))
                .map(worldInfo::getEntity)
                .filter(Building.class::isInstance)
                .filter(b -> !(b instanceof Refuge))
                .map(Building.class::cast)
                .filter(building -> {
                    List<HFUTEdge> edges = areaPassableTool.getHfutEdgesOf(building.getID());
                    if (edges == null || edges.isEmpty())
                        return false;
                    double minPassCost = edges.stream()
                            .mapToDouble(inPartitionSearchPathTool::getCostOrDefault)
                            .min()
                            .orElse(Double.MAX_VALUE);
                    int minPassSteps = (int) (minPassCost / OptionsTool.MOVING_DISTANCE);
                    return minPassSteps < params.getInt("buriedHumanConsiderRange");
                })
                .forEach(candidates::add);

        // 上一周期的目标（如果有，且不是 unavailable 的）也加入
        if (prevTarget != null
                && !unavailableTargetTool.isUnavailable(
                prevTarget.getID(),
                UnavailableType.block,
                UnavailableType.noBuried))
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
                if (unavailableTargetTool.isUnavailable(
                        b.getID(),
                        UnavailableType.block,
                        UnavailableType.noBuried))
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

        // TODO 如果目标还不够，把因为 block 而 unavailable 的去掉再来


        return new ArrayList<>(candidates);
    }

    @Nonnull
    @Override
    protected DoubleMatrix evaluate(@Nonnull ColumnIndexedDoubleMatrix m) {

        final DoubleMatrix priorityOfBuriedHuman = m.getColumn(CriterionEnum.priorityOfBuriedHuman);
        final DoubleMatrix stepsByPassCostToTarget = m.getColumn(CriterionEnum.stepsByPassCostToTarget);
        final DoubleMatrix isInMyAssignedPartition = m.getColumn(CriterionEnum.isInMyAssignedPartition);
        final DoubleMatrix prevScore = m.getColumn(CriterionEnum.prevScore);
        final DoubleMatrix isPrevFirstLevelTarget = prevScore.ge(3.0); // 是否为前一个周期第一层级目标
        final DoubleMatrix isPrevOthersTarget = prevScore.gt(0.0); // 是否为前一个周期其他层级目标
        final DoubleMatrix helpOuchCount = m.getColumn(CriterionEnum.helpOuchCount);

        // 选出第一层最优目标
        final DoubleMatrix isFirstLevel = priorityOfBuriedHuman; // 而且上面有被埋的人

        // 第一层中目标排序：
        // 1. 通行代价越小越好
        // 2. 上面的人越重要越好
        final DoubleMatrix firstLevelScore = isFirstLevel
                .mul(linearMinMaxNormalization(maxPositivization(stepsByPassCostToTarget))
                        .mul(params.getDouble("1stw-stepsByPassCostToTarget"))
                        .add(linearMinMaxNormalization(priorityOfBuriedHuman)
                                .mul(params.getDouble("1stw-priorityOfBuriedHuman")))
                        .add(3.0) // 保证第一层的目标比其他目标得分高
                        .add(isPrevFirstLevelTarget));

        // 剩下的目标排序（相比警察，救护被堵住就走不了了，因此这里不显式筛去我所在分区内的）
        // 1. 通行代价越小越好
        // 2. 在我分派到的分区上的优先
        // 3. helpOuchCount 大的优先
        final DoubleMatrix othersScore = isFirstLevel.not()
                .mul(linearMinMaxNormalization(maxPositivization(stepsByPassCostToTarget))
                        .mul(params.getDouble("3stw-stepsByPassCostToTarget"))
                        .add(linearMinMaxNormalization(helpOuchCount)
                                .mul(params.getDouble("3stw-helpOuchCount")))
                        .mul(isInMyAssignedPartition.add(isInMyAssignedPartition.not().mul(0.1))) // 不在我目标分区中的值 *0.1
                        .add(isPrevOthersTarget)); // 保证其得分比其他同层级目标得分高，以防止反复换目标


        final DoubleMatrix finalScore = firstLevelScore.add(othersScore);

        m.addColumn("isFirstLevel", isFirstLevel);
        m.addColumn("firstLevelScore", firstLevelScore);
        m.addColumn("othersScore", othersScore);
        m.addColumn("finalScore", finalScore);

        return finalScore;
    }
}