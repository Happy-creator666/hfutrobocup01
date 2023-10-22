package hfutinvincible.tool.path;

import adf.core.agent.develop.DevelopData;
import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.ScenarioInfo;
import adf.core.agent.info.WorldInfo;
import adf.core.agent.module.ModuleManager;
import hfutinvincible.anno.AgentType;
import hfutinvincible.anno.Autowired;
import hfutinvincible.anno.HfutTool;
import hfutinvincible.tool.LifeCycle;
import hfutinvincible.tool.edge.AreaPassableTool;
import hfutinvincible.tool.edge.EdgePairInfo;
import hfutinvincible.tool.edge.HFUTEdge;
import hfutinvincible.tool.misc.HFUTGeometryTool;
import org.apache.log4j.Logger;
import org.locationtech.jts.geom.Coordinate;
import rescuecore2.misc.Pair;
import rescuecore2.misc.geometry.Point2D;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.function.BiFunction;

@HfutTool
@AgentType({AmbulanceTeam.class, FireBrigade.class, PoliceForce.class})
public class PathUtilTool implements LifeCycle {
    @Autowired
    private WorldInfo worldInfo;
    @Autowired
    private Logger logger;

    // Tools
    @Autowired
    private AreaPassableTool areaPassableTool;

    public PathUtilTool() {

    }

    /**
     * 指定如何计算单步移动的通行代价，估计一条边路径的通行代价
     */
    public double getEdgePathPassCost(
            @Nonnull List<HFUTEdge> edgePath,
            @Nonnull BiFunction<HFUTEdge, HFUTEdge, Double> oneStepCostEvaluator) {
        if (edgePath.size() <= 1)
            return 0.0;
        double result = 0.0;
        Iterator<HFUTEdge> iter1 = edgePath.listIterator(0);
        Iterator<HFUTEdge> iter2 = edgePath.listIterator(1);
        while (iter2.hasNext()) {
            HFUTEdge e1 = iter1.next();
            HFUTEdge e2 = iter2.next();
            result += oneStepCostEvaluator.apply(e1, e2);
        }
        return result;
    }

    public double getEdgePathPassDistance(@Nonnull List<HFUTEdge> edgePath) {
        if (edgePath.size() <= 1)
            return 0.0;
        double result = 0.0;
        Iterator<HFUTEdge> iter1 = edgePath.listIterator(0);
        Iterator<HFUTEdge> iter2 = edgePath.listIterator(1);
        while (iter2.hasNext()) {
            HFUTEdge e1 = iter1.next();
            HFUTEdge e2 = iter2.next();
            result += areaPassableTool.getEdgePairPassDistance(e1, e2);
        }
        return result;
    }

    public double getEdgePathPassDistance(
            @Nonnull List<HFUTEdge> edgePath,
            double fromX,
            double fromY
    ) {
        if (edgePath.isEmpty())
            return 0.0;
        HFUTEdge firstEdge = edgePath.get(0);
        Point2D firstEdgeMidPoint = HFUTGeometryTool.getMidPoint(firstEdge);
        double x = HFUTGeometryTool.getDistance(fromX, fromY, firstEdgeMidPoint.getX(), firstEdgeMidPoint.getY());
        return getEdgePathPassDistance(edgePath) + x;
    }

    public double getAreaPathPassDistance(@Nonnull List<Area> path) {
        if (path.size() < 2)
            return 0.0;
        LinkedList<HFUTEdge> edgePath = areaPath2edgePath(path);
        assert edgePath.size() >= 1;
        double result = getEdgePathPassDistance(edgePath);
        HFUTEdge firstEdge = edgePath.getFirst();
        Coordinate firstEdgeMidPoint = HFUTGeometryTool.getMidPointCoordinate(firstEdge);
        Area firstArea = path.get(0);
        result += HFUTGeometryTool.getDistance(
                firstArea.getX(), firstArea.getY(),
                firstEdgeMidPoint.x, firstEdgeMidPoint.y);
        HFUTEdge lastEdge = edgePath.getLast();
        Coordinate lastEdgeMidPoint = HFUTGeometryTool.getMidPointCoordinate(lastEdge);
        Area lastArea = path.get(path.size() - 1);
        result += HFUTGeometryTool.getDistance(
                lastArea.getX(), lastArea.getY(),
                lastEdgeMidPoint.x, lastEdgeMidPoint.y);
        return result;
    }

