package hfutinvincible.tool.edge;

import adf.core.agent.communication.MessageManager;
import adf.core.agent.develop.DevelopData;
import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.ScenarioInfo;
import adf.core.agent.info.WorldInfo;
import adf.core.agent.module.ModuleManager;
import adf.core.agent.precompute.PrecomputeData;
import com.google.common.graph.EndpointPair;
import com.google.common.graph.MutableValueGraph;
import com.google.common.graph.ValueGraphBuilder;
import hfutinvincible.anno.AgentType;
import hfutinvincible.anno.Autowired;
import hfutinvincible.anno.HfutTool;
import hfutinvincible.message.BaseMessage;
import hfutinvincible.tool.area.ToJts;
import hfutinvincible.tool.LifeCycle;
import hfutinvincible.tool.misc.HFUTGeometryTool;
import hfutinvincible.tool.misc.OptionsTool;

import org.locationtech.jts.geom.*;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.util.GeometryFixer;
import rescuecore2.misc.Pair;
import rescuecore2.scenario.Scenario;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.ChangeSet;
import rescuecore2.worldmodel.EntityID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.BiFunction;
import org.apache.log4j.Logger;
import hfutinvincible.message.AreaEdgePairPassableMessage;
import hfutinvincible.tool.comm.MessageManageTool;
import hfutinvincible.brain.AgentBrain;

/**
 * 边对通行性判定工具
 */
@SuppressWarnings("UnstableApiUsage")
@HfutTool
@AgentType({FireBrigade.class, AmbulanceTeam.class, PoliceForce.class})
public class AreaPassableTool implements LifeCycle {


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
    Logger logger;

    /**
     * 无向无环图，顶点为所有 passable 的 Edges，图边上的值为一个 {@link EdgePairInfo}
     */
    private final MutableValueGraph<HFUTEdge, EdgePairInfo> edgeGraph;
    /** edgeGraph 中结点数 */
    private int numEdgeNode;
    /** 每个 Area 的 Blockade Polygon */
    private final Map<EntityID, Geometry> blockadePolygons;
    /** 每个 Area 的 C-free polygon */
    private final Map<EntityID, Geometry> cfreePolygons;
    /** 存储每个 Area 的所有边 */
    private final Map<EntityID, List<HFUTEdge>> hfutEdgeMap;
    /** 本周期新增的可通行边对 */
    private final Set<Pair<HFUTEdge, HFUTEdge>> newPassableEdgePairs;
    /** 本周期新增的不可通行边对 */
    private final Set<Pair<HFUTEdge, HFUTEdge>> newImpassableEdgePairs;

    private final List<Pair<Area, List<Boolean>>> edgePairInfosToSend;

    // Tools
   @Autowired MessageManageTool messageTool;

    private static final GeometryFactory GF = new GeometryFactory();


    public AreaPassableTool(){
        this.blockadePolygons = new HashMap<>();
        this.cfreePolygons = new HashMap<>();
        this.hfutEdgeMap = new HashMap<>();
        this.edgeGraph = ValueGraphBuilder.undirected().build();
        this.newPassableEdgePairs = new HashSet<>();
        this.newImpassableEdgePairs = new HashSet<>();
        this.edgePairInfosToSend = new ArrayList<>();
    }


    @Override
    public void precompute(PrecomputeData precomputeData) {
        init();
    }

    @Override
    public void resume(PrecomputeData precomputeData) {
        init();
    }

    @Override
    public void preparate() {
        init();
    }

