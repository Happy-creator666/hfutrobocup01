package hfutinvincible.tool.executor;

import adf.core.agent.action.Action;
import adf.core.agent.action.common.ActionMove;
import adf.core.agent.action.common.ActionRest;
import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.WorldInfo;
import hfutinvincible.anno.AgentType;
import hfutinvincible.anno.Autowired;
import hfutinvincible.anno.Disabled;
import hfutinvincible.anno.HfutTool;
import hfutinvincible.debug.Debugger;
import hfutinvincible.debug.paintable.Paintable;
import hfutinvincible.tool.CalcContext;
import hfutinvincible.tool.LifeCycle;
import hfutinvincible.tool.Params;
import hfutinvincible.tool.edge.AreaPassableTool;
import hfutinvincible.tool.edge.HFUTEdge;
import hfutinvincible.tool.human.HumanPositionTool;
import hfutinvincible.tool.human.StuckInfoTool;
import hfutinvincible.tool.human.StuckType;
import hfutinvincible.tool.misc.HFUTGeometryTool;
import hfutinvincible.tool.misc.OptionsTool;
import hfutinvincible.tool.path.PathUtilTool;
import org.apache.log4j.Logger;
import org.locationtech.jts.algorithm.distance.DistanceToPoint;
import org.locationtech.jts.algorithm.distance.PointPairDistance;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.geom.Point;
import rescuecore2.misc.geometry.Line2D;
import rescuecore2.misc.geometry.Point2D;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;

import javax.annotation.Nonnull;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@HfutTool
//@Disabled
@AgentType({FireBrigade.class, AmbulanceTeam.class})
public class FbAtStuckEscapeTool3 implements LifeCycle {

    @Autowired
    private AgentInfo agentInfo;
    @Autowired
    private WorldInfo worldInfo;
    @Autowired
    private Logger logger;

    //Tools
    @Autowired
    private StuckInfoTool stuckInfoTool;
    @Autowired
    private HumanPositionTool humanPositionTool;
    @Autowired
    private AreaPassableTool areaPassableTool;
    @Autowired
    private PathUtilTool pathUtilTool;

    /** 前一个周期的 target */
    private Area prevTarget;
    private boolean isDetouredLastStep;
    private final Params params = new Params(getClass().getSimpleName());
    private static final GeometryFactory GF = new GeometryFactory();

    @Override
    public void calc(CalcContext context) {
        logger.info("------------------This is the" + agentInfo.getTime() + "time FbAt Escape------------------");
        if(isDetouredLastStep){
            isDetouredLastStep = false;
            return;
        }

        // 获得我被卡住的情况，如果移动时被卡住，则尝试执行绕路策略
        StuckType myStuckType = stuckInfoTool.getStuckType(agentInfo.getID());
        if (myStuckType != StuckType.STUCK_WHEN_MOVING)
            return;

        // 要求前一个周期执行的动作是 move
        Action prevAction = agentInfo.getExecutedAction(-1);
        if (!(prevAction instanceof ActionMove)) { // IMPOSSIBLE BRANCH
            logger.error("My prevAction=" + prevAction + " is not moving action, but myStuckType=" + myStuckType);
            return;
        }

        // 得到缩水的 cfreePolygon
        logger.info("I am stuck when moving, try detouring to get out...");
        EntityID myPosAreaId = humanPositionTool.getMyPositionAreaId();
        Geometry cfreePolygon = areaPassableTool.getCfreePolygonOf(myPosAreaId);
        // 如果得到的可通行多边形为空则证明他一定被卡住了此时无法尝试移动
        if (cfreePolygon == null || cfreePolygon.isEmpty()) {
            logger.error("No cfreePolygon found.");
            return;
        }
        // 该种情况获得的 cfreePolyon 也认为一定被卡住了无法逃脱
        Geometry shunkCfreePolygon = cfreePolygon.buffer(-OptionsTool.AGENT_RADIUS);
        if (cfreePolygon.isEmpty() || cfreePolygon instanceof Point) {
            logger.error("CfreePolygon is too small.");
            return;
        }

        // 逃脱障碍策略
        Area myPosArea = (Area) worldInfo.getEntity(myPosAreaId);
        logger.info("try to get a escapeAction to get rid");
        ActionMove escapeAction = getEscapeAction(myPosAreaId);
        if(escapeAction != null){
            context.setResultAction(escapeAction);
            context.setResultTarget(prevTarget);
            if(escapeAction.getPath().size() > 1){
                logger.info("move to Area：" + escapeAction.getPath().get(1) + " to get rid");
            }

            return ;
        } else {
            // 没有得到escapeAction
            logger.info("getEscapeAction failed");
        }

        // 拒绝采样，随机得到一个 cfreePolygon 中的点
        // 设计一个算法获得escape point
        Envelope envelope = cfreePolygon.getEnvelopeInternal();
        double minX = envelope.getMinX();
        double maxX = envelope.getMaxX();
        double minY = envelope.getMinY();
        double maxY = envelope.getMaxY();
        int maxTryTime = params.getInt("maxTryTime");
        Collection<Blockade> blockades = worldInfo.getBlockades(myPosArea);
        List<List<Line2D>> linesOfEachBlockade = blockades.stream().map(b -> {
            int[] apexes = b.getApexes();
            List<Point2D> point2DS = HFUTGeometryTool.vertexArrayToPoints(apexes);
            List<Line2D> line2DS = HFUTGeometryTool.pointsToLines(point2DS);
            return line2DS;
        }).toList();
        while (--maxTryTime >= 0) { // 最大采样次数
            double randX = ThreadLocalRandom.current().nextDouble(minX, maxX);
            double randY = ThreadLocalRandom.current().nextDouble(minY, maxY);
            CoordinateXY escapePointCoord = new CoordinateXY(randX, randY);
            logger.info("try to find a escape point");
            // 找到了一个位于 cfreePolygon 中的点
            // 判断这个我与这个点的连线是否跨越障碍物
            Line2D lineMeEscapePoint = new Line2D(agentInfo.getX(), agentInfo.getY(), randX, randY);
            boolean isIntersectionWithBlockade = false;
            for(List<Line2D> linesOfBlockade : linesOfEachBlockade){
                List<Point2D> intersections = HFUTGeometryTool.lineSegmentPolygonIntersection(linesOfBlockade, lineMeEscapePoint);
                if (!intersections.isEmpty()){
                    isIntersectionWithBlockade = true;
                    break;
                }
            }
            if (shunkCfreePolygon.contains(GF.createPoint(escapePointCoord)) && !isIntersectionWithBlockade) {
                ActionMove am = new ActionMove(List.of(myPosAreaId), (int) randX, (int) randY);
                logger.info("Found escape point " + escapePointCoord + ", try " + am);
                context.setResultAction(am);
                isDetouredLastStep = true;
                return;
            }
        }
    }

