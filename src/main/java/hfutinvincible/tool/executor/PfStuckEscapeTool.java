package hfutinvincible.tool.executor;

import adf.core.agent.action.Action;
import adf.core.agent.action.common.ActionMove;
import adf.core.agent.action.police.ActionClear;
import adf.core.agent.develop.DevelopData;
import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.ScenarioInfo;
import adf.core.agent.info.WorldInfo;
import adf.core.agent.module.ModuleManager;
import hfutinvincible.anno.AgentType;
import hfutinvincible.anno.Autowired;
import hfutinvincible.anno.Disabled;
import hfutinvincible.anno.HfutTool;
import hfutinvincible.tool.*;
import hfutinvincible.tool.edge.AreaPassableTool;
import hfutinvincible.tool.human.HumanPositionTool;
import hfutinvincible.tool.misc.OptionsTool;
import org.apache.log4j.Logger;
import org.locationtech.jts.geom.*;
import rescuecore2.misc.geometry.Line2D;
import rescuecore2.misc.geometry.Vector2D;
import rescuecore2.standard.entities.Area;
import rescuecore2.standard.entities.Blockade;
import rescuecore2.standard.entities.PoliceForce;
import rescuecore2.standard.entities.Road;
import rescuecore2.worldmodel.EntityID;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@HfutTool
@AgentType({PoliceForce.class})
public class PfStuckEscapeTool implements LifeCycle {
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

    private static final GeometryFactory GF = new GeometryFactory();

    /** 警察只有最开始可能完全被卡在障碍里，此变量用于防止警察错误被计算为卡在障碍里 */
    private boolean canBeTotallyStuckInBlockade = true;

    /** 前一个周期的坐标 */
    private Coordinate prevCoord;
    /** 前一个周期的 target */
    private Area prevTarget;
    private int timeMeStuck;
    private Params params;

    //Tools
    @Autowired
    UnavailableTargetTool unavailableTargetTool;
    @Autowired
    HumanPositionTool humanPositionTool;
    @Autowired
    AreaPassableTool areaPassableTool;

    public PfStuckEscapeTool(){
        this.timeMeStuck=0;
        this.params = new Params(getClass().getSimpleName());
    }

