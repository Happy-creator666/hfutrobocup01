package hfutinvincible.tool.targetprovider;

import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.WorldInfo;
import adf.core.agent.precompute.PrecomputeData;
import hfutinvincible.Tuple2;
import hfutinvincible.anno.AgentType;
import hfutinvincible.anno.Autowired;
import hfutinvincible.anno.HfutTool;
import hfutinvincible.tool.*;
import hfutinvincible.tool.criterion.*;
import hfutinvincible.tool.edge.AreaPassableTool;
import hfutinvincible.tool.edge.HFUTEdge;
import hfutinvincible.tool.human.BuriednessTool;
import hfutinvincible.tool.human.HumanPositionTool;
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

@HfutTool
@AgentType({FireBrigade.class})
public class FbTargetProvider extends CriteriaBasedTargetProvider<Area> implements LifeCycle{

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
    UnavailableTargetTool unavailableTargetTool;
    @Autowired
    BuriednessTool buriednessTool;
    @Autowired
    HumanPositionTool humanPositionTool;
    @Autowired
    AreaPassableTool areaPassableTool;
    @Autowired
    InPartitionSearchPathTool inPartitionSearchPathTool;
    /** Criterion*/
    @Autowired
    PathToTargetCriterion pathToTargetCriterion;
    @Autowired
    HumanCountCriterion humanCountCriterion;
    @Autowired
    BuriedHumanConditionCriterion buriedHumanConditionCriterion;
    @Autowired
    AgentsSelectHereCriterion agentsSelectHereCriterion;
    @Autowired
    PartitionCriterion partitionCriterion;
    @Autowired
    PrevScoreCriterion prevScoreCriterion;
    @Autowired
    HelpOuchCriterion helpOuchCriterion;
    //Params
    private Params params = new Params(getClass().getSimpleName());