    @Override
    public void updateInfo(MessageManager messageManager) {
        this.messageTool.updateInfo(messageManager);
        //清除清空消息
        newPassableEdgePairs.clear();
        newImpassableEdgePairs.clear();
        edgePairInfosToSend.clear();

        // 处理收到的所有 AreaEdgePairPassableMessage，
        for (AreaEdgePairPassableMessage message : messageTool.getReceivedMessages(AreaEdgePairPassableMessage.class)) {
            for (Pair<HFUTEdge, HFUTEdge> edgePair : message.getImpassableEdgePairs()) {
                HFUTEdge e1 = edgePair.first();
                HFUTEdge e2 = edgePair.second();
                updateImpassableEdgePair(e1, e2);
                // 已经标记为可以通行的一对 Edge 现在变得不可通行，说明发生了余震
                if (getPassability(e1, e2) == 1) {
                    logger.info("Aftershock detected on time " + agentInfo.getTime() + " on " + edgePair);
                    afterShock();
                }
            }
            for (Pair<HFUTEdge, HFUTEdge> edgePair : message.getPassableEdgePairs()) {
                HFUTEdge e1 = edgePair.first();
                HFUTEdge e2 = edgePair.second();
                updatePassableEdgePair(e1, e2, null, null);
            }
        }
        // 处理视野内的 areas
        ChangeSet changeSet = worldInfo.getChanged();
        Set<Area> areasInSight = new HashSet<>();
        for (EntityID id : changeSet.getChangedEntities()) {
            StandardEntity entity = worldInfo.getEntity(id);
            if (entity instanceof Area here) {
                areasInSight.add(here);
//                // 如果 here 本周期属性没有发生改变，且之前已经算过它的 C-free polygon，则跳过
//                if (changeSet.getChangedProperties(id).isEmpty() && cfreePolygons.containsKey(here))
//                    continue;
                // 否则计算 here 的 C-free polygon，并加入 cfreePolygons
                // 注意：下面计算所有 blockade 的并的时候，使用基于 GeometryCollection 的方法，而不是 g1.union(g2)
                // 因为后者会抛出 non-noded exception (我也不知道为什么)
                Geometry areaPolygon = ToJts.fromRcrsArea(here);
                List<Geometry> blockadePolygons = new ArrayList<>();
                for (Blockade blockade : worldInfo.getBlockades(here)) {
                    Geometry changedBlockadePolygon = ToJts.fromRcrsBlockade(blockade);
                    blockadePolygons.add(changedBlockadePolygon);
                }
                Geometry blockadePolygon = new GeometryCollection(blockadePolygons.toArray(new Geometry[0]), GF);
                this.blockadePolygons.put(id, blockadePolygon.union());
                Geometry bufferedBlockadePolygon = blockadePolygon.buffer(OptionsTool.BUFFER_DISTANCE, 2);
                Geometry cfreePolygon = areaPolygon.difference(bufferedBlockadePolygon);
                cfreePolygons.put(id, cfreePolygon);
                // 对 here 的所有边对
                int numPolygons = cfreePolygon.getNumGeometries();
                List<LineString> edgeLines = getLineStringsOf(here);
                List<HFUTEdge> edges = hfutEdgeMap.get(here.getID());
                if(edges == null)
                    continue;
                List<Boolean> edgePairsPassability = new ArrayList<>();
                boolean updated = false;
                for (int i = 0; i < edges.size(); i ++) {
                    HFUTEdge edgeI = edges.get(i);
                    LineString edgeLineI = edgeI.toLineString(GF);
                    for (int j = i + 1; j < edges.size(); j ++) {
                        HFUTEdge edgeJ = edges.get(j);
                        LineString edgeLineJ = edgeJ.toLineString(GF);
                        Pair<HFUTEdge, HFUTEdge> edgePair = new Pair<>(edgeI, edgeJ);
                        // 遍历所有 C-free Polygons
                        boolean passable = false;
                        for (int k = 0; k < numPolygons; k ++) {
                            // 向外扩张 20，避免浮点误差
                            Geometry gk = cfreePolygon.getGeometryN(k).buffer(20, 2);
                            // 起点和终点所在边都与 gk 相交，说明通过 gk 可以从 edgeI 到 edgeJ
                            // TODO 如果有多个怎么办？是否全部检查一遍？最后选哪个呢?
                            if (edgeLineI.intersects(gk) && edgeLineJ.intersects(gk)) {
                                updated |= updatePassableEdgePair(edgeI, edgeJ, gk, blockadePolygon);
                                edgePairsPassability.add(true);
                                passable = true;
                                break;
                            }
                        }
                        // 找完了 here 所有的 C-free polygons 里的所有 polygon 都不满足要求
                        // 认为不能从 edgeI 到 edgeJ
                        if (!passable) {
                            int passability = getPassability(edgeI, edgeJ);
                            // 如果之前还认为能从 edgeI 到 edgeJ，则认为发生了余震
                            if (passability == 1) {
                                logger.info("Aftershock detected on time " + agentInfo.getTime() + " on " + edgePair);
                                afterShock();
                            }
                            updated |= updateImpassableEdgePair(edgeI, edgeJ);
                            edgePairsPassability.add(false);
                        }
                    }
                }
                // 任何边对的通行性与已有记录不同，通过消息发送之
                // 前一个周期世界模型里啥都没得，不发送任何消息
                if (agentInfo.getTime() > 1 && updated && !edgePairsPassability.isEmpty()) {
                    edgePairInfosToSend.add(new Pair<>(here, edgePairsPassability));
                }
            }
        }
    }


    private void init() {
        logger.info("initialize...");
        // 初始化 edgeGraph, hfutEdgeMap, edgeLineStringMap
        Collection<StandardEntity> allEntities = worldInfo.getAllEntities();
        for (StandardEntity entity : allEntities) {
            if (!(entity instanceof Area here))
                continue;
            List<Edge> edges = here.getEdges();
            List<HFUTEdge> hfutEdges = new ArrayList<>();
            for (int i = 0; i < edges.size(); i ++) {
                Edge ei = edges.get(i);
                if (!ei.isPassable()) continue;
                HFUTEdge eni = new HFUTEdge(ei);
                hfutEdges.add(eni);
                for (int j = i + 1; j < edges.size(); j ++) {
                    Edge ej = edges.get(j);
                    if (!ej.isPassable()) continue;
                    HFUTEdge enj = new HFUTEdge(ej);
                    edgeGraph.putEdgeValue(eni, enj,
                            new EdgePairInfo(-1,-1, here.getID(), null));
                }
            }
            hfutEdgeMap.put(here.getID(), hfutEdges);
        }
        this.numEdgeNode = edgeGraph.nodes().size();
    }



