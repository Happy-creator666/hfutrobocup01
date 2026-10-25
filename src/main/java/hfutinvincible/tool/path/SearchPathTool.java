package hfutinvincible.tool.path;

import adf.core.agent.develop.DevelopData;
import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.ScenarioInfo;
import adf.core.agent.info.WorldInfo;
import adf.core.agent.module.ModuleManager;
import hfutinvincible.anno.AgentType;
import hfutinvincible.anno.Autowired;
import hfutinvincible.anno.HfutTool;
import hfutinvincible.debug.Debugger;
import hfutinvincible.debug.Painter;
import hfutinvincible.debug.paintable.Paintable;
import hfutinvincible.tool.LifeCycle;
import hfutinvincible.tool.edge.AreaPassableTool;
import hfutinvincible.tool.edge.HFUTEdge;
import hfutinvincible.tool.human.HumanPositionRecord;
import hfutinvincible.tool.human.HumanPositionTool;
import hfutinvincible.tool.misc.HFUTGeometryTool;
import hfutinvincible.tool.misc.MeasureLengthTool;
import hfutinvincible.tool.misc.OptionsTool;
import org.apache.log4j.Logger;
import org.locationtech.jts.geom.CoordinateXY;
import rescuecore2.misc.geometry.Point2D;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@HfutTool
@AgentType({AmbulanceTeam.class, FireBrigade.class, PoliceForce.class})
public class SearchPathTool implements LifeCycle {
    @Autowired
    Logger logger;

    @Autowired
    AgentInfo agentInfo;
    @Autowired
    WorldInfo worldInfo;
    @Autowired
    ScenarioInfo scenarioInfo;
    @Autowired
    ModuleManager moduleManager;
    @Autowired
    DevelopData developData;
    @Autowired
    AreaPassableTool areaPassableTool;
    @Autowired PathUtilTool pathUtilTool;
    @Autowired
    MeasureLengthTool measureLengthTool;
    @Autowired
    HumanPositionTool humanPositionTool;

    private Set<HFUTEdge> fromEdges;
    private Set<HFUTEdge> toEdges;
    private Area fromArea;
    private Set<Area> toAreas;
    private Function<HFUTEdge, Stream<HFUTEdge>> adjacentEdgeProvider;
    private BiFunction<HFUTEdge, HFUTEdge, Double> oneStepCostEvaluator;
    private BiFunction<HFUTEdge, Point2D, Double> heuristicEvaluator;
    private LinkedList<HFUTEdge> edgePath;
    private Path areaPath;
    private boolean calculated = false;
    private boolean budgetExceeded;

    @Override
    public void preparate() {
        this.areaPassableTool.preparate();
        this.measureLengthTool.preparate();
    }

    private void reset() {
        this.fromEdges = null;
        this.toEdges = null;
        this.fromArea = null;
        this.toAreas = null;
        this.edgePath = null;
        this.areaPath = null;
        this.calculated = false;
        this.budgetExceeded = false;
        // 默认使用可通行且长度大于 EDGE_PASSABLE_MIN_LENGTH 的邻接边作为邻接结点
        this.adjacentEdgeProvider = edge -> this.areaPassableTool.getAdjacentEdges(edge)
                .stream()
                .filter(e -> HFUTGeometryTool.getLength(e.getEdge()) > OptionsTool.EDGE_PASSABLE_MIN_LENGTH);

        // Default movement models have a minimum cost multiplier of 0.8.
        this.heuristicEvaluator = (edge, toPoint) -> {
            Point2D point = HFUTGeometryTool.getMidPoint(edge);
            return 0.8 * Math.hypot(point.getX() - toPoint.getX(), point.getY() - toPoint.getY());
        };

        // 默认根据我的类型得到单步代价估计函数
        this.oneStepCostEvaluator = areaPassableTool.getEdgePairPassCostEvaluatorByType(agentInfo.me().getClass());
    }

    /**
     * 设置如何计算单步移动的通行代价
     */
    public SearchPathTool setOneStepCostEvaluator(BiFunction<HFUTEdge, HFUTEdge, Double> oneStepCostEvaluator) {
        this.oneStepCostEvaluator = oneStepCostEvaluator;
        this.heuristicEvaluator = (edge, point) -> 0.0;
        return this;
    }

    /**
     * 设置如何获得一个 HFUTEdge 的所有邻接边
     */
    public SearchPathTool setAdjacentEdgeProvider(Function<HFUTEdge, Stream<HFUTEdge>> adjacentEdgeProvider) {
        this.adjacentEdgeProvider = adjacentEdgeProvider;
        return this;
    }

