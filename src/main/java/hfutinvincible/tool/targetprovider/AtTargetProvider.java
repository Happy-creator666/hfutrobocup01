package hfutinvincible.tool.targetprovider;
import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.WorldInfo;
import hfutinvincible.Tuple2;
import hfutinvincible.anno.AgentType;
import hfutinvincible.anno.Autowired;
import hfutinvincible.anno.HfutTool;
import hfutinvincible.tool.LifeCycle;
import hfutinvincible.tool.UnavailableTargetTool;
import hfutinvincible.tool.UnavailableType;
import hfutinvincible.tool.human.BuriednessTool;
import hfutinvincible.tool.human.HumanPositionTool;
import hfutinvincible.tool.human.TimeToDeadTool;
import hfutinvincible.tool.misc.HFUTGeometryTool;
import hfutinvincible.tool.misc.MeasureLengthTool;
import hfutinvincible.tool.partition.Partition;
import hfutinvincible.tool.partition.StaticPartitionTool;
import org.apache.log4j.Logger;
import rescuecore2.misc.geometry.Point2D;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;

import java.util.*;

@HfutTool
@AgentType({AmbulanceTeam.class})
public class AtTargetProvider implements LifeCycle, TargetProvider<Area> {

    @Autowired
    StaticPartitionTool staticPartitionTool;

    @Autowired
    WorldInfo worldInfo;

    @Autowired
    TimeToDeadTool timeToDeadTool;

    @Autowired
    BuriednessTool buriednessTool;

    @Autowired
    HumanPositionTool humanPositionTool;

    @Autowired
    UnavailableTargetTool unavailableTargetTool;

    @Autowired
    MeasureLengthTool measureLengthTool;

    @Autowired
    AgentInfo agentInfo;
    @Autowired
    Logger logger;

    @Override
    public List<Tuple2<Area, Double>> getSortedCandidatesAndScores() {/* 注意：candidates在进行排序时分数越小越优先*/
        List<Tuple2<Area, Double>> candidatesAndScores = new ArrayList<>();
        boolean withHumanLoaded = agentInfo.someoneOnBoard() != null;
        if (withHumanLoaded) {
            // 如果装了人，只将所有 refuges 作为目标 并根据通行距离以及Refuge的容量进行排序
            for (StandardEntity e : worldInfo.getEntitiesOfType(StandardEntityURN.REFUGE)){
                if (e instanceof Refuge refuge){
                    int capacity = refuge.getBedCapacity();
                    // 设置Rufuge中床位的容量的权值为0.3解决两个Refuge之间容量差距过大问题
                    Double score = 1/(capacity * 0.25);  //TODO 需要根据实际情况进行更改
                    Double dist = measureLengthTool.getManhattanLength(humanPositionTool.getMyPositionArea(),refuge);
                    candidatesAndScores.add(Tuple2.of(refuge, score * dist));
                }
            }
            candidatesAndScores.sort(Comparator.comparingDouble(t -> t.e1));
//            // 按照自己与目标区域的距离进行排序
//            Area myPosArea = humanPositionTool.getMyPositionArea();
//            candidatesAndScores.sort((t1, t2) -> {
//                if (Objects.equals(t1.e1, t2.e1)) {
//                    double dist1 = measureLengthTool.getManhattanLength(myPosArea, t1.e0) * 0.7 ;
//                    double dist2 = measureLengthTool.getManhattanLength(myPosArea, t2.e0) * 0.7 ;
//                    return Double.compare(dist1, dist2);
//                }  else {
//                    return Double.compare(t1.e1, t2.e1);
//                }
//            });
        } else {
            Partition<EntityID> currentPartition = staticPartitionTool.getCurrentPartition();
            currentPartition.getElements().forEach(id -> {
                if (unavailableTargetTool.isUnavailable(id,
                        UnavailableType.noHurtNotSure,
                        UnavailableType.noHurtDefinitely,
                        UnavailableType.block,
                        UnavailableType.buried,
                        UnavailableType.competeFail
                )) return;
                StandardEntity entity = worldInfo.getEntity(id);
                if (entity instanceof Building building && !(entity instanceof Refuge refuge) ) {
                    Set<EntityID> humansOnBuilding = humanPositionTool.getHumanIdsOnArea(building.getID());
                    boolean hasHurtHuman = false;
                    for (EntityID humanId : humansOnBuilding) {
                        if (timeToDeadTool.isHurt(humanId) && !buriednessTool.isBuried(humanId)) {
                            hasHurtHuman = true;
                            break;
                        }
                    }
                    if (hasHurtHuman) logger.info(entity.getID() + " has hurt human in currentPartition, level 1");
                    else logger.info(entity.getID() + " no hurt human in currentPartition, level 3");
                    candidatesAndScores.add(Tuple2.of(building, hasHurtHuman ? 1. /* level 1*/ : 3. /* level 3*/));
                }
            });

            Partition<EntityID> assignedPartition = staticPartitionTool.getAssignedPartition();
            if (assignedPartition != currentPartition) {
                assignedPartition.getElements().forEach(id -> {
                    if (unavailableTargetTool.isUnavailable(id,
                            UnavailableType.noHurtNotSure,
                            UnavailableType.noHurtDefinitely,
                            UnavailableType.block,
                            UnavailableType.buried,
                            UnavailableType.competeFail
                    )) return;
                    StandardEntity entity = worldInfo.getEntity(id);
                    if (entity instanceof Building building && !(entity instanceof Refuge refuge)) {
                        logger.info(entity.getID() + " other building in assignedPartition, level 2");
                        candidatesAndScores.add(Tuple2.of(building, 2.));
                    }
                });
            }

            int currentPartitionId = staticPartitionTool.getCurrentPartitionId();
            Set<Integer> neighborPartitionIds = staticPartitionTool.getNeighborPartition(currentPartitionId);
            for (int neighborPartitionId : neighborPartitionIds) {
                Partition<EntityID> neighborPartition = staticPartitionTool.getPartition(neighborPartitionId);
                neighborPartition.getElements().forEach(id -> {
                    if (unavailableTargetTool.isUnavailable(id,
                            UnavailableType.noHurtNotSure,
                            UnavailableType.noHurtDefinitely,
                            UnavailableType.block,
                            UnavailableType.buried,
                            UnavailableType.competeFail
                    )) return;
                    StandardEntity entity = worldInfo.getEntity(id);
                    if (entity instanceof Building building && !(entity instanceof Refuge refuge)) {
                        logger.info(entity.getID() + " building in neighborPartition, level 4");
                        candidatesAndScores.add(Tuple2.of(building, 4.));
                    }
                });
            }
            // 按照自己与目标区域的距离进行排序
            Area myPosArea = humanPositionTool.getMyPositionArea();
            candidatesAndScores.sort((t1, t2) -> {
                if (Objects.equals(t1.e1, t2.e1)) {
                    double dist1 = measureLengthTool.getManhattanLength(myPosArea, t1.e0);
                    double dist2 = measureLengthTool.getManhattanLength(myPosArea, t2.e0);
                    return Double.compare(dist1, dist2);
                }  else {
                    return Double.compare(t1.e1, t2.e1);
                }
            });
        }
        return candidatesAndScores;
    }
}
