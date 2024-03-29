package hfutinvincible.tool.targetprovider;

import adf.core.agent.Agent;
import adf.core.agent.communication.MessageManager;
import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.WorldInfo;
import adf.core.agent.precompute.PrecomputeData;
import hfutinvincible.Tuple2;
import hfutinvincible.anno.AgentType;
import hfutinvincible.anno.Autowired;
import hfutinvincible.anno.HfutTool;
import hfutinvincible.tool.*;
import hfutinvincible.tool.building.BuildingEntranceTool;
import hfutinvincible.tool.criterion.*;
import hfutinvincible.tool.edge.AreaPassableTool;
import hfutinvincible.tool.edge.HFUTEdge;
import hfutinvincible.tool.human.HumanPositionTool;
import hfutinvincible.tool.human.StuckInfoTool;
import hfutinvincible.tool.human.StuckType;
import hfutinvincible.tool.misc.OptionsTool;
import hfutinvincible.tool.partition.Partition;
import hfutinvincible.tool.partition.StaticPartitionTool;
import hfutinvincible.tool.path.InPartitionSearchPathTool;
import org.apache.log4j.Logger;
import org.jblas.DoubleMatrix;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static hfutinvincible.tool.misc.MatrixUtils.linearMinMaxNormalization;
import static hfutinvincible.tool.misc.MatrixUtils.maxPositivization;
import static java.util.stream.Collectors.toList;
@HfutTool
@AgentType({PoliceForce.class})
public class PfTargetProvider extends CriteriaBasedTargetProvider<Area> implements LifeCycle {
    @Autowired
    AgentInfo agentInfo;
    @Autowired
    WorldInfo worldInfo;
    @Autowired
    Logger logger;
// Data
    /** 记录每个 area 上被卡的人 */
    private final Map<EntityID, Set<EntityID>> stuckHumanMap;
    /** 记录每个 area 上有哪些人 */
    private final Map<EntityID, Set<EntityID>> humanOnPosMap;
    /** 上一个周期选择的目标 */
    private Area prevTarget;


    @Autowired
    StaticPartitionTool staticPartitionTool;

    @Autowired
    UnavailableTargetTool unavailableTargetTool;

    @Autowired
    StuckInfoTool stuckInfoTool;

    @Autowired
    HumanPositionTool humanPositionTool;

    @Autowired
    BuildingEntranceTool buildingEntranceTool;
    @Autowired
    InPartitionSearchPathTool inPartitionSearchPathTool;
    @Autowired
    AreaPassableTool areaPassableTool;
    /** Criterion*/
    @Autowired
    AreaEdgePairPassableCriterion areaEdgePairPassableCriterion;
    @Autowired
    BuildingEntranceProximityCriterion buildingEntranceProximityCriterion;
    @Autowired
    PathToTargetCriterion pathToTargetCriterion;
    @Autowired
    RefugeEntranceProximityCriterion refugeEntranceProximityCriterion;
    @Autowired
    StuckHumanConditionCriterion stuckHumanConditionCriterion;
    @Autowired
    UnavailableCriterion unavailableCriterion;
    @Autowired
    FbAndAtsTargetPathCriteria fbAndAtsTargetPathCriteria;
    @Autowired
    PartitionCriterion partitionCriterion;
    @Autowired
    TargetBlockCriterion targetBlockCriterion;
    @Autowired
    PrevScoreCriterion prevScoreCriterion;
    @Autowired
    HelpOuchCriterion helpOuchCriterion;

    private Params params = new Params(getClass().getSimpleName());

    public PfTargetProvider(){
        this.stuckHumanMap = new HashMap<>();
        this.humanOnPosMap = new HashMap<>();
    }