    /**
     * 设置如何估计一个 HFUTEdge 到终点的通行代价
     */
    public SearchPathTool setHeuristicEvaluator(BiFunction<HFUTEdge, Point2D, Double> heuristicEvaluator) {
        this.heuristicEvaluator = heuristicEvaluator;
        return this;
    }

    /**
     * 获得邻接边时忽略两边之间所夹 area 为 building 的边（保证最后得到的 areaPath 中不存在建筑）
     */
    public SearchPathTool ignoreBuildings() {
        var oldProvider = this.adjacentEdgeProvider;
        this.adjacentEdgeProvider = fromEdge -> oldProvider.apply(fromEdge)
                .filter(toEdge -> {
                    EntityID areaId = areaPassableTool.getAreaIdBetween(fromEdge, toEdge);
                    if (areaId == null)
                        return false;
                    Area area = (Area) worldInfo.getEntity(areaId);
                    return !(area instanceof Building); // 筛去 buildings
                });
        return this;
    }

    /**
     * 获取邻接边时忽略所有无法通行的边
     */
    public SearchPathTool ignoreImpassableAdjacentEdges() {
        var oldProvider = this.adjacentEdgeProvider;
        this.adjacentEdgeProvider = fromEdge -> oldProvider.apply(fromEdge)
                .filter(toEdge -> {
                    int passability = areaPassableTool.getPassability(fromEdge, toEdge);
                    return passability != 0;
                });
        return this;
    }

    public SearchPathTool ignoreBlockades() {
        this.oneStepCostEvaluator = areaPassableTool.getEdgePairPassDistanceEvaluator();
        return this;
    }

    public SearchPathTool setFromAndTo(@Nonnull HFUTEdge fromEdge, @Nonnull HFUTEdge toEdge) {
        reset();
        this.fromEdges = Set.of(fromEdge).stream()
                .filter(edge -> HFUTGeometryTool.getLength(edge.getEdge()) > OptionsTool.EDGE_PASSABLE_MIN_LENGTH).collect(Collectors.toSet());
        this.toEdges = Set.of(toEdge).stream()
                .filter(edge -> HFUTGeometryTool.getLength(edge.getEdge()) > OptionsTool.EDGE_PASSABLE_MIN_LENGTH).collect(Collectors.toSet());
        return this;
    }

    public SearchPathTool setFromAndTo(@Nonnull HFUTEdge fromEdge, @Nonnull Set<HFUTEdge> toEdges) {
        reset();
        this.fromEdges = Set.of(fromEdge).stream()
                .filter(edge -> HFUTGeometryTool.getLength(edge.getEdge()) > OptionsTool.EDGE_PASSABLE_MIN_LENGTH).collect(Collectors.toSet());
        this.toEdges = new HashSet<>(toEdges).stream()
                .filter(edge -> HFUTGeometryTool.getLength(edge.getEdge()) > OptionsTool.EDGE_PASSABLE_MIN_LENGTH).collect(Collectors.toSet());
        return this;
    }

    public SearchPathTool setFromAndTo(@Nonnull Set<HFUTEdge> fromEdges, @Nonnull HFUTEdge toEdge) {
        reset();
        this.fromEdges = new HashSet<>(fromEdges).stream()
                .filter(edge -> HFUTGeometryTool.getLength(edge.getEdge()) > OptionsTool.EDGE_PASSABLE_MIN_LENGTH).collect(Collectors.toSet());
        this.toEdges = Set.of(toEdge).stream()
                .filter(edge -> HFUTGeometryTool.getLength(edge.getEdge()) > OptionsTool.EDGE_PASSABLE_MIN_LENGTH).collect(Collectors.toSet());
        return this;
    }

    public SearchPathTool setFromAndTo(@Nonnull Set<HFUTEdge> fromEdges, @Nonnull Set<HFUTEdge> toEdges) {
        reset();
        this.fromEdges = new HashSet<>(fromEdges).stream()
                .filter(edge -> HFUTGeometryTool.getLength(edge.getEdge()) > OptionsTool.EDGE_PASSABLE_MIN_LENGTH).collect(Collectors.toSet());
        this.toEdges = new HashSet<>(toEdges).stream()
                .filter(edge -> HFUTGeometryTool.getLength(edge.getEdge()) > OptionsTool.EDGE_PASSABLE_MIN_LENGTH).collect(Collectors.toSet());
        return this;
    }

