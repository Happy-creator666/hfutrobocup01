package hfutinvincible.tool.targetprovider;

import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.WorldInfo;
import hfutinvincible.Tuple2;
import hfutinvincible.anno.AgentType;
import hfutinvincible.anno.Autowired;
import hfutinvincible.anno.HfutTool;
import hfutinvincible.tool.*;
import hfutinvincible.tool.edge.AreaPassableTool;
import hfutinvincible.tool.edge.HFUTEdge;
import hfutinvincible.tool.human.BuriednessTool;
import hfutinvincible.tool.human.HumanPositionTool;
import hfutinvincible.tool.misc.OptionsTool;
import hfutinvincible.tool.partition.Partition;
import hfutinvincible.tool.partition.StaticPartitionTool;
import hfutinvincible.tool.path.InPartitionSearchPathTool;
import org.apache.log4j.Logger;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@HfutTool
@AgentType({FireBrigade.class})
public class FbTargetProvider implements LifeCycle,TargetProvider<Area>{

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

    //Params
    private Params params = new Params(getClass().getSimpleName());


    @Override
    public List<Tuple2<Area, Double>> getSortedCandidatesAndScores() {
        Map<Area, Integer> TargetWithItsPriority = new HashMap<>();
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

        //Todo 需要更改策略的优先级
        /** 将候选区域中的所有目标的数值设置为2也就是第三个层级 目前策略随意假设的*/
        candidates.stream()
                .map(buildings -> (Area) buildings)
                .forEach(areas -> TargetWithItsPriority.put(areas, 2));
        /** 将Refuge的类型设置为最高层*/
        candidates.stream()
                .filter(buildings -> buildings instanceof Refuge)
                .map(refuges -> (Area) refuges)
                .forEach(building -> TargetWithItsPriority.put(building, 0));

        /** 如果在candidates中找到其中存在有受伤的人的building将其优先级设为第二层*/
//        candidates.stream()
//                .filter(builds -> builds instanceof Building)
//                .map(Building.class::cast)
//                .filter(b -> !(b instanceof Refuge))
//                .filter(building -> {
//                    List<HFUTEdge> edges = areaPassableTool.getHfutEdgesOf(building.getID());
//                    if (edges == null || edges.isEmpty())
//                        return false;
//                    double minPassCost = edges.stream()
//                            .mapToDouble(inPartitionSearchPathTool::getCostOrDefault)
//                            .min()
//                            .orElse(Double.MAX_VALUE);
//                    int minPassSteps = (int) (minPassCost / OptionsTool.MOVING_DISTANCE);
//                    return minPassSteps < params.getInt("hurtHumanConsiderRange");
//                })
//                .forEach(building -> TargetWithItsPriority.put(building, 1));
        return TargetWithItsPriority.entrySet().stream()
                .sorted(Map.Entry.<Area,Integer>comparingByValue())
                .map(res -> Tuple2.of(res.getKey(),(double)res.getValue()))
                .collect(Collectors.toList());
    }

    @Override
    public void afterCalc(CalcContext context) {
        if(context.getTarget()!=null){
            prevTarget =context.getTarget();
        }
    }
}