    /**
     * @return 通行性是否与已有记录不同
     */
    private boolean updatePassableEdgePair(HFUTEdge e1, HFUTEdge e2, Geometry cfreePolygon, Geometry blockadePolygon) {
        if (cfreePolygon == null)
            cfreePolygon = GF.createEmpty(2);
        EdgePairInfo epi = edgeGraph.edgeValue(e1, e2).get();
        int passability = 1;
        if (blockadePolygon != null) {
            // 检查中心线的通行情况
            LineString centerLs = GF.createLineString(new Coordinate[]{
                    HFUTGeometryTool.getMidPointCoordinate(e1),
                    HFUTGeometryTool.getMidPointCoordinate(e2)
            });
            Geometry intersections = null;
            try {
                intersections = blockadePolygon.intersection(centerLs);
            } catch (TopologyException e) {
                try {
                    intersections = GeometryFixer.fix(blockadePolygon).intersection(centerLs);
                } catch (TopologyException ignored) {}
            }
            if (intersections != null && !intersections.isEmpty())
                passability = 2;
        }
        // 通行性与已有记录不同，则加入 passableEdgePairs
        boolean updated = false;
        if (epi.getPassability() != passability) {
            newPassableEdgePairs.add(new Pair<>(e1, e2));
            updated = true;
        }
        epi.setPassability(passability);
        epi.setLastUpdateTime(agentInfo.getTime());
        epi.setCfreePolygon(cfreePolygon);
        return updated;
    }
    /**
     * @return 通行性是否与已有记录不同
     */
    //更新所有不可通行的边对
    private boolean updateImpassableEdgePair(HFUTEdge e1, HFUTEdge e2) {
        EdgePairInfo epi = edgeGraph.edgeValue(e1, e2).get();
        // 通行性与已有记录不同，则加入 impassableEdgePairs
        boolean updated = false;
        if (epi.getPassability() != 0) {
            newImpassableEdgePairs.add(new Pair<>(e1, e2));
            updated = true;
        }
        epi.setPassability(0);
        epi.setLastUpdateTime(agentInfo.getTime());
        epi.setCfreePolygon(GF.createEmpty(2));
        return updated;
    }


    //如果有余震更新信息
    private void afterShock() {
        for (EndpointPair<HFUTEdge> edge : edgeGraph.edges()) {
            HFUTEdge from = edge.nodeU();
            HFUTEdge to = edge.nodeV();
            EdgePairInfo epi = edgeGraph.edgeValue(from, to).get();
            if (epi.getPassability() == 1) {
                epi.setPassability(-1);//更新为不可通行
                epi.setLastUpdateTime(agentInfo.getTime());
                epi.setCfreePolygon(GF.createEmpty(2));
            }
        }
    }
    //////////////////////////////////////////////////////////////////////////////////////////////////


    /**
     * 判断同一个 area 上的两点是否互相可达
     * @param commonAreaId 两个点所在的 area 的 StandardEntity
     * @param x1 第一个点的 x 坐标
     * @param y1 第一个点的 y 坐标
     * @param x2 第二个点的 x 坐标
     * @param y2 第二个点的 y 坐标
     * @return 1 - 可达 <br>
     *         0 - 不可达 <br>
     *         -1 - 不知道
     */
    public int isReachable(
            //获得一个对象commonAreaId
            @Nonnull StandardEntity commonAreaId,
            double x1,
            double y1,
            double x2,
            double y2
    ) {
        Geometry cfreePolygon = cfreePolygons.get(commonAreaId.getID());
        if (cfreePolygon == null)
            return -1;
        Point p1 = GF.createPoint(new CoordinateXY(x1, y1));
        Point p2 = GF.createPoint(new CoordinateXY(x2, y2));
        int numPolygons = cfreePolygon.getNumGeometries();
        for (int i = 0; i < numPolygons; i ++) {
            Geometry ithPolygon = cfreePolygon.getGeometryN(i);
            if (ithPolygon.contains(p1) && ithPolygon.contains(p2))
                return 1;
        }
        return 0;
    }


    /**
     * 判断同一个 area 上的两点是否互相直线可达
     * @param commonAreaId 两个点所在的 area 的 StandardEntity
     * @param x1 第一个点的 x 坐标
     * @param y1 第一个点的 y 坐标
     * @param x2 第二个点的 x 坐标
     * @param y2 第二个点的 y 坐标
     * @return 1 - 可达 <br>
     *         0 - 不可达 <br>
     *         -1 - 不知道
     */
    public int isDirectlyReachable(
            @Nonnull StandardEntity commonAreaId,
            double x1,
            double y1,
            double x2,
            double y2
    ) {
        Geometry blockadePolygon = blockadePolygons.get(commonAreaId.getID());
        if (blockadePolygon == null)
            return -1;
        if (blockadePolygon.isEmpty())
            return 1;
        Coordinate c1 = new CoordinateXY(x1, y1);
        Coordinate c2 = new CoordinateXY(x2, y2);
        LineString ls = GF.createLineString(new Coordinate[]{ c1, c2 });
        Geometry intersections = blockadePolygon.intersection(ls);
        if (!intersections.isEmpty())
            return 0;
        return 1;
    }


    /**
     * 判断同一个 area 上的两点是否互相可达
     * @param commonAreaId 两个点所在的 area 的 StandardEntity
     * @param c1 第一个点的坐标
     * @param c2 第二个点的坐标
     * @return 1 - 可达 <br>
     *         0 - 不可达 <br>
     *         -1 - 不知道
     */
    public int isReachable(
            @Nonnull StandardEntity commonAreaId,
            @Nonnull Coordinate c1,
            @Nonnull Coordinate c2
    ) {
        return isReachable(commonAreaId, c1.x, c1.y, c2.x, c2.y);
    }




