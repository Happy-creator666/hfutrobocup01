package hfutinvincible.tool.executor;

import adf.core.agent.action.Action;
import adf.core.agent.action.common.ActionMove;
import adf.core.agent.develop.DevelopData;
import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.ScenarioInfo;
import adf.core.agent.info.WorldInfo;
import adf.core.agent.module.ModuleManager;
import hfutinvincible.anno.AgentType;
import hfutinvincible.anno.Autowired;
import hfutinvincible.anno.HfutTool;
import hfutinvincible.tool.CalcContext;
import hfutinvincible.tool.LifeCycle;
import hfutinvincible.tool.Params;
import hfutinvincible.tool.edge.AreaPassableTool;
import hfutinvincible.tool.human.HumanPositionTool;
import hfutinvincible.tool.human.StuckInfoTool;
import hfutinvincible.tool.human.StuckType;
import hfutinvincible.tool.misc.HFUTGeometryTool;
import hfutinvincible.tool.misc.OptionsTool;
import hfutinvincible.tool.path.InPartitionSearchPathTool;
import org.apache.log4j.Logger;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.geom.Point;
import rescuecore2.misc.geometry.Line2D;
import rescuecore2.misc.geometry.Point2D;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@HfutTool
@AgentType({AmbulanceTeam.class, FireBrigade.class, PoliceForce.class})
public class FbAtStuckEscapeTool implements LifeCycle {
    @Autowired
    private AgentInfo agentInfo;
    @Autowired
    private WorldInfo worldInfo;
    @Autowired
    private Logger logger;

    private static final GeometryFactory GF = new GeometryFactory();

    // Tools
    @Autowired
    private StuckInfoTool stuckInfoTool;
    @Autowired
    private InPartitionSearchPathTool inPartitionSearchPathTool;
    @Autowired
    private HumanPositionTool humanPositionTool;
    @Autowired
    private AreaPassableTool areaPassableTool;

    private boolean isDetouredLastStep;
    private Area prevTarget;


    protected final Params params;

    public FbAtStuckEscapeTool() {

        this.params = new Params(getClass().getSimpleName());


        this.isDetouredLastStep = false;

    }

    @Override
    public void calc(CalcContext context) {
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
        if (cfreePolygon == null || cfreePolygon.isEmpty()) {
            logger.error("No cfreePolygon found.");
            return;
        }
        Geometry shunkCfreePolygon = cfreePolygon.buffer(-OptionsTool.AGENT_RADIUS);
        if (cfreePolygon.isEmpty() || cfreePolygon instanceof Point) {
            logger.error("CfreePolygon is too small.");
            return;
        }

        Area myPosArea = (Area) worldInfo.getEntity(myPosAreaId);
        if(myPosArea != null){
            List<EntityID> neighbours = myPosArea.getNeighbours();
            logger.info("try to move to the neighbor area to get rid");
            for(EntityID areaId : neighbours){
                Collection<Blockade> blockades = worldInfo.getBlockades(areaId);
                Area area = (Area) worldInfo.getEntity(areaId);
                if(blockades.isEmpty()){
                    ArrayList<EntityID> path = new ArrayList<>();
                    path.add(myPosAreaId);
                    path.add(areaId);
                    context.setResultAction(new ActionMove(path, area.getX(), area.getY()));
                    context.setResultTarget(prevTarget);
                    return ;
                }
            }
//                logger.info("try to move to the center of my area to get rid");
//                ArrayList<EntityID> path = new ArrayList<>();
//                path.add(myPosArea.getID());
//                resultCollector.setResultAction(new ActionMove(path, myPosArea.getX(), myPosArea.getY()));
//                resultCollector.setResultTarget(prevTarget);
//                return;
        }

        ActionMove escapeAction = getEscapeAction(myPosAreaId);
        if(escapeAction != null){
            context.setResultAction(escapeAction);
            context.setResultTarget(prevTarget);
            return ;
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
    }

    private ActionMove getEscapeAction(EntityID myPosArea){
        Collection<Blockade> blockades = worldInfo.getBlockades(myPosArea);
        Line2D closestLine = null;
        Point2D myPos = new Point2D(agentInfo.getX(), agentInfo.getY());
        // 获取障碍物的边，并且求得一个距离我最近的障碍物的边
        for (Blockade blockade : blockades) {
            int[] apexes = blockade.getApexes();
            closestLine = HFUTGeometryTool.getClosetLineOnPolygon(myPos, apexes);
        }

        if(closestLine != null){
            // 获取与最近的障碍物边直线相交的一个附近的Area
            Area area = (Area) worldInfo.getEntity(myPosArea);
            if(area != null){
                List<EntityID> neighboursId = area.getNeighbours();
                for(EntityID neighborId : neighboursId){
                    Area neighbor = (Area) worldInfo.getEntity(neighborId);
                    int[] apexes = neighbor.getApexList();
                    // 获取当前Area距离我最近的一条边
                    Line2D closestEdgeOfAreaToMe = HFUTGeometryTool.getClosetLineOnPolygon(myPos, apexes);
                    // 获取两条直线的交点
                    Point2D intersection = HFUTGeometryTool.lineIntersection(closestLine, closestEdgeOfAreaToMe);
                    // 如果这一个交点在Area的这条边上，则选择这个Area为逃脱的目标，并且使用交点作为逃脱的点
                    if(intersection != null && HFUTGeometryTool.contains(closestEdgeOfAreaToMe, intersection)){
                        ArrayList<EntityID> path = new ArrayList<>();
                        path.add(myPosArea);
                        path.add(neighborId);
                        return new ActionMove(path, (int) intersection.getX(), (int) intersection.getY());
                    }
                }
                // 如果没有得到一个动作，则使用我与障碍物边的垂线再次尝试

            }

        }
        return null;
    }
}