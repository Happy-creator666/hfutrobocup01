package hfutinvincible.tool.criterion;

import adf.core.agent.develop.DevelopData;
import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.ScenarioInfo;
import adf.core.agent.info.WorldInfo;
import adf.core.agent.module.ModuleManager;
import hfutinvincible.anno.AgentType;
import hfutinvincible.anno.Autowired;
import hfutinvincible.anno.HfutTool;
import hfutinvincible.tool.LifeCycle;
import hfutinvincible.tool.human.BuriednessTool;
import hfutinvincible.tool.human.HumanPositionTool;
import hfutinvincible.tool.human.StuckInfoTool;
import hfutinvincible.tool.human.TimeToDeadTool;
import hfutinvincible.tool.misc.OptionsTool;
import org.jblas.DoubleMatrix;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateXY;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@HfutTool
@AgentType({AmbulanceTeam.class, FireBrigade.class, PoliceForce.class})
public class StuckHumanConditionCriterion implements LifeCycle, ICriterion<Area> {
    @Autowired
    private WorldInfo worldInfo;

    // Tools
    @Autowired
    private HumanPositionTool humanPositionTool;
    @Autowired
    private StuckInfoTool stuckInfoTool;
    @Autowired
    private TimeToDeadTool timeToDeadTool;
    @Autowired
    private BuriednessTool buriednessTool;

    public StuckHumanConditionCriterion() {

    }

