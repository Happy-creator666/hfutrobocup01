//package hfutinvincible.tool.executor;
//
//import adf.core.agent.action.Action;
//import adf.core.agent.action.common.ActionMove;
//import adf.core.agent.develop.DevelopData;
//import adf.core.agent.info.AgentInfo;
//import adf.core.agent.info.ScenarioInfo;
//import adf.core.agent.info.WorldInfo;
//import adf.core.agent.module.ModuleManager;
//import hfutinvincible.anno.AgentType;
//import hfutinvincible.anno.Autowired;
//import hfutinvincible.anno.Disabled;
//import hfutinvincible.anno.HfutTool;
//import hfutinvincible.tool.CalcContext;
//import hfutinvincible.tool.LifeCycle;
//import hfutinvincible.tool.Params;
//import hfutinvincible.tool.edge.AreaPassableTool;
//import hfutinvincible.tool.edge.HFUTEdge;
//import hfutinvincible.tool.human.HumanPositionTool;
//import hfutinvincible.tool.human.StuckInfoTool;
//import hfutinvincible.tool.human.StuckType;
//import hfutinvincible.tool.misc.HFUTGeometryTool;
//import hfutinvincible.tool.misc.OptionsTool;
//import hfutinvincible.tool.path.InPartitionSearchPathTool;
//import hfutinvincible.tool.path.SearchPathTool;
//import org.apache.log4j.Logger;
//import org.locationtech.jts.geom.*;
//import rescuecore2.misc.geometry.GeometryTools2D;
//import rescuecore2.misc.geometry.Line2D;
//import rescuecore2.misc.geometry.Point2D;
//import rescuecore2.misc.geometry.Vector2D;
//import rescuecore2.standard.entities.AmbulanceTeam;
//import rescuecore2.standard.entities.Area;
//import rescuecore2.standard.entities.Blockade;
//import rescuecore2.standard.entities.FireBrigade;
//import rescuecore2.worldmodel.EntityID;
//
//import java.util.*;
//import java.util.concurrent.ThreadLocalRandom;
//
//@HfutTool
//@Disabled
//@AgentType({FireBrigade.class, AmbulanceTeam.class})
//public class FbAtStuckEscapeTool implements LifeCycle {
//    private static final GeometryFactory GF = new GeometryFactory();
//    @Autowired
//    Logger logger;
//    @Autowired
//    AgentInfo agentInfo;
//    @Autowired
//    WorldInfo worldInfo;
//    @Autowired
//    ScenarioInfo scenarioInfo;
//    @Autowired
//    ModuleManager moduleManager;
//    @Autowired
//    DevelopData developData;
//    //Tools
//    @Autowired
//    StuckInfoTool stuckInfoTool;
//    @Autowired
//    InPartitionSearchPathTool inPartitionSearchPathTool;
//    @Autowired
//    HumanPositionTool humanPositionTool;
//    @Autowired
//    AreaPassableTool areaPassableTool;
//    @Autowired
//    SearchPathTool searchPathTool;
//
//    private boolean isDetouredLastStep;
//    private Area prevTarget;
//    private final Params params = new Params(getClass().getSimpleName());
//
//    @Override
//    public void calc(CalcContext context) {
//        if(isDetouredLastStep){
//            isDetouredLastStep = false;
//            return;
//        }
//
//        // 获得我被卡住的情况，如果移动时被卡住，则尝试执行绕路策略
//        StuckType myStuckType = stuckInfoTool.getStuckType(agentInfo.getID());
//        if (myStuckType != StuckType.STUCK_WHEN_MOVING)
//            return;
//
//        // 要求前一个周期执行的动作是 move
//        Action prevAction = agentInfo.getExecutedAction(-1);
//        if (!(prevAction instanceof ActionMove)) { // IMPOSSIBLE BRANCH
//            logger.error("My prevAction=" + prevAction + " is not moving action, but myStuckType=" + myStuckType);
//            return;
//        }
//
//        // 得到缩水的 cfreePolygon
//        logger.info("I am stuck when moving, try detouring to get out...");
//        EntityID myPosAreaId = humanPositionTool.getMyPositionAreaId();
//        Geometry cfreePolygon = areaPassableTool.getCfreePolygonOf(myPosAreaId);
//        // 如果得到的可通行多边形为空则证明他一定被卡住了此时无法尝试移动
//        if (cfreePolygon == null || cfreePolygon.isEmpty()) {
//            logger.error("No cfreePolygon found.");
//            return;
//        }
//        // 该种情况获得的 cfreePolyon 也认为一定被卡住了无法逃脱
//        Geometry shunkCfreePolygon = cfreePolygon.buffer(-OptionsTool.AGENT_RADIUS);
//        if (cfreePolygon.isEmpty() || cfreePolygon instanceof Point) {
//            logger.error("CfreePolygon is too small.");
//            return;
//        }
//
//        Area myPosArea = (Area) worldInfo.getEntity(myPosAreaId);
////        if(myPosArea != null){
////            List<EntityID> neighbours = myPosArea.getNeighbours();
////            logger.info("try to move to the neighbor area to get rid");
////            for(EntityID areaId : neighbours){
////                Collection<Blockade> blockades = worldInfo.getBlockades(areaId);
////                Area area = (Area) worldInfo.getEntity(areaId);
////                if(blockades.isEmpty()){
////                    ArrayList<EntityID> path = new ArrayList<>();
////                    path.add(myPosAreaId);
////                    path.add(areaId);
////                    context.setResultAction(new ActionMove(path, area.getX(), area.getY()));
////                    context.setResultTarget(prevTarget);
////                    return ;
////                }
////            }
//////                logger.info("try to move to the center of my area to get rid");
//////                ArrayList<EntityID> path = new ArrayList<>();
//////                path.add(myPosArea.getID());
//////                resultCollector.setResultAction(new ActionMove(path, myPosArea.getX(), myPosArea.getY()));
//////                resultCollector.setResultTarget(prevTarget);
//////                return;
////        }
//        /**上面的代码是学长写  以下是我的简单策略 此时确实被卡住了尝试逃离调用getEscapeAction*/
//
//
//
//
//
//        ActionMove escapeAction = getEscapeAction(myPosAreaId);
//        if(escapeAction != null){
//            context.setResultAction(escapeAction);
//            context.setResultTarget(prevTarget);
//            return ;
//        }
////        // 拒绝采样，随机得到一个 cfreePolygon 中的点
////        // 设计一个算法获得escape point
////        Envelope envelope = cfreePolygon.getEnvelopeInternal();
////        double minX = envelope.getMinX();
////        double maxX = envelope.getMaxX();
////        double minY = envelope.getMinY();
////        double maxY = envelope.getMaxY();
////        int maxTryTime = params.getInt("maxTryTime");
////        Collection<Blockade> blockades = worldInfo.getBlockades(myPosArea);
////        List<List<Line2D>> linesOfEachBlockade = blockades.stream().map(b -> {
////            int[] apexes = b.getApexes();
////            List<Point2D> point2DS = HFUTGeometryTool.vertexArrayToPoints(apexes);
////            List<Line2D> line2DS = HFUTGeometryTool.pointsToLines(point2DS);
////            return line2DS;
////        }).toList();
////        while (--maxTryTime >= 0) { // 最大采样次数
////            double randX = ThreadLocalRandom.current().nextDouble(minX, maxX);
////            double randY = ThreadLocalRandom.current().nextDouble(minY, maxY);
////            CoordinateXY escapePointCoord = new CoordinateXY(randX, randY);
////            logger.info("try to find a escape point");
////            // 找到了一个位于 cfreePolygon 中的点
////            // 判断这个我与这个点的连线是否跨越障碍物
////            Line2D lineMeEscapePoint = new Line2D(agentInfo.getX(), agentInfo.getY(), randX, randY);
////            boolean isIntersectionWithBlockade = false;
////            for(List<Line2D> linesOfBlockade : linesOfEachBlockade){
////                List<Point2D> intersections = HFUTGeometryTool.lineSegmentPolygonIntersection(linesOfBlockade, lineMeEscapePoint);
////                if (!intersections.isEmpty()){
////                    isIntersectionWithBlockade = true;
////                    break;
////                }
////            }
////            if (shunkCfreePolygon.contains(GF.createPoint(escapePointCoord)) && !isIntersectionWithBlockade) {
////                ActionMove am = new ActionMove(List.of(myPosAreaId), (int) randX, (int) randY);
////                logger.info("Found escape point " + escapePointCoord + ", try " + am);
////                context.setResultAction(am);
////                isDetouredLastStep = true;
////                return;
////            }
////    }
//    }
//    @Override
//    public void afterCalc(CalcContext context) {
//        prevTarget = context.getTarget();
//    }
//
//
////    private ActionMove getEscapeAction(EntityID myPosArea){
////        Collection<Blockade> blockades = worldInfo.getBlockades(myPosArea);
////        Geometry cfreePolygon = areaPassableTool.getCfreePolygonOf(myPosArea);
////        Line2D closestLine = null;
////        Point2D myPos = new Point2D(agentInfo.getX(), agentInfo.getY());
////        // 获取障碍物的边，并且求得一个距离我最近的障碍物的边
////        for (Blockade blockade : blockades) {
////            int[] apexes = blockade.getApexes();
////            closestLine = HFUTGeometryTool.getClosetLineOnPolygon(myPos, apexes);
////        }
////
////        if(closestLine != null){
////            // 获取与最近的障碍物边直线相交的一个附近的Area
////            Area area = (Area) worldInfo.getEntity(myPosArea);
////            if(area != null){
////                List<EntityID> neighboursId = area.getNeighbours();
////                for(EntityID neighborId : neighboursId){
////                    Area neighbor = (Area) worldInfo.getEntity(neighborId);
////                    int[] apexes = neighbor.getApexList();
////                    // 获取当前Area距离我最近的一条边
////                    Line2D closestEdgeOfAreaToMe = HFUTGeometryTool.getClosetLineOnPolygon(myPos, apexes);
////                    // 获取两条直线的交点
////                    Point2D intersection = HFUTGeometryTool.lineIntersection(closestLine, closestEdgeOfAreaToMe);
////                    // 如果这一个交点在Area的这条边上，则选择这个Area为逃脱的目标，并且使用交点作为逃脱的点
////                    if(intersection != null && HFUTGeometryTool.contains(closestEdgeOfAreaToMe, intersection)){
////                        ArrayList<EntityID> path = new ArrayList<>();
////                        path.add(myPosArea);
////                        path.add(neighborId);
////                        return new ActionMove(path, (int) intersection.getX(), (int) intersection.getY());
////                    }
////                }
////                // 如果没有得到一个动作，则使用我与障碍物边的垂线再次尝试
////
////            }
////
////        }
////        return null;
////    }
//    /** 考虑利用迭代缩减法将可通行多边形进行缩减 对于凹多边形考虑将凹陷部分切除 对于剩余部分的多边形采用迭代缩减法*/
//    private ActionMove getEscapeAction(EntityID myPosArea){
//        Geometry cfreePolygon = areaPassableTool.getCfreePolygonOf(myPosArea);
//
//        if(cfreePolygon == null)
//        {
//            logger.error("No cfreePolygon to escape");
//            return null;
//        }
//        // 得出多边形的所有顶点以及边
//        List<Coordinate> vertexes = new ArrayList<>();
//        for (int i = 0; i < cfreePolygon.getNumPoints(); i++) {
//            vertexes.add(cfreePolygon.getCoordinates()[i]);
//        }
//
//            List<Coordinate> new_vertexs = ShinkcfreePolygon(vertexes,20.0);
//            //按照一定比例进行缩放操作使其范围不段减小直至面积足够小，在这个小多边形内部找到一个点设置智能体向该点进行移动
//            double size = 0;
//            for (int i = 0; i + 1 <new_vertexs.size() ; i++) {
//                size += (new_vertexs.get(i).getX() * new_vertexs.get(i + 1).getY()) - (new_vertexs.get(i + 1).getX() * new_vertexs.get(i).getY());
//            }
//
//            while(size >= 100)
//            {
//                new_vertexs =ShinkcfreePolygon(new_vertexs,20.0);
//                size = 0;
//                for (int i = 0; i + 1 <new_vertexs.size() ; i++) {
//                    size += (new_vertexs.get(i).getX() * new_vertexs.get(i + 1).getY()) - (new_vertexs.get(i + 1).getX() * new_vertexs.get(i).getY());
//                }
//            }
//            OptionalDouble MaxX = new_vertexs.stream()
//                    .mapToDouble(Coordinate::getX)
//                    .max();
//            OptionalDouble MinX = new_vertexs.stream()
//                    .mapToDouble(Coordinate::getX)
//                    .min();
//            OptionalDouble MaxY = new_vertexs.stream()
//                    .mapToDouble(Coordinate::getY)
//                    .max();
//            OptionalDouble MinY = new_vertexs.stream()
//                    .mapToDouble(Coordinate::getY)
//                    .min();
//
//        if (MaxX.isPresent() && MinX.isPresent() && MaxY.isPresent() && MinY.isPresent()) {
//            Random random = new Random();
//            double rX = MinX.getAsDouble() + (MaxX.getAsDouble() - MinX.getAsDouble()) * (0.01 + (0.99 - 0.01) * random.nextDouble());
//            double rY = MinY.getAsDouble() + (MaxY.getAsDouble() - MinY.getAsDouble()) * (0.01 + (0.99 - 0.01) * random.nextDouble());
//            Coordinate rPoint = new Coordinate(rX, rY);
//            List<EntityID> path = new ArrayList<>();
//            path.add(myPosArea);
//            return new ActionMove(path,(int)rPoint.getX(),(int) rPoint.getY());
//        }
//        else {
//            logger.error("No Action can be done to escape");
//            return null;
//        }
//    }
//
//    /**
//     * 返回一个按照一定距离缩小的Geometry
//     * @param cfreePolygonCoordinates :多边形顶点集
//     * @param shrinkdis :缩小长度(>0)
//     * @return 缩放后的多边形顶点集
//     * */
//    public List<Coordinate> ShinkcfreePolygon(List<Coordinate> cfreePolygonCoordinates,double shrinkdis){
//        List<Coordinate> new_coordonates = new ArrayList<>();
//        // 假设我获得的是按照逆时针或者顺时针排列的坐标集合
//        int num = cfreePolygonCoordinates.size();
//        for (int i = 0;i <num;i++) {
//            // 分别获得两个方向的向量
//            double x1 = cfreePolygonCoordinates.get(i % num).getX() - cfreePolygonCoordinates.get((i-1) % num).getX();
//            double y1 = cfreePolygonCoordinates.get(i % num).getY() - cfreePolygonCoordinates.get((i-1) % num).getY();
//            double x2 = cfreePolygonCoordinates.get((i+1) % num).getX() - cfreePolygonCoordinates.get(i % num).getX();
//            double y2 = cfreePolygonCoordinates.get((i+1) % num).getY() - cfreePolygonCoordinates.get(i % num).getY();
//
//            Coordinate old_coordinate = cfreePolygonCoordinates.get(i);
//
//            Vector2D vector1 = new Vector2D(x1,y1).normalised();
//            Vector2D vector2 = new Vector2D(x2,y2).normalised();
//
//            Vector2D newvector = vector1.add(vector2).scale(shrinkdis);  //原顶点 -> 新顶点
//            // 向量叉乘
//            double Vec_cross = (x1 * y2) - (x2 * y1);
//            Coordinate new_coordinate;
//            //判断凹凸角
//            if(Vec_cross <0) //凸角
//            {
//                 new_coordinate = new Coordinate(old_coordinate.getX() + newvector.getX(),
//                        old_coordinate.getY() + newvector.getY());
//            }
//            //凹角
//            else {
//                newvector = newvector.getNormal().getNormal();
//                new_coordinate = new Coordinate(old_coordinate.getX() + newvector.getX(),
//                        old_coordinate.getY() + newvector.getY());
//            }
//            new_coordonates.add(new_coordinate);
//        }
//        return new_coordonates;
//    }
//}