    /**
     * 判断同一个 area 上的两点是否互相直线可达
     * @param commonAreaId 两个点所在的 area 的 StandardEntity
     * @param c1 第一个点的坐标
     * @param c2 第二个点的坐标
     * @return 1 - 可达 <br>
     *         0 - 不可达 <br>
     *         -1 - 不知道
     */
    public int isDirectlyReachable(
            @Nonnull StandardEntity commonAreaId,
            @Nonnull Coordinate c1,
            @Nonnull Coordinate c2
    ) {
        return isDirectlyReachable(commonAreaId, c1.x, c1.y, c2.x, c2.y);
    }

    @Nullable
    public Coordinate getFirstStuckCoord(
            final @Nonnull Coordinate fromCoord,
            final @Nonnull Coordinate toCoord,
            final @Nonnull Geometry blockadePolygon
    ) {
        LineSegment lsg = new LineSegment(fromCoord, toCoord);
        LineString lsr = GF.createLineString(new Coordinate[]{ fromCoord, toCoord });
        Geometry intersections;
        try {
            intersections = blockadePolygon.intersection(lsr);
        } catch (TopologyException e) {
            intersections = GeometryFixer.fix(blockadePolygon).intersection(lsr);
        }
        if (intersections == null || intersections.isEmpty())
            return null;
        Coordinate[] coordinates = intersections.getCoordinates();
        double minProjFactor = Double.MAX_VALUE;
        Coordinate minCoord = null;
        for (Coordinate c : coordinates) {
            double projFactor = lsg.projectionFactor(c);
            if (projFactor < minProjFactor) {
                minProjFactor = projFactor;
                minCoord = c;
            }
        }
        return minCoord;
    }



    /**
     * 返回本周期需要发送的 area 及其所有边对的通行情况
     * <p>
     * 目前只有当 area 通行情况发生改变时才会发送
     */
    @Nonnull
    public List<Pair<Area, List<Boolean>>> getEdgePairInfosToSend() {
        return edgePairInfosToSend;
    }


    /**
     * 返回一个 area 的不可通行度.
     * <p>
     * 不可通行度由这个 area 所有边对中不可通行的边对数 / 边对总数得到
     */
    public double getImpassableRatio(@Nonnull StandardEntity areaId) {
        Area area = (Area) worldInfo.getEntity(areaId.getID());
        if (area == null)
            throw new IllegalArgumentException("areaId " + areaId + " is not valid");
        return getImpassableRatio(area);
    }


    /**
     * 获得一个 area 上所有 blockade 的 repair cost 之和
     */
    public double getTotalRepairCost(@Nonnull StandardEntity areaId) {
        Area area = (Area) worldInfo.getEntity(areaId.getID());
        if (area == null)
            throw new IllegalArgumentException("areaId " + areaId + " is not valid");
        return getTotalRepairCost(area);
    }

    /**
     * 返回一个 area 的不可通行度.
     * <p>
     * 不可通行度由这个 area 所有边对中不可通行的边对数 / 边对总数得到
     */
    public double getImpassableRatio(@Nonnull Area area) {
        List<Integer> passability = getEdgePairPassabilityOfArea(area);
        if (passability.isEmpty())
            return 0.0; // TODO 是否不合适？
        double total = 0.0;
        for (int p : passability) {
            if (p == 0)         total += 1.0; // 完全堵住
            else if (p == 2)    total += 0.5; // 中心线堵住
            else if (p == -1)   total += 0.2; // 不知道堵住没
        }
        return total / passability.size();
    }


    /**
     * 获得一个 area 上所有 blockade 的 repair cost 之和
     */
    public double getTotalRepairCost(@Nonnull Area area) {
        double totalRepairCost = 0.0;
        if (area.isBlockadesDefined()) {
            for (EntityID blockadeId : area.getBlockades()) {
                Blockade blockade = (Blockade) worldInfo.getEntity(blockadeId);
                if (blockade == null)
                    continue;
                totalRepairCost += blockade.getRepairCost();
            }
        }
        return totalRepairCost;
    }