    @Nonnull
    @Override
    public Map<CriterionEnum, DoubleMatrix> evaluate(@Nonnull List<Area> candidates) {

        // 每个区域 priority 初始值为 0
        int candidateNum = candidates.size();
        final DoubleMatrix priorityOfStuckHuman = DoubleMatrix.zeros(candidateNum);

        Map<EntityID, Integer> candidateId2index = IntStream.range(0, candidateNum)
                .boxed()
                .collect(Collectors.toMap(i -> candidates.get(i).getID(), i -> i));

        Set<EntityID> changedEntities = worldInfo.getChanged().getChangedEntities();

        stuckInfoTool.getStuckHumanIds(OptionsTool.IGNORE_STUCK_HUMAN_AFTER)
                .forEach(stuckHumanId -> {
                    // 被卡的是警察，不用管
                    Human human = (Human) worldInfo.getEntity(stuckHumanId);
                    if (human == null || human instanceof PoliceForce)
                        return;
                    EntityID posAreaId = humanPositionTool.getPositionAreaId(stuckHumanId);
                    if (posAreaId == null) return;
                    Integer timeToDead = timeToDeadTool.getTimeToDeadNowEstimate(stuckHumanId);
                    if (timeToDead == null) // 如果不知道 timeToDead
                        timeToDead = OptionsTool.TIME_TO_DEAD_UNKNOWN;
                    Integer buriedness = buriednessTool.getBuriedness(stuckHumanId);
                    if (buriedness == null)
                        buriedness = 0; //  如果不知道 buriedness，默认为 0
                    // 增量
                    // 因为警察清理干净一块区域上的障碍，这个障碍上所有被卡的人都能获救
                    // 因此对于警察而言，一个 area 上人越多越值得去做
                    // 因此每多一个人，delta 至少加一
                    double delta = 1.0 + fun(timeToDead - buriedness);
                    Area posArea = (Area) worldInfo.getEntity(posAreaId);
                    if (posArea instanceof Building) {
                        // TODO 卡在建筑里面，肯定所有出口都出不去，所有出口 priority 都加大
                        for (EntityID neighborId : posArea.getNeighbours()) {
                            Integer index = candidateId2index.get(neighborId);
                            if (index == null) // 不是候选 area
                                continue;
                            StandardEntity neighbor = worldInfo.getEntity(neighborId);
                            if (!(neighbor instanceof Road)) // 出口必须是 road
                                continue;
                            priorityOfStuckHuman.put(index, priorityOfStuckHuman.get(index) + 1.0 + fun(timeToDead - buriedness));
                        }
                    } else if (posArea instanceof Road) {
                        Integer index = candidateId2index.get(posAreaId);
                        if (index == null) // 不是候选 area 上的人
                            return;
                        // 这个 area 在我视野范围内
                        if (changedEntities.contains(posAreaId)) {
//                            // 注意：这个人可能不是被我所在 area 上的障碍卡住，而是被我邻居的某个障碍卡住
//                            // 因此计算我到我和我邻居的上所有障碍的距离，按平方反比规律增加 priority（距离越近，增加的 priority 越多）
//                            Human stuckHuman = (Human) worldInfo.getEntity(stuckHumanId);
//                            if (stuckHuman == null)
//                                return;
//                            Coordinate humanCoord = new CoordinateXY(stuckHuman.getX(), stuckHuman.getY());
//                            // 考虑这个人所在的 area 和其所在 area 的邻居
//                            List<EntityID> areaIds = new ArrayList<>(posArea.getNeighbours());
//                            areaIds.add(posAreaId);
//                            DoubleMatrix vec = DoubleMatrix.zeros(areaIds.size());
//                            for (int i = 0; i < areaIds.size(); i ++) {
//                                EntityID areaId = areaIds.get(i);
//                                if (!candidateId2index.containsKey(areaId)
//                                        || !changedEntities.contains(areaId))
//                                    continue;
//                                Collection<Blockade> blockades = worldInfo.getBlockades(areaId);
//                                if (blockades.isEmpty())
//                                    continue;
//                                double minDist = Double.MAX_VALUE;
//                                for (Blockade blockade : blockades) {
//                                    double dist = stuckInfoTool.distanceToBlockade(humanCoord, blockade);
//                                    if (dist < minDist)
//                                        minDist = dist;
//                                }
//                                // 平方反比衰减
//                                // 防止除 0 错误
//                                vec.put(i, Math.min(1e-12, 1 / (minDist * minDist)));
//                            }
//                            double sum = vec.sum();
//                            for (int i = 0; i < areaIds.size(); i ++) {
//                                Integer j = candidateId2index.get(posAreaId);
//                                if (j == null) // 不是候选 area 上的人
//                                    return;
//                                priorityOfStuckHuman.put(j, priorityOfStuckHuman.get(j) + (vec.get(i) / sum) * delta);
//                            }
                            // 找出被卡住的人距离哪个障碍最近
                            // 这个障碍所在的 area 优先级增加
                            List<EntityID> checkAreaIds = new ArrayList<>(posArea.getNeighbours());
                            checkAreaIds.add(posAreaId);
                            double minDist = Double.MAX_VALUE;
                            EntityID closestAreaId = null;
                            Human stuckHuman = (Human) worldInfo.getEntity(stuckHumanId);
                            if (stuckHuman == null)
                                return;
                            Coordinate humanCoord;
                            if (stuckHuman.isXDefined() && stuckHuman.isYDefined())
                                humanCoord = new CoordinateXY(stuckHuman.getX(), stuckHuman.getY());
                            else humanCoord = new CoordinateXY(posArea.getX(), posArea.getY()); // TODO not sure
                            for (EntityID checkAreaId : checkAreaIds) {
                                if (!candidateId2index.containsKey(checkAreaId))
                                    continue;
                                for (Blockade blockade : worldInfo.getBlockades(checkAreaId)) {
                                    double dist = stuckInfoTool.distanceToBlockade(humanCoord, blockade);
                                    if (dist < minDist) {
                                        minDist = dist;
                                        closestAreaId = checkAreaId;
                                    }
                                }
                            }
                            if (closestAreaId != null) {
                                index = candidateId2index.get(closestAreaId);
                                if (index != null)
                                    priorityOfStuckHuman.put(index, priorityOfStuckHuman.get(index) + delta);
                            }
                        } else { // 这个 area 不在我视野范围内，直接加 delta
                            priorityOfStuckHuman.put(index, priorityOfStuckHuman.get(index) + delta);
                        }
                    }
                });

//        MiscLayer stuckHumanConditionLayer = HFUTDebugger.getMiscLayerOf(agentInfo, StuckHumanConditionLayer.class);
//        if (stuckHumanConditionLayer != null)
//            for (int i = 0; i < candidateNum; i ++) {
//                Area ithCandidate = candidates.get(i);
//                double value = priorityOfStuckHuman.get(i);
//                stuckHumanConditionLayer.addText(ithCandidate.getX() + 100, ithCandidate.getY() + 100,
//                        String.valueOf(value), Color.MAGENTA);
//            }

        return Map.of(CriterionEnum.priorityOfStuckHuman, priorityOfStuckHuman);
    }

    // 一个乱取的评估函数，需要改
    private double fun(int timeToDead) {
        if (timeToDead < OptionsTool.TIME_TO_DEAD_A) // 马上就要死了，没有必要救
            return 0;
        else if (timeToDead < OptionsTool.TIME_TO_DEAD_B) // 上升部分
            return (1.0 / (OptionsTool.TIME_TO_DEAD_B - OptionsTool.TIME_TO_DEAD_A)) * (timeToDead - OptionsTool.TIME_TO_DEAD_A);
        else if (timeToDead < OptionsTool.TIME_TO_DEAD_C) // 下降部分
            return OptionsTool.TIME_TO_DEAD_D + (OptionsTool.TIME_TO_DEAD_C - timeToDead) * (1.0 - OptionsTool.TIME_TO_DEAD_D) / (OptionsTool.TIME_TO_DEAD_C - OptionsTool.TIME_TO_DEAD_B);
        else return OptionsTool.TIME_TO_DEAD_D; // 还能活很久的人所在区域，优先级为一个定值
    }
}