    @Override
    public void resume(PrecomputeData precomputeData) {
        setCriteria(
                areaEdgePairPassableCriterion,
                buildingEntranceProximityCriterion,
                pathToTargetCriterion,
                refugeEntranceProximityCriterion,
                stuckHumanConditionCriterion,
                unavailableCriterion,
                fbAndAtsTargetPathCriteria,
                partitionCriterion,
                targetBlockCriterion,
                prevScoreCriterion,
                helpOuchCriterion
        );
    }

    @Override
    public void updateInfo(MessageManager messageManager) {
        this.stuckHumanMap.clear();
        this.stuckInfoTool.getStuckHumanIds(OptionsTool.IGNORE_STUCK_HUMAN_AFTER)
                .forEach(humanId -> {
                    EntityID humanPosAreaId = humanPositionTool.getPositionAreaId(humanId);
                    this.stuckHumanMap.compute(humanPosAreaId, (k, v) -> {
                        Set<EntityID> ret = v == null ? new HashSet<>() : v;
                        ret.add(humanId);
                        return ret;
                    });
                });

        // 更新 humanOnPosMap
        this.humanOnPosMap.clear();
        this.humanPositionTool.recordMap.forEach((humanId, value) -> {
            EntityID humanPosId = value.getHumanPosId();
            this.humanOnPosMap.compute(humanPosId, (k, v) -> {
                Set<EntityID> ret = v == null ? new HashSet<>() : v;
                ret.add(humanId);
                return ret;
            });
        });
    }

    @Override
    public void afterCalc(CalcContext context) {
        Area target = context.getTarget();
        if(target!= null){
            prevTarget=target;
        }
    }

    @Nonnull
    @Override
    protected List<Area> generateCandidates() {
        Set<Area> candidates = new HashSet<>();

        // 分区里的建筑入口
        staticPartitionTool.getWorkingPartition()
                .getElements()
                .filter(id -> !unavailableTargetTool.isUnavailable(
                        id, UnavailableType.pfNoAction))
                .map(worldInfo::getEntity)
                .filter(Road.class::isInstance) // 只考虑 road，忽略所有建筑
                .map(Area.class::cast)
                .forEach(candidates::add);
        // 卡住人的路
        stuckInfoTool.getStuckHumanIds(OptionsTool.IGNORE_STUCK_HUMAN_AFTER)
                .map(stuckHumanId -> humanPositionTool.getPositionAreaId(stuckHumanId))
                .filter(Objects::nonNull)
                .filter(id -> !unavailableTargetTool.isUnavailable(
                        id, UnavailableType.pfNoAction))
                .flatMap(stuckHumanAreaId -> { // 将卡住的人所在的 area 和其周围 area 加入
                    Area stuckHumanArea = (Area) worldInfo.getEntity(stuckHumanAreaId);
                    if (stuckHumanArea == null)
                        return Stream.of();
                    Stream<Area> neighbors = stuckHumanArea.getNeighbours()
                            .stream()
                            .map(worldInfo::getEntity)
                            .filter(Area.class::isInstance)
                            .map(Area.class::cast);
                    return Stream.concat(Stream.of(stuckHumanArea), neighbors);
                })
                .filter(area -> {
                    List<HFUTEdge> edges = areaPassableTool.getHfutEdgesOf(area.getID());
                    if (edges == null || edges.isEmpty())
                        return false;
                    double minPassCost = edges.stream()
                            .mapToDouble(inPartitionSearchPathTool::getCostOrDefault)
                            .min()
                            .orElse(Double.MAX_VALUE);
                    int minPassSteps = (int) (minPassCost / OptionsTool.MOVING_DISTANCE);
                    return minPassSteps < 5;
                })
                .forEach(candidates::add);

        if (prevTarget != null && !unavailableTargetTool.isUnavailable(prevTarget.getID(), UnavailableType.pfNoAction))
            candidates.add(prevTarget);

        return new ArrayList<>(candidates);
    }