    public double getAreaPathPassDistance(@Nonnull List<Area> path, double fromPosX, double fromPosY, double toPosX, double toPosY) {
        if (path.size() < 2)
            return 0.0;
        Iterator<Area> iter1 = path.listIterator(0);
        Iterator<Area> iter2 = path.listIterator(1);
        boolean start = false;
        LinkedList<HFUTEdge> edgePath = new LinkedList<>();
        while (iter2.hasNext()) {
            Area a1 = iter1.next();
            Area a2 = iter2.next();
            // 从包含 (fromPosX, fromPosY) 的第一块 area 开始计算
            if (a1.getShape().contains(fromPosX, fromPosY))
                start = true;
            if (start) {
                Edge e = a1.getEdgeTo(a2.getID());
                assert e != null;
                edgePath.add(new HFUTEdge(e));
            }
            // 到包含 (toPosX, toPosY) 的第一块 area 为止
            if (a2.getShape().contains(toPosX, toPosY))
                break;
        }
        assert edgePath.size() > 0;
        double result = getEdgePathPassDistance(edgePath);
        HFUTEdge firstEdge = edgePath.getFirst();
        Coordinate firstEdgeMidPoint = HFUTGeometryTool.getMidPointCoordinate(firstEdge);
        result += HFUTGeometryTool.getDistance(fromPosX, fromPosY, firstEdgeMidPoint.x, firstEdgeMidPoint.y);
        HFUTEdge lastEdge = edgePath.getLast();
        Coordinate lastEdgeMidPoint = HFUTGeometryTool.getMidPointCoordinate(lastEdge);
        result += HFUTGeometryTool.getDistance(toPosX, toPosY, lastEdgeMidPoint.x, lastEdgeMidPoint.y);
        return result;
    }

    /**
     * 返回指定 areaIdPath 第一个被卡住的 area
     */
    @Nullable
    public EntityID getFirstImpassableAreaIdOfAreaIdPath(
            int fromX, int fromY, @Nonnull final List<EntityID> areaIdPath) {
        if (areaIdPath.size() <= 1)
            return null;
        final List<Area> areaPath = areaIdPath.stream()
                .map(worldInfo::getEntity)
                .map(Objects::requireNonNull)
                .map(Area.class::cast)
                .toList();
        // 检测起点能不能到第一条边
        Edge e = areaPath.get(0).getEdgeTo(areaIdPath.get(1));
        if (e == null) // IMPOSSIBLE BRANCH
            return null;
        EntityID firstAreaId = areaIdPath.get(0);
        if (!areaPassableTool.isReachableInSight(fromX, fromY, firstAreaId, new HFUTEdge(e)))
            return firstAreaId;
        // 检测后续边对通行情况
        final LinkedList<HFUTEdge> edgePath = areaPath2edgePath(areaPath);
        return getFirstImpassableAreaIdOfEdgePath(edgePath);
    }

    /**
     * 返回指定 edgePath 第一个被卡住的 area
     */
    @Nullable
    public EntityID getFirstImpassableAreaIdOfEdgePath(@Nonnull final List<HFUTEdge> edgePath) {
        final Iterator<HFUTEdge> iter1 = edgePath.listIterator(0);
        final Iterator<HFUTEdge> iter2 = edgePath.listIterator(1);
        while (iter2.hasNext()) {
            HFUTEdge e1 = iter1.next();
            HFUTEdge e2 = iter2.next();
            EntityID betweenAreaId = areaPassableTool.getAreaIdBetween(e1, e2);
            if (areaPassableTool.getPassability(e1, e2) == 0)
                return betweenAreaId;
        }
        return null;
    }