    public SearchPathTool setFromAndTo(@Nonnull Area fromArea, @Nonnull Area toArea) {
        reset();
        this.fromArea = fromArea;
        this.toAreas = Set.of(toArea);
        if (fromArea.equals(toArea)) {
            logger.info("fromArea and toArea are the same: " + fromArea);
            this.edgePath = new LinkedList<>();
            this.areaPath = new Path(List.of(fromArea));
            this.calculated = true;
            return this;
        }
        Edge e = fromArea.getEdgeTo(toArea.getID());
        if (e != null && e.isPassable()) {
            logger.info("fromArea " + fromArea + " and toArea " + toArea + " are neighbours!");
            this.edgePath = new LinkedList<>(List.of(new HFUTEdge(e)));
            this.areaPath = new Path(List.of(fromArea, toArea));
            this.calculated = true;
            return this;
        }
        // XXX 计算作为起点和终点的两条边
        List<HFUTEdge> edgesOfFromArea = areaPassableTool.getHfutEdgesOf(fromArea.getID());
        if (edgesOfFromArea == null) {
            logger.error("Edges of fromArea " + fromArea + " is not initialized!");
//            logger.debug(this.areaPassableTool.getCountResume());
//            logger.debug(this.areaPassableTool.getHfutEdgeMap());
//            logger.debug(fromArea.getEdges());
//            for (Edge edge : fromArea.getEdges()) {
//                logger.debug(edge + ", " + edge.isPassable());
//            }
            this.edgePath = null;
            this.areaPath = null;
            this.calculated = true;
            return this;
        }
        if (edgesOfFromArea.isEmpty()) {
            logger.error("fromArea has no passable edge.");
            this.edgePath = null;
            this.areaPath = null;
            this.calculated = true;
            return this;
        }
        this.fromEdges = new HashSet<>(edgesOfFromArea).stream()
                .filter(edge -> HFUTGeometryTool.getLength(edge.getEdge()) > OptionsTool.EDGE_PASSABLE_MIN_LENGTH).collect(Collectors.toSet());
        List<HFUTEdge> edgesOfToArea = areaPassableTool.getHfutEdgesOf(toArea.getID());
        if (edgesOfToArea == null) {
            logger.error("Edges of toArea " + toArea + " is not initialized!");
            this.edgePath = null;
            this.areaPath = null;
            this.calculated = true;
            return this;
        }
        if (edgesOfToArea.isEmpty()) {
            logger.error("toArea " + toArea + " has no passable edge.");
            this.edgePath = null;
            this.areaPath = null;
            this.calculated = true;
            return this;
        }
        this.toEdges = new HashSet<>(edgesOfToArea).stream()
                .filter(edge -> HFUTGeometryTool.getLength(edge.getEdge()) > OptionsTool.EDGE_PASSABLE_MIN_LENGTH).collect(Collectors.toSet());
        return this;
    }

    public SearchPathTool setFromAndTo(Area fromArea, List<Area> toAreas) {
        reset();
        this.fromArea = fromArea;
        this.toAreas = new HashSet<>(toAreas);
        if (toAreas.contains(fromArea)) {
            logger.info("fromArea in toAreas.");
            this.edgePath = new LinkedList<>();
            this.areaPath = new Path(List.of(fromArea));
            this.calculated = true;
            return this;
        }
        // XXX 计算作为起点和终点的两条边
        List<HFUTEdge> edgesOfFromArea = areaPassableTool.getHfutEdgesOf(fromArea.getID());
        if (edgesOfFromArea == null) {
            logger.error("Edges of fromArea is not initialized!");
            this.edgePath = null;
            this.areaPath = null;
            this.calculated = true;
            return this;
        }
        if (edgesOfFromArea.isEmpty()) {
            logger.error("fromArea has no passable edge.");
            this.edgePath = null;
            this.areaPath = null;
            this.calculated = true;
            return this;
        }
        this.fromEdges = new HashSet<>(edgesOfFromArea);
        this.toEdges = new HashSet<>();
        for (Area area : toAreas) {
            Edge e = fromArea.getEdgeTo(area.getID());
            // fromArea 和 toAreas 中某个 area 相邻
            if (e != null && e.isPassable()) {
                logger.info("fromArea " + fromArea + " and toArea (in toAreas) " + area + " are neighbors");
                this.edgePath = new LinkedList<>(List.of(new HFUTEdge(e)));
                this.areaPath = new Path(List.of(fromArea, area));
                this.calculated = true;
                return this;
            }
            List<HFUTEdge> passableEdges = areaPassableTool.getHfutEdgesOf(area.getID());
            if (passableEdges == null) {
                logger.error("Edges of toarea " + area + " is not initialized!");
                continue;
            }
            toEdges.addAll(passableEdges);
        }
        if (toEdges.isEmpty()) {
            logger.error("No edges of toAreas " + toAreas);
            this.edgePath = null;
            this.areaPath = null;
            this.calculated = true;
            return this;
        }
        return this;
    }

