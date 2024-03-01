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
import hfutinvincible.anno.HfutTool;
import hfutinvincible.tool.*;
import hfutinvincible.tool.edge.AreaPassableTool;
import hfutinvincible.tool.human.HumanPositionTool;
import hfutinvincible.tool.misc.OptionsTool;
import org.apache.log4j.Logger;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateXY;
import org.locationtech.jts.geom.GeometryFactory;
import rescuecore2.misc.geometry.Vector2D;
import rescuecore2.standard.entities.Area;
import rescuecore2.standard.entities.Blockade;
import rescuecore2.standard.entities.PoliceForce;
import rescuecore2.worldmodel.EntityID;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

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
    private  Params params;

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
                return;
            }

            for (Blockade blockade : blockades) {
                Shape blockadeShape = blockade.getShape();
                if (blockadeShape.contains(myX, myY)) {
                    logger.info("Totally stuck in blockade. Clear using old function.");
                    context.setResultAction(new ActionClear(blockade));
                    context.setResultTarget(null);
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
                if(blockades.isEmpty()){
                    ArrayList<EntityID> path = new ArrayList<>();
                    path.add(myPosArea.getID());
                    path.add(areaId);
                    context.setResultAction(new ActionMove(path, area.getX(), area.getY()));
                    context.setResultTarget(prevTarget);
                    return;
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
                context.setResultAction(new ActionClear((int) cleanVector.getX(), (int) cleanVector.getY(), closestBlockade));
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