    @Override
    public void afterCalc(CalcContext context) {
        prevTarget = context.getTarget();
        logger.info("---------------------Time" + agentInfo.getTime() + " FbAt Escape end--------------------\n");
    }

    private ActionMove getEscapeAction(EntityID myPosArea){
        Area area = (Area) worldInfo.getEntity(myPosArea);
        List<EntityID> neighboursId = area.getNeighbours();
        Collection<Blockade> blockades = worldInfo.getBlockades(myPosArea);
        neighboursId.forEach(neighbour -> {
            blockades.addAll(worldInfo.getBlockades(neighbour));
        });
        Point2D myPos = new Point2D(agentInfo.getX(), agentInfo.getY());

        List<EntityID> areas = new ArrayList<>();
        areas.add(myPosArea);
        areas.addAll(neighboursId);
        Geometry[] blockadesGeometry = areas.stream()
                .map(areaPassableTool::getBlockadePolygonOf)
                .filter(Objects::nonNull)
                .toArray(Geometry[]::new);
        GeometryCollection gc = GF.createGeometryCollection(blockadesGeometry);
        Geometry joinedBlockades = gc.union();

        Line2D closestLine = null;
        Blockade nearestBlockade = null;
        double minDistance = Double.MAX_VALUE;

        // 找到距离我最近的障碍物
        for (Blockade blockade : blockades) {
            int[] blockadeApexes = blockade.getApexes();
            List<Point2D> point2DS = HFUTGeometryTool.vertexArrayToPoints(blockadeApexes);
            for (Point2D point2D2 : point2DS) {
                double distance = HFUTGeometryTool.getDistance(myPos, point2D2);
                if(distance < minDistance){
                    minDistance = distance;
                    nearestBlockade = blockade;
                }
            }
        }
        // 获取障碍物的边，并且求得一个距离我最近的障碍物的边
        if(nearestBlockade != null){
            int[] apexes = nearestBlockade.getApexes();
            closestLine = HFUTGeometryTool.getClosetLineOnPolygon(myPos, apexes);
        }

        if(closestLine != null){
            // 获取与最近的障碍物边直线相交的一个附近的Area
            Geometry cfreePolygonOf = areaPassableTool.getCfreePolygonOf(myPosArea);
            if(area != null){
                // 记录最后一次遍历时障碍物边与附近area距离我最近的一条边的交点
                for(EntityID neighborId : neighboursId){
                    Area neighbor = (Area) worldInfo.getEntity(neighborId);
                    int[] apexes = neighbor.getApexList();
                    // 获取当前Area距离我最近的一条边
                    Line2D closestEdgeOfAreaToMe = HFUTGeometryTool.getClosetLineOnPolygon(myPos, apexes);
                    // 获取两条直线的交点
                    Point2D intersection = HFUTGeometryTool.lineIntersection(closestLine, closestEdgeOfAreaToMe);
                    if (intersection != null){

                        //如果障碍物边closestLine在closestEdgeOfAreaToMe上
                        if(HFUTGeometryTool.contains(closestLine, intersection) && HFUTGeometryTool.contains(closestEdgeOfAreaToMe, intersection)){
                            Point2D origin = closestEdgeOfAreaToMe.getOrigin();
                            Point2D endPoint = closestEdgeOfAreaToMe.getEndPoint();
                            double distance1 = HFUTGeometryTool.pointToLineSegmentDistance(origin, closestLine);
                            double distance2 = HFUTGeometryTool.pointToLineSegmentDistance(endPoint, closestLine);
                            if(distance1 >= distance2){
                                closestEdgeOfAreaToMe = new Line2D(origin, HFUTGeometryTool.getMidPoint(closestEdgeOfAreaToMe));
                            } else {
                                closestEdgeOfAreaToMe = new Line2D(endPoint, HFUTGeometryTool.getMidPoint(closestEdgeOfAreaToMe));
                            }
                            logger.info("closestLine is in closestEdgeOfAreaToMe");
                            intersection = HFUTGeometryTool.getMidPoint(closestEdgeOfAreaToMe);
                        }

                        // 如果这一个交点在Area的这条边上，则选择这个Area为逃脱的目标，并且使用交点作为逃脱的点
                        if(intersection != null && HFUTGeometryTool.contains(closestEdgeOfAreaToMe, intersection)){
                            ArrayList<EntityID> path = new ArrayList<>();
                            path.add(myPosArea);
                            path.add(neighborId);
                            logger.info("move to the edge of neighborArea，intersection：" + intersection);
                            return new ActionMove(path, (int) intersection.getX(), (int) intersection.getY());
                        }
                    }
                }
                // 如果没有得到一个动作，则使用我与障碍物边的垂线再次尝试

                ////////////////////////////////////////////////////////
                logger.info("start my tactics ······");
                List<Edge> edges = area.getEdges();
                Line2D meToAreaCentreLine = new Line2D(new Point2D(area.getX(), area.getY()),
                        HFUTGeometryTool.getMidPoint(new Edge(closestLine.getOrigin(), closestLine.getEndPoint())));
                for (Edge edge : edges) {
                    Point2D edgeMidPoint = HFUTGeometryTool.getMidPoint(edge);
                    Edge oppositeEdge = HFUTGeometryTool.getOppositeEdge(area, edge, true, false);
                    if(oppositeEdge == null){
                        continue;
                    }
                    Line2D line = new Line2D(edgeMidPoint, HFUTGeometryTool.getMidPoint(oppositeEdge));
                    Point2D pointRid = HFUTGeometryTool.lineIntersection(meToAreaCentreLine, line);
                    // 如果其交点在当前Area的CfreePolygon中，则以该交点为逃脱点
                    if (cfreePolygonOf.contains(GF.createPoint(new Coordinate(pointRid.getX(), pointRid.getY())))) {
                        // 如果距离该逃脱点太近，则放弃此逃脱点
                        if(HFUTGeometryTool.getDistance(myPos, pointRid) < 50){
                            logger.info("too close to the pointRid: " + pointRid);
                        } else {
                            logger.info("move to area central：" + pointRid);
                            return new ActionMove(List.of(myPosArea), (int) pointRid.getX(), (int) pointRid.getY());
                        }
                    }

                    // 到当前area的edge中点
                    LineString meToEdge = GF.createLineString(new Coordinate[]{
                            new CoordinateXY(agentInfo.getX(), agentInfo.getY()),
                            HFUTGeometryTool.getMidPointCoordinate(edge)
                    });
                    if(!joinedBlockades.intersects(meToEdge)){
                        // 如果距离该逃脱点太近，则放弃此逃脱点
                        if(HFUTGeometryTool.getDistance(myPos, edgeMidPoint) < 50){
                            logger.info("too close to the edgeMidPoint: " + edgeMidPoint);
                        } else {
                            logger.info("move to edge of current area, edgeMidPoint: " + edgeMidPoint);
                            return new ActionMove(List.of(myPosArea), (int) edgeMidPoint.getX(), (int) edgeMidPoint.getY());
                        }
                    }
                }
            }

        }

        return null;
    }
}