    /**
     * 返回一个 area 的通行情况（各边对间是否可以通行）
     */
    @Nonnull
    public List<Integer> getEdgePairPassabilityOfArea(@Nonnull Area area) {
        List<HFUTEdge> edges = getHfutEdgesOf(area.getID());
        if (edges == null || edges.size() <= 1)
            return new ArrayList<>();
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < edges.size(); i ++) {
            HFUTEdge ithEdge = edges.get(i);
            for (int j = i + 1; j < edges.size(); j ++) {
                HFUTEdge jthEdge = edges.get(j);
                result.add(getPassability(ithEdge, jthEdge));
            }
        }
        return result;
    }

    /**
     * 返回一个函数，用于估计两条 HFUTEdge 间通行代价
     */
    public BiFunction<HFUTEdge, HFUTEdge, Double> getEdgePairPassCostEvaluator(
            double passableRoadFactor,
            double impassableRoadFactor,
            double unknownRoadFactor,
            double unknownBuildingFactor,
            double unbrokenBuildingFactor,
            double brokenBuildingFactor
    ) {
        return (fromEdge, toEdge) -> {
            EdgePairInfo edgeValue = edgeGraph.edgeValueOrDefault(fromEdge, toEdge, null);

            // 计算两边之间通行距离 d = 起点边中点 到 area 中心距离 + area 中心 到 目标边中点距离
            // area 为起点边和目标边中间夹着的 Area
            // 不直接算起点边和终点边中点之间的距离，是为了尽可能防止出现赘余边
            Area betweenArea = (Area) worldInfo.getEntity(edgeValue.getAreaId());
            Coordinate fromEdgeMid = HFUTGeometryTool.getMidPointCoordinate(fromEdge);
            Coordinate toEdgeMid = HFUTGeometryTool.getMidPointCoordinate(toEdge);
            Coordinate areaCentroid = new CoordinateXY(betweenArea.getX(), betweenArea.getY());
            double passCost = fromEdgeMid.distance(areaCentroid) + areaCentroid.distance(toEdgeMid);

            if (betweenArea instanceof Road) {
                int passability = edgeValue.getPassability();
                int time = edgeValue.getLastUpdateTime();
                if (passability == -1)
                    passCost *= unknownRoadFactor;
                else if (passability == 1)
                    passCost *= passableRoadFactor;
                else {
                    // TODO 改为按时间衰减的函数
                    passCost *= impassableRoadFactor;
                }
            } else if (betweenArea instanceof Building building) {
                if (!building.isBrokennessDefined())
                    passCost *= unknownBuildingFactor;
                else {
                    double brokenness = building.getBrokenness();
                    if (brokenness == 0)
                        passCost *= unbrokenBuildingFactor;
                    else passCost *= brokenBuildingFactor;
                }
            }
            return passCost;
        };
    }


    @Nonnull
    public BiFunction<HFUTEdge, HFUTEdge, Double> getEdgePairPassCostEvaluatorByType(
            @Nonnull Class<? extends StandardEntity> humanType
    ) {
        if (humanType.equals(PoliceForce.class)) {
            return getEdgePairPassCostEvaluator(
                    OptionsTool.PF_PASSABLE_ROAD_FACTOR,
                    OptionsTool.PF_IMPASSABLE_ROAD_FACTOR,
                    OptionsTool.PF_UNKNOWN_ROAD_FACTOR,
                    OptionsTool.PF_UNKNOWN_BUILDING_FACTOR,
                    OptionsTool.PF_UNBROKEN_BUILDING_FACTOR,
                    OptionsTool.PF_BROKEN_BUILDING_FACTOR
            );
        } else if (humanType.equals(AmbulanceTeam.class)) {
            return getEdgePairPassCostEvaluator(
                    OptionsTool.AT_PASSABLE_ROAD_FACTOR,
                    OptionsTool.AT_IMPASSABLE_ROAD_FACTOR,
                    OptionsTool.AT_UNKNOWN_ROAD_FACTOR,
                    OptionsTool.AT_UNKNOWN_BUILDING_FACTOR,
                    OptionsTool.AT_UNBROKEN_BUILDING_FACTOR,
                    OptionsTool.AT_BROKEN_BUILDING_FACTOR
            );
        } else if (humanType.equals(FireBrigade.class)) {
            return getEdgePairPassCostEvaluator(
                    OptionsTool.FB_PASSABLE_ROAD_FACTOR,
                    OptionsTool.FB_IMPASSABLE_ROAD_FACTOR,
                    OptionsTool.FB_UNKNOWN_ROAD_FACTOR,
                    OptionsTool.FB_UNKNOWN_BUILDING_FACTOR,
                    OptionsTool.FB_UNBROKEN_BUILDING_FACTOR,
                    OptionsTool.FB_BROKEN_BUILDING_FACTOR
            );
        } else {
            String errMsg = "Invalid human type " + humanType;
            logger.error(errMsg);
            throw new IllegalArgumentException(errMsg);
        }
    }

    public BiFunction<HFUTEdge, HFUTEdge, Double> getEdgePairPassDistanceEvaluator() {
        return (fromEdge, toEdge) -> {
            EdgePairInfo edgeValue = edgeGraph.edgeValueOrDefault(fromEdge, toEdge, null);

            // 计算两边之间通行距离 d = 起点边中点 到 area 中心距离 + area 中心 到 目标边中点距离
            // area 为起点边和目标边中间夹着的 Area
            // 不直接算起点边和终点边中点之间的距离，是为了尽可能防止出现赘余边
            Area betweenArea = (Area) worldInfo.getEntity(edgeValue.getAreaId());
            Coordinate fromEdgeMid = HFUTGeometryTool.getMidPointCoordinate(fromEdge);
            Coordinate toEdgeMid = HFUTGeometryTool.getMidPointCoordinate(toEdge);
            Coordinate areaCentroid = new CoordinateXY(betweenArea.getX(), betweenArea.getY());
            return fromEdgeMid.distance(areaCentroid) + areaCentroid.distance(toEdgeMid);
        };
    }

    /**
     * 估计两边之间的通行距离
     * @param fromEdge 出发边
     * @param toEdge 目标边
     */
    public double getEdgePairPassDistance(HFUTEdge fromEdge, HFUTEdge toEdge) {
        return getEdgePairPassDistanceEvaluator().apply(fromEdge, toEdge);
    }

    /**
     * 估计两边之间的通行代价，权重根据智能体的类型确定
     * @param fromEdge 出发边
     * @param toEdge 目标边
     * @param humanType 智能体类型
     */
    public double getEdgePairPassCostByAgentType(
            @Nonnull HFUTEdge fromEdge,
            @Nonnull HFUTEdge toEdge,
            @Nonnull Class<? extends StandardEntity> humanType
    ) {
        return getEdgePairPassCostEvaluatorByType(humanType)
                .apply(fromEdge, toEdge);
    }

    @Nonnull
    public BiFunction<Pair<Coordinate, Area>, HFUTEdge, Double> getPoint2EdgePassDistanceEvaluator() {
        return (pair, toEdge) -> {
            Coordinate fromCoord = pair.first();
            Coordinate toEdgeMidCoord = HFUTGeometryTool.getMidPointCoordinate(toEdge);
            return fromCoord.distance(toEdgeMidCoord);
        };
    }

    /**
     * 估计点到一条边之间的通行距离
     * @param fromCoord 出发点
     * @param fromArea 出发点所在 area
     * @param toEdge 目标边
     */
    public double getPoint2EdgePassDistance(CoordinateXY fromCoord, Area fromArea, HFUTEdge toEdge) {
        return getPoint2EdgePassDistanceEvaluator().apply(new Pair<>(fromCoord, fromArea), toEdge);
    }

    @Nonnull
    public BiFunction<Pair<Coordinate, Area>, HFUTEdge, Double> getPoint2EdgePassCostEvaluatorByType(
            @Nonnull Class<? extends StandardEntity> humanType
    ) {
        if (humanType.equals(PoliceForce.class)) {
            return getPoint2EdgePassCostEvaluator(
                    OptionsTool.PF_PASSABLE_ROAD_FACTOR,
                    OptionsTool.PF_IMPASSABLE_ROAD_FACTOR,
                    OptionsTool.PF_UNKNOWN_ROAD_FACTOR,
                    OptionsTool.PF_UNKNOWN_BUILDING_FACTOR,
                    OptionsTool.PF_UNBROKEN_BUILDING_FACTOR,
                    OptionsTool.PF_BROKEN_BUILDING_FACTOR
            );
        } else if (humanType.equals(AmbulanceTeam.class)) {
            return getPoint2EdgePassCostEvaluator(
                    OptionsTool.AT_PASSABLE_ROAD_FACTOR,
                    OptionsTool.AT_IMPASSABLE_ROAD_FACTOR,
                    OptionsTool.AT_UNKNOWN_ROAD_FACTOR,
                    OptionsTool.AT_UNKNOWN_BUILDING_FACTOR,
                    OptionsTool.AT_UNBROKEN_BUILDING_FACTOR,
                    OptionsTool.AT_BROKEN_BUILDING_FACTOR
            );
        } else if (humanType.equals(FireBrigade.class)) {
            return getPoint2EdgePassCostEvaluator(
                    OptionsTool.FB_PASSABLE_ROAD_FACTOR,
                    OptionsTool.FB_IMPASSABLE_ROAD_FACTOR,
                    OptionsTool.FB_UNKNOWN_ROAD_FACTOR,
                    OptionsTool.FB_UNKNOWN_BUILDING_FACTOR,
                    OptionsTool.FB_UNBROKEN_BUILDING_FACTOR,
                    OptionsTool.FB_BROKEN_BUILDING_FACTOR
            );
        } else {
            String errMsg = "Invalid human type " + humanType;
            logger.error(errMsg);
            throw new IllegalArgumentException(errMsg);
        }
    }

    /**
     * 估计点到一条边之间的通行代价，权重根据智能体类型确定
     * @param fromCoord 出发点坐标
     * @param fromArea 出发点所在 Area
     * @param toEdge 目标边
     * @param humanType 智能体类型
     */
    public double getPoint2EdgePassCostByAgentType(
            @Nonnull Coordinate fromCoord,
            @Nonnull Area fromArea,
            @Nonnull HFUTEdge toEdge,
            @Nonnull Class<? extends StandardEntity> humanType
    ) {
        return getPoint2EdgePassCostEvaluatorByType(humanType)
                .apply(new Pair<>(fromCoord, fromArea), toEdge);
    }

    public List<LineString> getLineStringsOf(@Nonnull Area area) {
        List<Edge> edges = area.getEdges();
        List<LineString> ret = new ArrayList<>();
        for (Edge e : edges) {
            Coordinate from = new CoordinateXY(e.getStartX(), e.getStartY());
            Coordinate to = new CoordinateXY(e.getEndX(), e.getEndY());
            LineString ls = GF.createLineString(new Coordinate[] {from, to});
            ret.add(ls);
        }
        return ret;
    }


    /**
     * 返回一个函数，用于估计一个点到一条 HFUTEdge 间的通行代价
     */
    @Nonnull
    public BiFunction<Pair<Coordinate, Area>, HFUTEdge, Double> getPoint2EdgePassCostEvaluator(
            double passableRoadFactor,
            double impassableRoadFactor,
            double unknownRoadFactor,
            double unknownBuildingFactor,
            double unbrokenBuildingFactor,
            double brokenBuildingFactor
    ) {
        return (fromPos, toEdge) -> {

            Coordinate fromCoord = fromPos.first();
            Area fromArea = fromPos.second();

            Coordinate toEdgeMidCoord = HFUTGeometryTool.getMidPointCoordinate(toEdge);
            double passCost = fromCoord.distance(toEdgeMidCoord);

            if (fromArea instanceof Road) {
                Pair<Integer, Geometry> passability = getPassability(fromCoord, fromArea, toEdge);
                if (passability.first() == -1)
                    passCost *= unknownRoadFactor;
                else if (passability.first() == 1)
                    passCost *= passableRoadFactor;
                else {
                    passCost *= impassableRoadFactor;
                }
            } else // 如果 betweenArea 是建筑，则根据建筑的损坏情况调整通行代价
                if (fromArea instanceof Building building) {
                    if (!building.isBrokennessDefined())
                        passCost *= unknownBuildingFactor;
                    else {
                        double brokenness = building.getBrokenness();
                        if (brokenness == 0)
                            passCost *= unbrokenBuildingFactor;
                        else passCost *= brokenBuildingFactor;
                    }
                }

            return passCost;
        };
    }


    public Set<Pair<HFUTEdge, HFUTEdge>> getNewPassableEdgePairs() {
        return newPassableEdgePairs;
    }

    public Set<Pair<HFUTEdge, HFUTEdge>> getNewImpassableEdgePairs() {
        return newImpassableEdgePairs;
    }

    /**
     * 获得一个 area 的所有边（HFUTEdge）
     * @param areaId
     * @return
     */
    @Nullable
    public List<HFUTEdge> getHfutEdgesOf(EntityID areaId) {
        List<HFUTEdge> edges = hfutEdgeMap.get(areaId);
        return edges;
    }


    /**
     * 判断边 e1 和 e2 是否互相可达
     * @return -1 - unknown <br>
     *          0 - impassable <br>
     *          1 - passable
     *          2 - centerBlock
     */
    public int getPassability(HFUTEdge e1, HFUTEdge e2) {
        EdgePairInfo epi = edgeGraph.edgeValueOrDefault(e1, e2, null);
        if (epi == null) // 不存在这条边，认为 impassable
            return 0;
        else return epi.getPassability();
    }


    /**
     * 判断能否从一个点到达一条边
     * @param fromX 出发点 x 坐标
     * @param fromY 出发点 y 坐标
     * @param checkAreaId 出发点和目标边所在的 area（应该在视野范围内）
     * @param toEdge 目标边
     */
    public boolean isReachableInSight(
            double fromX, double fromY,
            @Nonnull StandardEntity checkAreaId,
            @Nonnull HFUTEdge toEdge
    ) {
        // 只有路上可能有障碍
        if (!(worldInfo.getEntity(checkAreaId.getID()) instanceof Road))
            return true;
        Geometry cfreePolygon = cfreePolygons.get(checkAreaId.getID());
        if (cfreePolygon == null)
            return false;
        cfreePolygon = cfreePolygon.buffer(20, 2);
        Point fromPoint = GF.createPoint(new CoordinateXY(fromX, fromY));
        LineString toEdgeLs = GF.createLineString(new Coordinate[]{
                new CoordinateXY(toEdge.getStartX(), toEdge.getStartY()),
                new CoordinateXY(toEdge.getEndX(), toEdge.getEndY())
        });
        int numGeo = cfreePolygon.getNumGeometries();
        for (int i = 0; i < numGeo; i ++) {
            Geometry g = cfreePolygon.getGeometryN(i);
            Geometry intersection = null;
            try {
                intersection = g.intersection(toEdgeLs);
            } catch (TopologyException e) {
                try {
                    intersection = GeometryFixer.fix(g).intersection(toEdgeLs);
                } catch (Exception ignored) {}
            }
            if (intersection == null || intersection.isEmpty())
                continue;
            if (g.contains(fromPoint))
                return true;
        }
        return false;
    }

    /**
     * 判断能否从出发点 c 到目的边 e2
     * @param c 出发点
     * @param areaId 点所在 area 的 StandardEntity
     * @param e2 目的边
     * @return first: -1 - unknown <br>
     *                0  - impassable <br>
     *                1  - passable
     *         second: 如果 passable，则为 cfreePolygon，否则为 null
     */
    @Deprecated
    public Pair<Integer, Geometry> getPassability(Coordinate c, StandardEntity areaId, HFUTEdge e2) {
        Geometry cfreePolygon = cfreePolygons.get(areaId.getID());
        // 找不到 cfreePolygon，说明我没看到过这个地方
        if (cfreePolygon == null)
            return new Pair<>(-1, null);
        int numPolygons = cfreePolygon.getNumGeometries();
        LineString edgeLine = ToJts.fromRcrsEdge(e2.getEdge()).toGeometry(GF);
        Point mePoint = GF.createPoint(c);
        // 存在任何一个 c free polygon 包含指定点且与指定边相交，则认为可以通行
        for (int i = 0; i < numPolygons; i ++) {
            Geometry g = cfreePolygon.getGeometryN(i).buffer(20, 2);
            if (edgeLine.intersects(g) && mePoint.within(g)) {
                return new Pair<>(1, g);
            }
        }
        return new Pair<>(0, null);
    }

    /**
     * 判断能否从出发点 (cx, cy) 到目的边 e2
     * @param cx 出发点 x
     * @param cy 出发点 y
     * @param areaId 点所在 area 的 StandardEntity
     * @param e2 目的边
     * @return first: -1 - unknown <br>
     *                0  - impassable <br>
     *                1  - passable
     *         second: 如果 passable，则为 cfreePolygon，否则为 null
     */
    @Deprecated
    public Pair<Integer, Geometry> getPassability(int cx, int cy, StandardEntity areaId, HFUTEdge e2) {
        return getPassability(new Coordinate(cx, cy), areaId, e2);
    }

    /**
     * 判断能否从出发点 (cx, cy) 到目的边 e2
     * @param cx 出发点 x
     * @param cy 出发点 y
     * @param areaId 点所在 area 的 StandardEntity
     * @param e2 目的边
     * @return first: -1 - unknown <br>
     *                0  - impassable <br>
     *                1  - passable
     *         second: 如果 passable，则为 cfreePolygon，否则为 null
     */
    @Deprecated
    public Pair<Integer, Geometry> getPassability(double cx, double cy, StandardEntity areaId, HFUTEdge e2) {
        return getPassability(new Coordinate(cx, cy), areaId, e2);
    }

    /**
     * 返回一个 edgePath 中所有明确堵住的 Area
     */
    public List<EntityID> getBlockedAreaIds(List<HFUTEdge> edgePath) {
        int numEdge = edgePath.size();
        List<EntityID> blockedAreas = new ArrayList<>(numEdge);
        for (int i = 0; i < numEdge - 1; i ++) {
            HFUTEdge e1 = edgePath.get(i);
            HFUTEdge e2 = edgePath.get(i + 1);
            EdgePairInfo epi = edgeGraph.edgeValueOrDefault(e1, e2, null);
            if (epi == null) {
                String errMsg = "IMPOSSIBLE! Two adjacent edge in an edgePath should always exist in edgeGraph";
                logger.error(errMsg);
                throw new RuntimeException(errMsg);
            }
            if (epi.getPassability() == 0)
                blockedAreas.add(epi.getAreaId());
        }
        return blockedAreas;
    }

    /**
     * 获得两 Edge 中间夹着的 Area 的 EntityID
     */
    public EntityID getAreaIdBetween(HFUTEdge e1, HFUTEdge e2) {
        EdgePairInfo epi = edgeGraph.edgeValueOrDefault(e1, e2, null);
        if (epi == null) {
            logger.error("" + e1 + " and " + e2 + " are not adjacent.");
            return null;
        }
        return epi.getAreaId();
    }


    /**
     * 返回与 edge 邻接的所有结点
     */
    public Set<HFUTEdge> getAdjacentEdges(HFUTEdge edge) {
        return edgeGraph.adjacentNodes(edge);
    }

    /**
     * 返回 edgeGraph 中所有结点数
     */
    public int getEdgeNum() {
        return numEdgeNode;
    }

    /**
     * 返回一个 Map，Key 是 Area 的 EntityID，Value 是 key area 的 C-free polygon
     */
    public Map<EntityID, Geometry> getCfreePolygons() {
        return cfreePolygons;
    }

    /**
     * 返回 id 的 C-free Polygon <br>
     * 如果我从未看到过 id，或者指定 id 是 Building 这种不可能有障碍的 area，则返回 null
     */
    @Nullable
    public Geometry getCfreePolygonOf(@Nonnull EntityID id) {
        Geometry geometry = cfreePolygons.get(id);
        return geometry;
    }

    public Coordinate getFixPosition(EntityID checkAreaId){
        Geometry cfreePolygon = cfreePolygons.get(checkAreaId);
        if (cfreePolygon == null) {
            logger.info("can not find fix position");
            return null;
        }
        cfreePolygon = cfreePolygon.buffer(20, 2);
        Coordinate fromCoord = new CoordinateXY((int) agentInfo.getX(), (int) agentInfo.getY());
        // 修正位置，保证 fromCoord 在 cfreePolygon 内
        if (!cfreePolygon.contains(GF.createPoint(fromCoord))) {
            fromCoord = HFUTGeometryTool.getNearestPointOnGeometry(new CoordinateXY((int) agentInfo.getX(), (int) agentInfo.getY()), cfreePolygon);
        }
        return fromCoord;
    }

    /**
     * 返回 id 的 Blockade Polygon <br>
     * 如果我从未看到过 id，或者指定 id 是 Building 这种不可能有障碍的 area，则返回 null
     */
    @Nullable
    public Geometry getBlockadePolygonOf(@Nonnull EntityID id) {
        Geometry geometry = blockadePolygons.get(id);
        return geometry;
    }
    public Set<EndpointPair<HFUTEdge>> edges() {
        return edgeGraph.edges();
    }

    public Map<EntityID, List<HFUTEdge>> getHfutEdgeMap() {
        return hfutEdgeMap;
    }

    public EdgePairInfo edgeValue(HFUTEdge U, HFUTEdge V) {
        return edgeGraph.edgeValueOrDefault(U, V,  null);
    }



}








