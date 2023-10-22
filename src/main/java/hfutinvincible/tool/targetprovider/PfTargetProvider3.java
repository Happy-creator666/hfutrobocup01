package hfutinvincible.tool.targetprovider;

import adf.core.agent.develop.DevelopData;
import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.ScenarioInfo;
import adf.core.agent.info.WorldInfo;
import adf.core.agent.module.ModuleManager;
import hfutinvincible.Tuple2;
import hfutinvincible.anno.AgentType;
import hfutinvincible.anno.Autowired;
import hfutinvincible.anno.HfutTool;
import hfutinvincible.tool.CalcContext;
import hfutinvincible.tool.LifeCycle;
import hfutinvincible.tool.UnavailableTargetTool;
import hfutinvincible.tool.UnavailableType;
import hfutinvincible.tool.building.BuildingEntranceTool;
import hfutinvincible.tool.edge.AreaPassableTool;
import hfutinvincible.tool.human.HumanPositionTool;
import hfutinvincible.tool.human.StuckInfoTool;
import hfutinvincible.tool.partition.Partition;
import hfutinvincible.tool.partition.StaticPartitionTool;
import hfutinvincible.tool.path.InPartitionSearchPathTool;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;

import java.util.*;

@HfutTool
@AgentType({AmbulanceTeam.class, FireBrigade.class, PoliceForce.class})
public class PfTargetProvider3 implements LifeCycle, TargetProvider<Area> {
    @Autowired
    private WorldInfo worldInfo;

    @Autowired
    private StaticPartitionTool staticPartitionTool;

    @Autowired
    private BuildingEntranceTool buildingEntranceTool;

    @Autowired
    private HumanPositionTool humanPositionTool;

    @Autowired
    private AreaPassableTool areaPassableTool;


    @Autowired
    private InPartitionSearchPathTool inPartitionSearchPathTool;

    @Autowired
    private UnavailableTargetTool unavailableTargetTool;

    @Autowired
    private StuckInfoTool stuckInfoTool;


    private Area prevTarget;

    public PfTargetProvider3() {

    }

    @Override
    public void afterCalc(CalcContext context) {
        Area target = context.getTarget();

        if (target != null)
            prevTarget = target;
    }