    @Override
    @Nonnull
    protected DoubleMatrix evaluate(@Nonnull ColumnIndexedDoubleMatrix m) {

        final DoubleMatrix priorityOfStuckHuman = m.getColumn(CriterionEnum.priorityOfStuckHuman);
        final DoubleMatrix isRefugeEntrance = m.getColumn(CriterionEnum.isRefugeEntrance);
        final DoubleMatrix impassableRatio = m.getColumn(CriterionEnum.impassableRatio);
        final DoubleMatrix atTargetPathPriority = m.getColumn(CriterionEnum.atTargetPathPriority);
        final DoubleMatrix fbTargetPathPriority = m.getColumn(CriterionEnum.fbTargetPathPriority);
        final DoubleMatrix buildingEntranceProximity = m.getColumn(CriterionEnum.buildingEntranceProximity);
        final DoubleMatrix isBuildingEntrance = buildingEntranceProximity.ge(0.5);
        // TODO isAreaOnCvsTargetPath
        final DoubleMatrix stepsByPassCostToTarget = m.getColumn(CriterionEnum.stepsByPassCostToTarget);
        final DoubleMatrix totalVisitCount = m.getColumn(CriterionEnum.totalLastVisitTime);
        final DoubleMatrix pfCleanPathOverlapRatio = m.getColumn(CriterionEnum.pfCleanPathOverlapRatio);
        final DoubleMatrix refugeEntranceProximity = m.getColumn(CriterionEnum.refugeEntranceProximity);
        final DoubleMatrix blockRatio = m.getColumn(CriterionEnum.blockRatio);
        final DoubleMatrix isInMyAssignedPartition = m.getColumn(CriterionEnum.isInMyAssignedPartition);
        final DoubleMatrix blockTargetPriority = m.getColumn(CriterionEnum.blockTargetPriority);
        final DoubleMatrix prevScore = m.getColumn(CriterionEnum.prevScore);
        final DoubleMatrix isPrevFirstLevelTarget = prevScore.ge(5.0); // 是否为前一个周期第一层级目标
        final DoubleMatrix isPrevSecondLevelTarget = prevScore.ge(3.0); // 是否为前一个周期第二层级目标
        final DoubleMatrix isPrevThirdLevelTarget = prevScore.ge(1.0); // 是否为前一个周期第三层级目标
        final DoubleMatrix isPrevOthersTarget = prevScore.gt(0.0); // 是否为前一个周期其他层级目标
        final DoubleMatrix helpOuchCount = m.getColumn(CriterionEnum.helpOuchCount);

        // 选出第一层最优先目标
        // 1. 上面有被卡的人
        // 2. 堵住（不知道，中心线被堵，完全被堵都算），而且是以下三种情况之一：
        //     2.1. 是 refuge 入口
        //     2.2. 是 at 的目标路径
        //     2.3. 是 fb 的目标路径
        final DoubleMatrix isFirstLevel = priorityOfStuckHuman.or(
                impassableRatio.and(
                        isRefugeEntrance.or(atTargetPathPriority).or(fbTargetPathPriority)));

        // 第一层中目标排序：
        // 1. 通行代价越小越好
        // 2. 上面的人越重要越好
        // 3. 和别的 pf 清理路径重合越少越好
        final DoubleMatrix firstLevelScore = isFirstLevel
                .mul(linearMinMaxNormalization(maxPositivization(stepsByPassCostToTarget))
                        .mul(params.getDouble("1stw-stepsByPassCostToTarget"))
                        .add(linearMinMaxNormalization(priorityOfStuckHuman)
                                .mul(params.getDouble("1stw-priorityOfStuckHuman")))
                        .add(linearMinMaxNormalization(maxPositivization(pfCleanPathOverlapRatio))
                                .mul(params.getDouble("1stw-pfCleanPathOverlapRatio")))
                        .add(5.0) // 保证第一层的目标比其他目标得分高
                        .add(isPrevFirstLevelTarget)); // 保证其得分比其他同层级目标得分高，以防止反复换目标

        // 选出第二层次优目标
        final DoubleMatrix isSecondLevel = isFirstLevel.not() // 如果不是第一层
                .and(impassableRatio) // 如果堵住了
                .and(blockTargetPriority); // 而且是妨碍其他智能体的 area

        // 第二层中目标排序
        // 1. 通行代价越小越好
        // 2. 越妨碍其他智能体，越优先
        final DoubleMatrix secondLevelScore = isSecondLevel
                .mul(linearMinMaxNormalization(maxPositivization(stepsByPassCostToTarget))
                        .mul(params.getDouble("2stw-stepsByPassCostToTarget"))
                        .add(linearMinMaxNormalization(blockTargetPriority)
                                .mul(params.getDouble("2stw-blockTargetPriority")))
                        .add(3.0) // 保证第二层的目标比除第一层外其他目标得分高
                        .add(isPrevSecondLevelTarget)); // 保证其得分比其他同层级目标得分高，以防止反复换目标

        // 选出第三层目标
        final DoubleMatrix isThirdLevel = isFirstLevel.not() // 不是第一层
                .and(isSecondLevel.not()) // 也不是第二层
                .and(impassableRatio) // 堵住的
                .and(isBuildingEntrance); // 建筑入口

        // 第三层中目标排序
        // 1. 通行代价越小越好
        // 2. helpOuchCount 越大越好
        final DoubleMatrix thirdLevelScore = isThirdLevel
                .mul(linearMinMaxNormalization(maxPositivization(stepsByPassCostToTarget))
                        .mul(params.getDouble("3stw-stepsByPassCostToTarget"))
                        .add(linearMinMaxNormalization(helpOuchCount)
                                .mul(params.getDouble("3stw-helpOuchCount")))
                        .add(1.0) // 保证第三层的目标比除第一、二层外其他目标得分高
                        .add(isPrevThirdLevelTarget));

        // 剩下的目标中，选出在我被分配到的分区中的排序：（警察哪里都去得，因此直接排除我所在分区中的）
        // 1. 越接近 refuge 的入口越好
        // 2. 越是我没去过的地方越好
        // 3. 一路上能清理的障碍越多越好（堵塞程度越大越好）
        // 4. 在我分派到的分区上的优先
        // 5. helpOuchCount 越大越好
        final DoubleMatrix othersScore = isFirstLevel.not()
                .and(isSecondLevel.not())
                .and(isThirdLevel.not())
                .mul(linearMinMaxNormalization(refugeEntranceProximity)
                        .mul(params.getDouble("4stw-refugeEntranceProximity"))
                        .add(linearMinMaxNormalization(maxPositivization(totalVisitCount))
                                .mul(params.getDouble("4stw-totalVisitCount")))
                        .add(linearMinMaxNormalization(blockRatio)
                                .mul(params.getDouble("4stw-blockRatio")))
                        .add(linearMinMaxNormalization(helpOuchCount)
                                .mul(params.getDouble("4stw-helpOuchCount")))
                        .mul(isInMyAssignedPartition.add(isInMyAssignedPartition.not().mul(0.1))) // 不在我目标分区中的值 *0.1
                        .add(isPrevOthersTarget)); // 保证其得分比其他同层级目标得分高，以防止反复换目标

        final DoubleMatrix finalScore = firstLevelScore.add(secondLevelScore).add(thirdLevelScore).add(othersScore);

        m.addColumn("isFirstLevel", isFirstLevel);
        m.addColumn("isSecondLevel", isSecondLevel);
        m.addColumn("isThirdLevel", isThirdLevel);
        m.addColumn("firstLevelScore", firstLevelScore);
        m.addColumn("secondLevelScore", secondLevelScore);
        m.addColumn("thirdLevelScore", thirdLevelScore);
        m.addColumn("othersScore", othersScore);
        m.addColumn("finalScore", finalScore);

        return finalScore;
    }
}
