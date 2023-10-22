//package hfutinvincible.tool.targetprovider;
//
//import adf.core.agent.develop.DevelopData;
//import adf.core.agent.info.AgentInfo;
//import adf.core.agent.info.ScenarioInfo;
//import adf.core.agent.info.WorldInfo;
//import adf.core.agent.module.ModuleManager;
//import hfutinvincible.Tuple3;
//import hfutinvincible.debug.HFUTDebugger;
//import hfutinvincible.debug.MiscLayer;
//import hfutinvincible.debug.PfActionLayer;
//import hfutinvincible.tool.HFUTTool;
//import hfutinvincible.tool.UnavailableTargetTool;
//import hfutinvincible.tool.UnavailableType;
//import hfutinvincible.tool.building.BuildingEntranceTool;
//import hfutinvincible.tool.edge.AreaPassableTool;
//import hfutinvincible.tool.edge.HFUTEdge;
//import hfutinvincible.tool.human.HumanPositionTool;
//import hfutinvincible.tool.human.StuckInfoTool;
//import hfutinvincible.tool.misc.OptionsTool;
//import hfutinvincible.tool.partition.Partition;
//import hfutinvincible.tool.partition.StaticPartitionTool;
//import hfutinvincible.tool.path.InPartitionSearchPathTool;
//import rescuecore2.standard.entities.Area;
//import rescuecore2.standard.entities.Building;
//import rescuecore2.standard.entities.Road;
//import rescuecore2.worldmodel.EntityID;
//
//import javax.annotation.Nonnull;
//import java.awt.*;
//import java.util.*;
//import java.util.List;
//import java.util.function.Function;
//
//public class PfTargetProvider2 extends HFUTTool {
//
//    // Tools
//    private StaticPartitionTool staticPartitionTool;
//    private HumanPositionTool humanPositionTool;
//    private StuckInfoTool stuckInfoTool;
//    private UnavailableTargetTool unavailableTargetTool;
//    private BuildingEntranceTool buildingEntranceTool;
//    private AreaPassableTool areaPassableTool;
//    private InPartitionSearchPathTool inPartitionSearchPathTool;
//
//
//    /** 上一个周期选择的目标 */
//    private Area prevTarget;
//
//    public PfTargetProvider2(AgentInfo ai, WorldInfo wi, ScenarioInfo si, ModuleManager moduleManager, DevelopData developData) {
//        super(ai, wi, si, moduleManager, developData);
//
//        onInitialize(toolProvider -> {
//            this.staticPartitionTool = toolProvider.get("StaticPartitionTool");
//            this.humanPositionTool = toolProvider.get("HumanPositionTool");
//            this.stuckInfoTool = toolProvider.get("StuckInfoTool");
//            this.staticPartitionTool = toolProvider.get("StaticPartitionTool");
//            this.unavailableTargetTool = toolProvider.get("UnavailableTargetTool");
//            this.buildingEntranceTool = toolProvider.get("BuildingEntranceTool");
//            this.areaPassableTool = toolProvider.get("AreaPassableTool");
//            this.inPartitionSearchPathTool = toolProvider.get("InPartitionSearchPathTool");
//        });
//
//        onAfterCalc(resultCollector -> {
//            Area target = resultCollector.getTarget();
//            if (target != null)
//                prevTarget = target;
//        });
//    }
//
//    @Nonnull
//    public List<Area> getSortedCandidates() {
//
//        List<Area> result = new ArrayList<>();
//        MiscLayer pfActionLayer = HFUTDebugger.getMiscLayerOf(agentInfo, PfActionLayer.class);
//        Partition<EntityID> myAssignedPartition = staticPartitionTool.getAssignedPartition();
//
//        Function<Area, Tuple3<Area, Integer, Integer>> func = area -> {
//            List<HFUTEdge> edges = areaPassableTool.getHfutEdgesOf(area.getID());
//            if (edges == null)
//                return null;
//            LinkedList<HFUTEdge> bestPath = null;
//            double minPassCost = Double.MAX_VALUE;
//            for (HFUTEdge edge : edges) {
//                var x = inPartitionSearchPathTool.getEdgePathAndCost(edge);
//                if (x == null)
//                    continue;
//                if (x.e1 < minPassCost) {
//                    minPassCost = x.e1;
//                    bestPath = x.e0;
//                }
//            }
//            if (bestPath == null)
//                return null;
//            int passSteps = (int) (minPassCost / OptionsTool.MOVING_DISTANCE);
//            int totPassability = 0; // 越大越难通行
//            Iterator<HFUTEdge> iter1 = bestPath.listIterator(0);
//            Iterator<HFUTEdge> iter2 = bestPath.listIterator(1);
//            while (iter2.hasNext()) {
//                HFUTEdge e1 = iter1.next();
//                HFUTEdge e2 = iter2.next();
//                int passability = areaPassableTool.getPassability(e1, e2);
//                if (passability == -1)
//                    totPassability += 1;
//                else if (passability == 2)
//                    totPassability += 2;
//                else if (passability == 0)
//                    totPassability += 4;
//            }
//            return Tuple3.of(area, passSteps, totPassability / bestPath.size());
//        };
//
//        // 所有 refuge 的入口
//        buildingEntranceTool.getRefugeEntrances()
//                .stream()
//                .filter(id -> !unavailableTargetTool.isUnavailable(
//                        id, UnavailableType.pfNoAction))
//                .map(worldInfo::getEntity)
//                .filter(Area.class::isInstance)
//                .map(Area.class::cast)
//                .map(func)
//                .filter(Objects::nonNull)
//                .sorted((t1, t2) -> {
//                    if (t1.e0.equals(prevTarget))
//                        return -1;
//                    if (t2.e0.equals(prevTarget))
//                        return 1;
//                    if (!Objects.equals(t1.e1, t2.e1))
//                        return t1.e1 - t2.e1;
//                    return t1.e2 - t2.e2;
//                })
//                .map(t -> t.e0)
//                .forEach(a -> {
//                    if (pfActionLayer != null)
//                        pfActionLayer.addText(a.getX(), a.getY() - 200, "refuge entrance", Color.RED);
//                    result.add(a);
//                });
//
//        // 距离我的距离小于给定值的，所有标记为 block 的 building 的入口
//        unavailableTargetTool.getAllUnavailableTargets(UnavailableType.block)
//                .filter(id -> !unavailableTargetTool.isUnavailable(
//                        id, UnavailableType.pfNoAction))
//                .map(worldInfo::getEntity)
//                .filter(Building.class::isInstance)
//                .map(Area.class::cast)
//                .flatMap(blockBuilding -> blockBuilding.getNeighbours()
//                        .stream()
//                        .map(worldInfo::getEntity)
//                        .filter(Road.class::isInstance)
//                        .map(Area.class::cast))
//                .map(func)
//                .filter(Objects::nonNull)
//                .filter(t -> t.e1 < 10)
//                .sorted((t1, t2) -> {
//                    if (t1.e0.equals(prevTarget))
//                        return -1;
//                    if (t2.e0.equals(prevTarget))
//                        return 1;
//                    if (!Objects.equals(t1.e1, t2.e1))
//                        return t1.e1 - t2.e1;
//                    return t1.e2 - t2.e2;
//                })
//                .map(t -> t.e0)
//                .forEach(a -> {
//                    if (pfActionLayer != null)
//                        pfActionLayer.addText(a.getX(), a.getY() - 200, "block building entrance", Color.RED);
//                    result.add(a);
//                });
//
//
//        // 距离我的距离小于给定值的，卡住人的路
//        stuckInfoTool.getStuckHumanIds(20)
//                .map(humanPositionTool::getPositionAreaId)
//                .filter(Objects::nonNull)
//                .filter(id -> !unavailableTargetTool.isUnavailable(
//                        id, UnavailableType.pfNoAction))
//                .map(worldInfo::getEntity)
//                .filter(Area.class::isInstance)
//                .map(Area.class::cast)
//                .map(func)
//                .filter(Objects::nonNull)
//                .filter(t -> t.e1 < 10)
//                .sorted((t1, t2) -> {
//                    if (t1.e0.equals(prevTarget))
//                        return -1;
//                    if (t2.e0.equals(prevTarget))
//                        return 1;
//                    if (!Objects.equals(t1.e1, t2.e1))
//                        return t1.e1 - t2.e1;
//                    return t1.e2 - t2.e2;
//                })
//                .map(t -> t.e0)
//                .forEach(a -> {
//                    if (pfActionLayer != null)
//                        pfActionLayer.addText(a.getX(), a.getY() - 200, "road stuck human", Color.RED);
//                    result.add(a);
//                });
//
//        // 分配到的分区里的 building 入口
//        myAssignedPartition.getElements()
//                .filter(id -> !unavailableTargetTool.isUnavailable(
//                        id, UnavailableType.pfNoAction))
//                .filter(buildingEntranceTool::isBuildingEntrance)
//                .map(worldInfo::getEntity)
//                .filter(Area.class::isInstance)
//                .map(Area.class::cast)
//                .map(func)
//                .filter(Objects::nonNull)
//                .sorted((t1, t2) -> {
//                    if (t1.e0.equals(prevTarget))
//                        return -1;
//                    if (t2.e0.equals(prevTarget))
//                        return 1;
//                    if (!Objects.equals(t1.e1, t2.e1))
//                        return t1.e1 - t2.e1;
//                    return t1.e2 - t2.e2;
//                })
//                .map(t -> t.e0)
//                .forEach(a -> {
//                    if (pfActionLayer != null)
//                        pfActionLayer.addText(a.getX(), a.getY() - 200, "assigned building entrances", Color.RED);
//                    result.add(a);
//                });
//
//        return result;
//    }
//}