    @Override
    public List<Tuple2<Area, Double>> getSortedCandidatesAndScores() {

        Set<Integer> partitionSet = new HashSet<>();
        int currPartitionId = staticPartitionTool.getAssignedPartitionId();
        partitionSet.add(currPartitionId);
        Map<Area, Integer> taskMap = new HashMap<>();
        for (;;) {
            partitionSet.stream()
                    .map(staticPartitionTool::getPartition)
                    .flatMap(Partition::getElements)
                    .filter(id -> !unavailableTargetTool.isUnavailable(id, UnavailableType.pfNoAction))
                    .map(worldInfo::getEntity)
                    .filter(Area.class::isInstance)
                    .map(Area.class::cast)
                    .forEach(area -> taskMap.put(area, 5));
            if (!taskMap.isEmpty())
                break;
            // try expand
            int oldSize = partitionSet.size();
            partitionSet.stream()
                    .flatMap(id -> staticPartitionTool.getNeighborPartition(id).stream())
                    .forEach(partitionSet::add);
            if (oldSize == partitionSet.size())
                break;
        }
        for (Map.Entry<Area, Integer> task : taskMap.entrySet()) {
            Area area = task.getKey();
            // level4 分区内所有 available refuge
            // 应该是refuge或building的入口
            if (area instanceof Refuge)
                task.setValue(0);
                // 是建筑
            else if (area instanceof Building) {
                List<EntityID> neighboursAndMe = area.getNeighbours();
                neighboursAndMe.add(area.getID());
                long humanCountAtNeighboursAndMe = neighboursAndMe.stream()
                        .flatMap(id -> humanPositionTool.getHumanIdsOnArea(id).stream())
                        .count();
                if (humanCountAtNeighboursAndMe > 0)
                    task.setValue(1);
                else
                    task.setValue(2);
            }else if (area instanceof Road){
                Set<EntityID> agentIdsAtArea = humanPositionTool.getHumanIdsOnArea(area.getID());
                for(EntityID id : agentIdsAtArea){
                    StandardEntity entity = worldInfo.getEntity(id);
                    if(entity instanceof FireBrigade){
                        task.setValue(2);
                    }
                    if(entity instanceof AmbulanceTeam )
                    {
                        task.setValue(3);
                    }
                    if(entity instanceof Civilian){
                        task.setValue(4);
                    }
                }
            }
        }

        return taskMap.entrySet().stream()
                .map(entry -> Tuple2.of(entry.getKey(), (double) entry.getValue()))
                .sorted((o1, o2) -> {
                    if (!Objects.equals(o1.e1, o2.e1))
                        return Double.compare(o1.e1, o2.e1);
                    else {
                        double cost1 = inPartitionSearchPathTool.getCostOrDefault(o1.e0.getID());
                        double cost2 = inPartitionSearchPathTool.getCostOrDefault(o2.e0.getID());
                        return Double.compare(cost1, cost2);
                    }
                })
                .toList();
    }

//    @Override
//    public List<Tuple2<Area, Double>> getSortedCandidatesAndScores() {
//
//        List<Tuple2<Area,Double>> result = new ArrayList<>();
//        List<Area>[] levels = new List[5];
//
//        Set<EntityID> areaIdsInPartition = staticPartitionTool.getWorkingPartition()
//                .getElements()
//                .filter(id -> !unavailableTargetTool.isUnavailable(id, UnavailableType.pfNoAction))
//                .filter(id -> worldInfo.getEntity(id) instanceof Area)
//                .collect(Collectors.toSet());
//
//        // level1 分区内所有refuge available的入口
//        Set<EntityID> refugeEntrancesSet = buildingEntranceTool.getRefugeEntrances();
//        Set<EntityID> level1 = Sets.intersection(refugeEntrancesSet, areaIdsInPartition);
//        levels[0] = level1.stream().sorted((o1,o2) -> {
//               Double d1 = this.inPartitionSearchPathTool.getCostOrDefault(o1);
//               Double d2 = this.inPartitionSearchPathTool.getCostOrDefault(o2);
//               return d1.compareTo(d2);
//            })
//            .map(worldInfo::getEntity)
//            .map(Area.class::cast)
//            .toList();
//
//        // level2 分区内所有被困的human所处的建筑 available的入口
//        Set<EntityID> level2 = areaIdsInPartition.stream()
//                .map(id -> worldInfo.getEntity(id))
//                .map(area -> area instanceof Building)
//                .map(Building.class::cast)
//                .filter(building -> {
//                    Set<EntityID> ids = humanPositionTool.getHumanIdsOnArea(building.getID());
//                    return ids.stream().anyMatch(stuckInfoTool::isStuck);
//                })
//                .flatMap(building -> buildingEntranceTool.getEntrancesOf(building.getID()).stream())
//                .map(Road::getID)
//                .collect(Collectors.toSet());
//
//
//        levels[1] = level2.stream().sorted((o1,o2) -> {
//                Double d1 = this.inPartitionSearchPathTool.getCostOrDefault(o1);
//                Double d2 = this.inPartitionSearchPathTool.getCostOrDefault(o2);
//                return d1.compareTo(d2);
//                })
//                .map(worldInfo::getEntity)
//                .map(Area.class::cast)
//                .toList();
//
//        // level3 分区内所有被卡住的人的路
//        Set<EntityID> level3 = new HashSet<>();
//        stuckInfoTool.getStuckHumanIds(OptionsTool.IGNORE_STUCK_HUMAN_AFTER)
//                .map(stuckHumanId -> humanPositionTool.getPositionAreaId(stuckHumanId))
//                .filter(Objects::nonNull)
//                .filter(id -> !unavailableTargetTool.isUnavailable(
//                        id, UnavailableType.pfNoAction))
//                .flatMap(stuckHumanAreaId -> { // 将卡住的人所在的 area 和其周围 area 加入
//                    Area stuckHumanArea = (Area) worldInfo.getEntity(stuckHumanAreaId);
//                    if (stuckHumanArea == null)
//                        return Stream.of();
//                    Stream<Area> neighbors = stuckHumanArea.getNeighbours()
//                            .stream()
//                            .map(worldInfo::getEntity)
//                            .filter(Area.class::isInstance)
//                            .map(Area.class::cast);
//                    return Stream.concat(Stream.of(stuckHumanArea), neighbors);
//                })
//                .filter(area -> {
//                    List<HFUTEdge> edges = areaPassableTool.getHfutEdgesOf(area.getID());
//                    if (edges == null || edges.isEmpty())
//                        return false;
//                    double minPassCost = edges.stream()
//                            .mapToDouble(inPartitionSearchPathTool::getCostOrDefault)
//                            .min()
//                            .orElse(Double.MAX_VALUE);
//                    int minPassSteps = (int) (minPassCost / OptionsTool.MOVING_DISTANCE);
//                    return minPassSteps < 5;
//                })
//                .map(Area::getID)
//                .forEach(level3::add);
//        levels[2] = level3.stream().sorted((o1,o2) -> {
//                Double d1 = this.inPartitionSearchPathTool.getCostOrDefault(o1);
//                Double d2 = this.inPartitionSearchPathTool.getCostOrDefault(o2);
//                return d1.compareTo(d2);
//                })
//                .map(worldInfo::getEntity)
//                .map(Area.class::cast)
//                .toList();
//        /////////////////////////////////////
//        double from = 1000.;
//        for (List<Area> levelI : levels) {
//            for (Area area : levelI) {
//                from -= 1.;
//                result.add(Tuple2.of(area, from));
//            }
//        }
//        return result;
//    }
}