    @Override
    public void resume(PrecomputeData precomputeData) {
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

        logger.info("the time is "+agentInfo.getTime()+"candidates is"+candidates);
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

//    @Override
//    public List<Tuple2<Area, Double>> getSortedCandidatesAndScores() {
//        Map<Area, Integer> TargetWithItsPriority = new HashMap<>();
//        List<Area> candidates = new ArrayList<>();
//
//
//        // 被分派到的分区内所有未被标记为 unavailable 的 building 加入
//        Partition<EntityID> myAssignedPartition = staticPartitionTool.getAssignedPartition();
//        myAssignedPartition.getElements()
//                .filter(id -> !unavailableTargetTool.isUnavailable(
//                        id,
//                        UnavailableType.block,
//                        UnavailableType.noBuried))
//                .map(worldInfo::getEntity)
//                .filter(Building.class::isInstance)
//                .map(Building.class::cast)
//                .forEach(candidates::add);
//
//        // 指定周期内能到达的有被埋的人 / 市民的 buildings 加入
//        Stream.concat(buriednessTool.getBuriedHumanIds(),
//                        worldInfo.getEntityIDsOfType(StandardEntityURN.CIVILIAN).stream())
//                .map(humanPositionTool::getPositionAreaId)
//                .filter(Objects::nonNull)
//                .filter(id -> !unavailableTargetTool.isUnavailable(
//                        id,
//                        UnavailableType.block,
//                        UnavailableType.noBuried))
//                .map(worldInfo::getEntity)
//                .filter(Building.class::isInstance)
//                .filter(b -> !(b instanceof Refuge))
//                .map(Building.class::cast)
//                .filter(building -> {
//                    List<HFUTEdge> edges = areaPassableTool.getHfutEdgesOf(building.getID());
//                    if (edges == null || edges.isEmpty())
//                        return false;
//                    double minPassCost = edges.stream()
//                            .mapToDouble(inPartitionSearchPathTool::getCostOrDefault)
//                            .min()
//                            .orElse(Double.MAX_VALUE);
//                    int minPassSteps = (int) (minPassCost / OptionsTool.MOVING_DISTANCE);
//                    return minPassSteps < params.getInt("buriedHumanConsiderRange");
//                })
//                .forEach(candidates::add);
//
//        // 上一周期的目标（如果有，且不是 unavailable 的）也加入
//        if (prevTarget != null
//                && !unavailableTargetTool.isUnavailable(
//                prevTarget.getID(),
//                UnavailableType.block,
//                UnavailableType.noBuried))
//            candidates.add(prevTarget);
//
//        // 如果目标不够，再将以我为中心，通行代价递增，逐步加入目标
//        List<Building> allBuildings = worldInfo.getAllEntities()
//                .stream()
//                .filter(Building.class::isInstance)
//                .map(Building.class::cast)
//                .toList();
//        int start = params.getInt("additionalCandidatesStartRadius");
//        int max = params.getInt("additionalCandidatesMaxRadius");
//        int delta = params.getInt("additionalCandidatesDeltaRadius");
//        List<Building> additionalCandidates = new ArrayList<>();
//        for (int i = start; i < max; i += delta) {
//            additionalCandidates.clear();
//            for (Building b : allBuildings) {
//                if (unavailableTargetTool.isUnavailable(
//                        b.getID(),
//                        UnavailableType.block,
//                        UnavailableType.noBuried))
//                    continue;
//                List<HFUTEdge> edges = areaPassableTool.getHfutEdgesOf(b.getID());
//                if (edges == null || edges.isEmpty())
//                    continue;
//                double minPassCost = edges.stream()
//                        .mapToDouble(inPartitionSearchPathTool::getCostOrDefault)
//                        .min()
//                        .orElse(Double.MAX_VALUE);
//                int minPassSteps = (int) (minPassCost / OptionsTool.MOVING_DISTANCE);
//                if (minPassSteps < i)
//                    additionalCandidates.add(b);
//            }
//            int enoughTargetsThreshold = params.getInt("enoughTargetsThreshold");
//            if (candidates.size() + additionalCandidates.size() > enoughTargetsThreshold)
//                break;
//        }
//        candidates.addAll(additionalCandidates);
//
//        // TODO 如果目标还不够，把因为 block 而 unavailable 的去掉再来
//
//        //Todo 需要更改策略的优先级
//
//        /** 将候选区域中的所有目标的数值设置为2也就是第三个层级 目前策略随意假设的*/
//        candidates.stream()
//                .map(buildings -> (Area) buildings)
//                .forEach(areas -> TargetWithItsPriority.put(areas, 2));
//        /** 将Refuge的类型设置为最高层*/
//        candidates.stream()
//                .filter(buildings -> buildings instanceof Refuge)
//                .map(refuges -> (Area) refuges)
//                .forEach(building -> TargetWithItsPriority.put(building, 0));
//
//        /** 如果在candidates中找到其中存在有受伤的人的building将其优先级设为第二层*/
////        candidates.stream()
////                .filter(builds -> builds instanceof Building)
////                .map(Building.class::cast)
////                .filter(b -> !(b instanceof Refuge))
////                .filter(building -> {
////                    List<HFUTEdge> edges = areaPassableTool.getHfutEdgesOf(building.getID());
////                    if (edges == null || edges.isEmpty())
////                        return false;
////                    double minPassCost = edges.stream()
////                            .mapToDouble(inPartitionSearchPathTool::getCostOrDefault)
////                            .min()
////                            .orElse(Double.MAX_VALUE);
////                    int minPassSteps = (int) (minPassCost / OptionsTool.MOVING_DISTANCE);
////                    return minPassSteps < params.getInt("hurtHumanConsiderRange");
////                })
////                .forEach(building -> TargetWithItsPriority.put(building, 1));
//        return TargetWithItsPriority.entrySet().stream()
//                .sorted(Map.Entry.<Area,Integer>comparingByValue())
//                .map(res -> Tuple2.of(res.getKey(),(double)res.getValue()))
//                .collect(Collectors.toList());
//    }
//
//    @Override
//    public void afterCalc(CalcContext context) {
//        if(context.getTarget()!=null){
//            prevTarget =context.getTarget();
//        }
//    }
//    // Todo 还需要修改权值 根据不同的重要程度进行权值的修改
//    /** key: candidates 对应的Area  value 对应区域各类人员的数量*/
//    public Map<Area,Integer> getHumanCount(List<Area> candidates){
//        int candidateNum = candidates.size();
//        Map<Integer,List<Integer>> humanPriority = new HashMap<>();
//        for (int i = 0; i <candidateNum ; i++) {
//            Area area = candidates.get(i);
//            int Cvnum = 0;
//            int Pfnum = 0;
//            int Atnum = 0;
//            int Fbnum = 0;
//            List<Integer> humanCount = new ArrayList<>();
//            Set<EntityID> humanIdsOnArea = humanPositionTool.getHumanIdsOnArea(area.getID());
//            for (EntityID humanId:humanIdsOnArea) {
//                Human human =(Human) worldInfo.getEntity(humanId);
//                if (human instanceof Civilian)              Cvnum += 1;
//                else if (human instanceof PoliceForce)      Pfnum += 1;
//                else if (human instanceof AmbulanceTeam)    Atnum += 1;
//                else if (human instanceof FireBrigade)      Fbnum += 2;
//            }
//            humanCount.add(Cvnum);
//            humanCount.add(Pfnum);
//            humanCount.add(Atnum);
//            humanCount.add(Fbnum);
//            humanPriority.put(i,humanCount);
//        }
//        Map<Area,Integer> HumanBuriedPriority = new HashMap<>();
//        for (int i = 0; i <candidateNum ; i++) {
//            int Priority =0;
//            for (int j = 0;j<humanPriority.get(i).size();j++) {
//                Priority +=humanPriority.get(i).get(j);
//            }
//            for (int z = 0; z <candidateNum ; z++) {
//                HumanBuriedPriority.put(candidates.get(i),Priority );
//            }
//        }
//        return HumanBuriedPriority;
//    }

}