    @Override
    public void calc(CalcContext context) {
        Area myPosArea = humanPositionTool.getMyPositionArea();
        double myX = agentInfo.getX();
        double myY = agentInfo.getY();

        // 如果发现有某个 blockade 完全把我卡在里面，则使用接触清理把这个 blockade 移除
        if (canBeTotallyStuckInBlockade) {
            // 获得我所在 area 上的所有 blockades
            Collection<Blockade> blockades = worldInfo.getBlockades(myPosArea);
            if (blockades.isEmpty()) {
                logger.info("No blockades nearby. Do nothing.");
                unavailableTargetTool.setUnavailableAndWait(
                        myPosArea.getID(),
                        UnavailableType.pfNoAction,
                        params.getInt("waitAll"),
                        true);
                canBeTotallyStuckInBlockade = false;
                return;
            }

            for (Blockade blockade : blockades) {
                Shape blockadeShape = blockade.getShape();
                if (blockadeShape.contains(myX, myY)) {
                    logger.info("Totally stuck in blockade. Clear using old function.");
                    context.setResultAction(new ActionClear(blockade));
                    context.setResultTarget(prevTarget);
                    return;
                }
            }
        }

        // 没有完全被卡在障碍里，则以后也不可能完全被卡在障碍里
        canBeTotallyStuckInBlockade = false;

        Coordinate currCoord = new CoordinateXY(agentInfo.getX(), agentInfo.getY());
        Action prevAction = agentInfo.getExecutedAction(-1);
        if (prevAction instanceof ActionMove am) {
            if (prevCoord.distance(currCoord) < params.getDouble("stuckThreshold")) {
                // TODO 是否有更好的方法
                if (prevTarget != null) { // 如果之前有指定目标，则换个目标
                    int unavailableTime = params.getInt("unavailableTime");
                    unavailableTargetTool.setUnavailableAndWait(prevTarget.getID(), UnavailableType.block, unavailableTime, true);
                    logger.info("I am stuck! set prevTarget as unavailable for " + unavailableTime);
                    return;
                } // TODO else???
            }
        }

        // 解决一些PF发出了Move，但是Server不处理的问题
        if (Math.abs(prevCoord.getX() - myX) < params.getDouble("stuckThreshold") &&
                Math.abs(prevCoord.getY() - myY) < params.getDouble("stuckThreshold")) {
            timeMeStuck += 1;
            logger.debug("I may stuck and now timeMeStuck is " + timeMeStuck);
        }
        else timeMeStuck = 0;

//            if (timeMeStuck != 0 && timeMeStuck % 6 == 0){
//                logger.info("I was stuck for too long, change another target to do");
//                if(prevTarget != null){
//                    int unavailableTime = params.getInt("unavailableTime");
//                    unavailableTargetTool.setUnavailableAndWait(prevTarget.getID(), UnavailableType.block, unavailableTime, true);
//                } else if(prevAction instanceof ActionClear actionClear){
//                    EntityID prevClearTarget = actionClear.getTarget();
//                    if(prevClearTarget != null){
//                        int unavailableTime = params.getInt("unavailableTime");
//                        unavailableTargetTool.setUnavailableAndWait(prevTarget.getID(), UnavailableType.block, unavailableTime, true);
//                    }
//                }
//                return;
//            }

        if (timeMeStuck != 0 && timeMeStuck % 5 == 0){
            List<EntityID> neighbours = myPosArea.getNeighbours();
            logger.info("try to move to the neighbor area to get rid");
            for(EntityID areaId : neighbours){
                Collection<Blockade> blockades = worldInfo.getBlockades(areaId);
                Area area = (Area) worldInfo.getEntity(areaId);
                if(area instanceof Road && blockades.isEmpty()){
                    logger.debug("move to a neighbor area : " + area);
                    ArrayList<EntityID> path = new ArrayList<>();
                    path.add(myPosArea.getID());
                    path.add(areaId);
                    context.setResultAction(new ActionMove(path, area.getX(), area.getY()));
                    context.setResultTarget(prevTarget);
                    return;
                }
            }

            // 移动到目标路径的下一个area的cfreepolygon中
            if (prevAction instanceof ActionMove prevActionMove){
                List<EntityID> subPath = prevActionMove.getPath().subList(0, 2);
                logger.debug("current path size: " + subPath.size());
                // get target point
                Geometry cfreePolygon = areaPassableTool.getCfreePolygonOf(subPath.get(1));
                if (cfreePolygon != null) {
                    logger.debug("get a cfreepolygon from next area");
                    Geometry shunkCfreePolygon = cfreePolygon.buffer(-OptionsTool.AGENT_RADIUS);
                    if (shunkCfreePolygon != null){
                        Envelope envelope = cfreePolygon.getEnvelopeInternal();
                        double minX = envelope.getMinX();
                        double maxX = envelope.getMaxX();
                        double minY = envelope.getMinY();
                        double maxY = envelope.getMaxY();
                        int maxTryTime = params.getInt("maxTryTime");
                        while (--maxTryTime >= 0) { // 最大采样次数
                            double randX = ThreadLocalRandom.current().nextDouble(minX, maxX);
                            double randY = ThreadLocalRandom.current().nextDouble(minY, maxY);
                            CoordinateXY escapePointCoord = new CoordinateXY(randX, randY);
                            logger.info("try to find a escape point");
                            // 找到了一个位于 cfreePolygon 中的点
                            // 判断这个我与这个点的连线是否跨越障碍物
                            Line2D lineMeEscapePoint = new Line2D(agentInfo.getX(), agentInfo.getY(), randX, randY);
//                                    boolean isIntersectionWithBlockade = false;
//                                    for(List<Line2D> linesOfBlockade : linesOfEachBlockade){
//                                        List<Point2D> intersections = HFUTGeometryTool.lineSegmentPolygonIntersection(linesOfBlockade, lineMeEscapePoint);
//                                        if (!intersections.isEmpty()){
//                                            isIntersectionWithBlockade = true;
//                                            break;
//                                        }
//                                    }
                            if (shunkCfreePolygon.contains(GF.createPoint(escapePointCoord))) {
                                ActionMove am = new ActionMove(subPath, (int) randX, (int) randY);
                                logger.info("Found escape point " + escapePointCoord + ", try " + am);
                                context.setResultAction(am);
                                return;
                            }
                        }

                    }
                }

            }


//                Geometry cfreePolygon = areaPassableTool.getCfreePolygonOf(myPosArea.getID());
//                if ()
//                logger.info("try to move to the center of my area to get rid");
//                ArrayList<EntityID> path = new ArrayList<>();
//                path.add(myPosArea.getID());
//                resultCollector.setResultAction(new ActionMove(path, myPosArea.getX(), myPosArea.getY()));
//                resultCollector.setResultTarget(prevTarget);
            return;
        }

        if (timeMeStuck != 0 && timeMeStuck % 3 == 0){
            logger.info("I was stuck more than 3 steps, try to execute clear to escape");
            Collection<Blockade> blockades = worldInfo.getBlockades(myPosArea);
            int closestX = Integer.MAX_VALUE;
            int closestY = Integer.MAX_VALUE;
            double closestDist = Double.MAX_VALUE;
            Blockade closestBlockade = null;
            for(Blockade b : blockades){
                int[] apexes = b.getApexes();
                for(int i = 0; i < apexes.length - 2; i += 2){
                    int x = apexes[i];
                    int y = apexes[i + 1];
                    double dist = Math.abs(y - myY) * Math.abs(y - myY) + Math.abs(x - myX) * Math.abs(x - myX);
                    if(dist < closestDist){
                        closestX = x;
                        closestY = y;
                        closestDist = dist;
                        closestBlockade = b;
                    }
                }
            }

            // todo 尝试使用更好的算法来获得一个较好的临时的清障算法
            // use checkline(edge to edge)
            if (closestBlockade != null){
                Vector2D cleanVector = new Vector2D((int) (closestX - myX), (int) (closestY - myY))
                        .normalised()
                        .scale(OptionsTool.REPAIR_DISTANCE);
                context.setResultAction(new ActionClear(agentInfo, cleanVector));
                context.setResultTarget(prevTarget);
                return;
            }
        }
    }

    @Override
    public void afterCalc(CalcContext context) {
        prevCoord = new CoordinateXY(agentInfo.getX(), agentInfo.getY());
        prevTarget = context.getTarget();
    }
}