    /**
     * 将 edgePath 转换为 areaPath
     * @param edgePath
     * @param fromArea 起点 area，会被加入到路径开头
     * @param toArea 终点 area，会被加入到路径
     * @return
     */
    public LinkedList<Area> edgePath2AreaPath(
            @Nonnull List<HFUTEdge> edgePath,
            @Nonnull Area fromArea,
            @Nonnull Area toArea
    ) {
        if (edgePath.size() == 0) {
            if (fromArea.equals(toArea))
                return new LinkedList<>(List.of(fromArea));
            else return new LinkedList<>();
        } else if (edgePath.size() == 1) {
            HFUTEdge e1 = edgePath.get(0);
            Edge e2 = fromArea.getEdgeTo(toArea.getID());
            if (e2 != null && e1.equals(new HFUTEdge(e2))) {
                return new LinkedList<>(List.of(fromArea, toArea));
            }
            List<HFUTEdge> fromAreaEdges = areaPassableTool.getHfutEdgesOf(fromArea.getID());
            if (fromAreaEdges != null && fromAreaEdges.contains(e1))
                return new LinkedList<>(List.of(fromArea));
            return new LinkedList<>();
        } else {
            LinkedList<Area> roadPath = new LinkedList<>();
            Iterator<HFUTEdge> iter1 = edgePath.listIterator();
            Iterator<HFUTEdge> iter2 = edgePath.listIterator(1);
            while (iter2.hasNext()) {
                HFUTEdge e1 = iter1.next();
                HFUTEdge e2 = iter2.next();
                EdgePairInfo epi = areaPassableTool.edgeValue(e1, e2);
                Area curArea = (Area) worldInfo.getEntity(epi.getAreaId());
                if (roadPath.isEmpty())
                    roadPath.add(curArea);
                else {
                    Area prevArea = roadPath.getLast();
                    if (prevArea.equals(curArea))
                        continue;
                    else if (prevArea.getEdgeTo(curArea.getID()) == null)
                        break;
                    roadPath.add(curArea);
                }
            }
            if (!roadPath.getFirst().equals(fromArea))
                roadPath.offerFirst(fromArea);
            if (!roadPath.getLast().equals(toArea))
                roadPath.offerLast(toArea);
            return roadPath;
        }
    }

    /**
     * 将一个 areaPath 转换为 edgePath，只会考虑两个 Area 之间的边
     */
    public LinkedList<HFUTEdge> areaPath2edgePath(List<Area> areaPath) {
        Iterator<Area> iter1 = areaPath.listIterator();
        Iterator<Area> iter2 = areaPath.listIterator(1);
        LinkedList<HFUTEdge> edgePath = new LinkedList<>();
        while (iter2.hasNext()) {
            Area area1 = iter1.next();
            Area area2 = iter2.next();
            Edge edge = area1.getEdgeTo(area2.getID());
            if (edge == null) {
                String errMsg = "IMPOSSIBLE! After areaPath construction process, area1 " + area1 + " and area2 " + area2 + "should always be adjacent.";
                logger.error(errMsg);
                throw new RuntimeException(errMsg);
            }
            edgePath.add(new HFUTEdge(edge));
        }
        return edgePath;
    }

    /**
     * 删除赘余 area
     */
    public LinkedList<Area> removeRedundantAreas(LinkedList<Area> roadPath) {
        logger.info(roadPath);
        LinkedList<Area> result = new LinkedList<>();
        result.add(roadPath.getFirst()); // 第一个 area 先加入 result
        Iterator<Area> iter0 = roadPath.listIterator(0);
        Iterator<Area> iter1 = roadPath.listIterator(1);
        Iterator<Area> iter2 = roadPath.listIterator(2);
        while (iter2.hasNext()) {
            Area area0 = iter0.next();
            Area area1 = iter1.next();
            Area area2 = iter2.next();
            // 中间的 area 是赘余的
            if (area0.getEdgeTo(area2.getID()) != null)
                continue;
            result.add(area1);
        }
        result.add(roadPath.getLast());
        return result;
    }

    /**
     * 将一个 edgePath 转换为边对集
     */
    public Set<Pair<HFUTEdge, HFUTEdge>> edgePath2edgePairs(LinkedList<HFUTEdge> edgePath) {
        Iterator<HFUTEdge> iter1 = edgePath.iterator();
        Iterator<HFUTEdge> iter2 = edgePath.iterator();
        iter2.next();
        Set<Pair<HFUTEdge, HFUTEdge>> ret = new HashSet<>();
        while (iter2.hasNext())
            ret.add(new Pair<>(iter1.next(), iter2.next()));
        return ret;
    }
}