    public SearchPathTool calc() {
        return calc(Integer.MAX_VALUE, Long.MAX_VALUE);
    }

    public SearchPathTool calc(int maxExpansions, long deadlineNanos) {
        logger.info("SearchPathTool.calc()");
        if (this.calculated)
            return this;
        assert this.fromEdges != null;
        assert this.toEdges != null;
        assert this.oneStepCostEvaluator != null;
        assert this.heuristicEvaluator != null;
        AStarSearch.Result<HFUTEdge> result = AStarSearch.search(
                this.fromEdges,
                this.toEdges,
                this.adjacentEdgeProvider,
                this.oneStepCostEvaluator,
                currEdge -> this.toEdges.stream()
                        .mapToDouble(goal -> this.heuristicEvaluator.apply(currEdge, HFUTGeometryTool.getMidPoint(goal)))
                        .min().orElse(0),
                maxExpansions, deadlineNanos
        );
        this.calculated = true;
        this.budgetExceeded = result.status() == AStarSearch.Status.BUDGET_EXCEEDED;
        this.edgePath = result.status() == AStarSearch.Status.FOUND ? new LinkedList<>(result.path()) : null;
        return this;
    }

    public boolean isBudgetExceeded() { return budgetExceeded; }

    @Nullable
    public LinkedList<HFUTEdge> getEdgePath() {
        assert this.calculated;
        return this.edgePath;
    }

    @Nullable
    public Path getAreaPath() {
        assert this.calculated;
        if (this.areaPath == null && this.edgePath != null) {
            Area toArea = null;
            HFUTEdge lastEdge = edgePath.getLast();
            if (this.toAreas != null) {
                for (Area area : this.toAreas) {
                    if (areaPassableTool.getHfutEdgesOf(area.getID()).contains(lastEdge)) {
                        toArea = area;
                        break;
                    }
                }
            }
            if (toArea == null) {
                for (HFUTEdge edge:this.edgePath){
                    Painter<?> searchpathpainter = Debugger.getPainterOfAgent(agentInfo);
                    searchpathpainter.addPaintables("SearchPathTool",false,
                            Paintable.line(edge.getEdge().getStart(),edge.getEdge().getEnd(), OptionsTool.RED));
                }
                for (Area area :this.toAreas){
                    Painter<?> searchpathpainter = Debugger.getPainterOfAgent(agentInfo);
                    searchpathpainter.addPaintables("SearchPathTool",false,
                            Paintable.shape(area.getShape(),OptionsTool.T_RED,true));
                }
//                for (HFUTEdge edge : this.edgePath) {
//                    HFUTDebugger2.getMiscLayerOf(agentInfo)
//                            .addLine(edge.getEdge().getLine(), OptionsTool.RED);
//                }
//                for (Area area : this.toAreas)
//                    HFUTDebugger2.getMiscLayerOf(agentInfo)
//                            .addShape(area.getShape(), OptionsTool.T_RED);
            }
            try {
                LinkedList<Area> areaPath = pathUtilTool.edgePath2AreaPath(edgePath, this.fromArea, toArea);
                areaPath = pathUtilTool.removeRedundantAreas(areaPath);
                this.edgePath = pathUtilTool.areaPath2edgePath(areaPath);
                this.areaPath = new Path(areaPath);
            } catch (RuntimeException e) {
                logger.error(e);
                this.areaPath = null;
                this.edgePath = null;
            }
        }
        return this.areaPath;
    }

    @Nullable
    public Double getPassCost() {
        if (this.edgePath != null) {
            double passCost = pathUtilTool.getEdgePathPassCost(this.edgePath, this.oneStepCostEvaluator);
            if (this.edgePath.size() >= 1) {
                // 计算我所在点到第一条边的通行代价
                HFUTEdge firstEdge = this.edgePath.getFirst();
                Area start = this.fromArea == null ? humanPositionTool.getMyPositionArea() : this.fromArea;
                boolean atMyPosition = start.equals(humanPositionTool.getMyPositionArea());
                double mePosX = atMyPosition ? agentInfo.getX() : start.getX();
                double mePosY = atMyPosition ? agentInfo.getY() : start.getY();
                double initialCost = areaPassableTool.getPoint2EdgePassCostByAgentType(
                        new CoordinateXY(mePosX, mePosY),
                        start,
                        firstEdge,
                        agentInfo.me().getClass());
                passCost += initialCost;
            }
            return passCost;
        }
        return null;
    }
}
