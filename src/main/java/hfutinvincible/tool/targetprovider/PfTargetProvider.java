package hfutinvincible.tool.targetprovider;

import adf.core.agent.Agent;
import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.WorldInfo;
import hfutinvincible.Tuple2;
import hfutinvincible.anno.AgentType;
import hfutinvincible.anno.Autowired;
import hfutinvincible.anno.HfutTool;
import hfutinvincible.tool.CalcContext;
import hfutinvincible.tool.LifeCycle;
import hfutinvincible.tool.UnavailableTargetTool;
import hfutinvincible.tool.UnavailableType;
import hfutinvincible.tool.building.BuildingEntranceTool;
import hfutinvincible.tool.human.HumanPositionTool;
import hfutinvincible.tool.human.StuckInfoTool;
import hfutinvincible.tool.human.StuckType;
import hfutinvincible.tool.misc.OptionsTool;
import hfutinvincible.tool.partition.Partition;
import hfutinvincible.tool.partition.StaticPartitionTool;
import org.apache.log4j.Logger;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;

import java.util.*;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;
@HfutTool
@AgentType({PoliceForce.class})
public class PfTargetProvider implements LifeCycle,TargetProvider<Area> {
    @Autowired
    AgentInfo agentInfo;
    @Autowired
    WorldInfo worldInfo;
    @Autowired
    Logger logger;
    /**上一个周期选择的目标*/
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



    @Override
    public void afterCalc(CalcContext context) {
        Area target = context.getTarget();

        if(target!= null){
            prevTarget=target;
        }
    }

    @Override
    public List<Tuple2<Area, Double>> getSortedCandidatesAndScores() {
        Set<EntityID>agentsPartitionId = new HashSet<>();
        Set <Integer>partitionIdset = new HashSet<>();
        Map<Area,Integer>TargetWithItsPriority=new HashMap<>();
        int assignPartitionId = staticPartitionTool.getAssignedPartitionId();
        partitionIdset.add(assignPartitionId);

        for (;;) {
            /** 首先将所有可能的区域的优先级都设置为3*/
            partitionIdset.stream()
                    .map(staticPartitionTool::getPartition)
                    .flatMap(Partition::getElements)
                    .filter(id -> !unavailableTargetTool.isUnavailable(id, UnavailableType.pfNoAction))
                    .map(worldInfo::getEntity)
                    .filter(Area.class::isInstance)
                    .map(Area.class::cast)
                    .forEach(area -> TargetWithItsPriority.put(area,3));
            if (!TargetWithItsPriority.isEmpty())
                break;
            // try expand
            int oldSize = partitionIdset.size();
            partitionIdset.stream()
                    .flatMap(id -> staticPartitionTool.getNeighborPartition(id).stream())
                    .forEach(partitionIdset::add);
            if (oldSize == partitionIdset.size())
                break;
        }
        /** 在我当前的分区内寻找被卡住的人
         * 若没有在我这个分区内找到则继续搜索直到找到一个被卡住的智能体优先级设置为最高*/
        for(;;){
            Partition<EntityID> myCurrentPartition = this.staticPartitionTool.getCurrentPartition();
            stuckInfoTool.getStuckHumanIds(OptionsTool.IGNORE_STUCK_HUMAN_AFTER)
//                    .filter(agentId ->{
//                        Class<?>stuckType=
//                        return PoliceForce.class.equals(stuckType) || FireBrigade.class.equals(stuckType) || AmbulanceTeam.class.equals(stuckType);
//                    })
                    .map(id -> humanPositionTool.getPositionArea(id))
                    .filter(Objects::nonNull)
                    .map(Area.class::cast)
                    .filter(area -> myCurrentPartition.contains(area.getID()))
                    .forEach(stuckArea -> TargetWithItsPriority.put(stuckArea, 0));
            if (!TargetWithItsPriority.isEmpty())
                break;
            // try expand
            int oldSize = partitionIdset.size();
            partitionIdset.stream()
                    .flatMap(id -> staticPartitionTool.getNeighborPartition(id).stream())
                    .forEach(partitionIdset::add);
            if (oldSize == partitionIdset.size())
                break;
        }
        /**判断是否为building或者rufuge的入口进一步更新优先级*/
        for(Map.Entry<Area,Integer> TargetPriority:TargetWithItsPriority.entrySet()){
            if(buildingEntranceTool.isRefugeEntrance(TargetPriority.getKey().getID()))
            {
                TargetPriority.setValue(1);
            } else if (buildingEntranceTool.isBuildingEntrance(TargetPriority.getKey().getID())) {
                TargetPriority.setValue(2);
            }
        }
        return TargetWithItsPriority.entrySet().stream()
                .sorted(Map.Entry.<Area,Integer>comparingByValue())
                .map(res -> Tuple2.of(res.getKey(),(double)res.getValue()))
                .collect(Collectors.toList());
    }
}
