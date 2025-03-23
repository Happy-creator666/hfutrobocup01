package hfutinvincible.tool.executor;

import adf.core.agent.action.Action;
import adf.core.agent.action.ambulance.ActionRescue;
import adf.core.agent.action.common.ActionMove;
import adf.core.agent.action.common.ActionRest;
import adf.core.agent.develop.DevelopData;
import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.ScenarioInfo;
import adf.core.agent.info.WorldInfo;
import adf.core.agent.module.ModuleManager;
import adf.core.agent.precompute.PrecomputeData;
import hfutinvincible.Tuple;
import hfutinvincible.Tuple2;
import hfutinvincible.anno.After;
import hfutinvincible.anno.AgentType;
import hfutinvincible.anno.Autowired;
import hfutinvincible.anno.HfutTool;
import hfutinvincible.debug.DebugData;
import hfutinvincible.debug.Debugger;
import hfutinvincible.debug.Painter;
import hfutinvincible.debug.paintable.Paintable;
import hfutinvincible.tool.*;
import hfutinvincible.tool.comm.MessageManageTool;
import hfutinvincible.tool.edge.AreaPassableTool;
import hfutinvincible.tool.human.*;
import hfutinvincible.tool.mediator.FbMediator;
import hfutinvincible.tool.misc.HFUTGeometryTool;
import hfutinvincible.tool.misc.OptionsTool;
import hfutinvincible.tool.path.Path;
import hfutinvincible.tool.path.PathUtilTool;
import hfutinvincible.tool.path.SearchPathTool;
import hfutinvincible.tool.targetprovider.FbTargetProvider;
import org.apache.log4j.Logger;
import org.locationtech.jts.geom.*;
import rescuecore2.misc.geometry.Line2D;
import rescuecore2.misc.geometry.Point2D;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;

import javax.annotation.Nonnull;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

@HfutTool
@AgentType({FireBrigade.class})
public class FbExecutor implements LifeCycle {
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
    //Tools
    // 分散清理
    @Autowired
    FbTargetProvider targetProvider;

    @Autowired
    SearchPathTool searchPathTool;
    @Autowired
    AgentTargetAreaTool agentTargetAreaTool;
    @Autowired
    BuriednessTool buriednessTool;
    @Autowired
    StuckInfoTool stuckInfoTool;
    @Autowired
    TimeToDeadTool timeToDeadTool;
    @Autowired
    HumanPositionTool humanPositionTool;
    @Autowired
    MessageManageTool messageTool;
    @Autowired
    PathUtilTool pathUtilTool;
    @Autowired
    UnavailableTargetTool unavailableTargetTool;
    @Autowired
    FbMediator fbMediator;
//    @Autowired
//    FbAtStuckEscapeTool ;
    @Autowired
    AgentRestTool agentRestTool;
    @Autowired
    AreaPassableTool areaPassableTool;

    private static final GeometryFactory GF = new GeometryFactory();
    //Params
    private Params params = new Params(getClass().getSimpleName());

    @Override
    public void resume(PrecomputeData precomputeData) {
        // 参数更改时，重新计算目标排序与得分，并且更新调试器
        params.registerParamsChangedListener(() -> {
            DebugData debugData = Debugger.getDebugData(agentInfo);
            if (debugData == null) return;
            if (!debugData.calcTargetOrNot) return;
            logger.info("params modified, recalculate sortedTargetsAndScores");
            debugData.sortedTargetsAndScores = targetProvider.getSortedCandidatesAndScores();
            logger.debug("newSortedTargetsAndScores=" + debugData.sortedTargetsAndScores);
            Debugger.registerOrRefreshAgent(worldInfo, agentInfo, scenarioInfo);
        });
    }

    @After({"AgentRestTool","FbAtStuckEscapeTool3"})
    @Override
    public void calc(CalcContext context) {
        if (agentInfo.getTime() < scenarioInfo.getKernelAgentsIgnoreuntil()) {
            return;
        }
        final Area mePosArea = humanPositionTool.getMyPositionArea();
        final Set<EntityID> changedEntities = worldInfo.getChanged().getChangedEntities();

        final List<EntityID> rescuableBuriedHuman = new ArrayList<>();
        for(Human human : worldInfo.getBuriedHumans(mePosArea.getID())){
            EntityID humanId = human.getID();
            Integer timeToDead = timeToDeadTool.getTimeToDeadNowEstimate(humanId);
            if (timeToDead == null) { // IMPOSSIBLE BRANCH
                logger.error("IMPOSSIBLE! I should always know the timeToDead of human in the same area as me.");
                continue;
            }
            if (timeToDead < params.getDouble("ignoreTimeToDead")) {
                logger.info("human " + humanId + " is almost died. ignore.");
                continue;
            }
            Integer buriedness = buriednessTool.getBuriedness(humanId);
            if (buriedness == null) { // IMPOSSIBLE BRANCH
                logger.error("IMPOSSIBLE! I should always know the buriedness of human in the same area as me.");
                continue;
            }
            if (buriedness == 0) { // 没被埋
                continue;
            }
            logger.info("human " + humanId + " is rescuable");
            rescuableBuriedHuman.add(humanId);
        }

        if (!rescuableBuriedHuman.isEmpty()) {
            int otherFbsSelectHereCount = agentTargetAreaTool.getAgentIdsSelectedHere(mePosArea.getID())
                    .stream()
                    .filter(id -> !id.equals(agentInfo.getID())) // 除了我自己
                    .filter(id -> worldInfo.getEntity(id) instanceof FireBrigade)
                    .collect(Collectors.toSet()).size();
            Integer buriedness;
            // 循环，直到找到一个目标
            for(EntityID humanToRescue : rescuableBuriedHuman){
                buriedness = buriednessTool.getBuriedness(humanToRescue);
                if(buriedness<=20)
                {
                    if(otherFbsSelectHereCount <= buriedness){
                        Action resultAction = new ActionRescue(humanToRescue); // 则 Rescue
                        context.setResultAction(resultAction);
                        otherFbsSelectHereCount -= buriedness;
                        logger.info("Rescuing " +  humanToRescue + ", buriedness=" + buriednessTool.getBuriedness(humanToRescue));
                        return;
                    } else {
                        if(otherFbsSelectHereCount <= 0)
                            break;
                        // === 避免集体同时放弃 ===
                        double stayProbability = params.getDouble("stayProbability"); // 30% 的概率继续留在目标
                        if (Math.random() < stayProbability) {
                            Action resultAction = new ActionRescue(humanToRescue); // 则 Rescue
                            context.setResultAction(resultAction);
                            otherFbsSelectHereCount -= buriedness;
                            logger.info("Rescuing " +  humanToRescue + ", buriedness=" + buriednessTool.getBuriedness(humanToRescue));
                            return;
                        } else {
                            break;
                        }
                    }
                }
                else {
                    if(otherFbsSelectHereCount > 20) {
                        // === 避免集体同时放弃 ===
                        double stayProbability = params.getDouble("stayProbability"); // 30% 的概率继续留在目标
                        if (Math.random() < stayProbability) {
                            Action resultAction = new ActionRescue(humanToRescue); // 则 Rescue
                            context.setResultAction(resultAction);
                            otherFbsSelectHereCount -= buriedness;
                            logger.info("Rescuing " +  humanToRescue + ", buriedness=" + buriednessTool.getBuriedness(humanToRescue));
                            return;
                        } else {
                            logger.info("otherFbsSelectHereCount > 20, I don't select");
                            break;
                        }
                    }
                    else {
                        Action resultAction = new ActionRescue(humanToRescue); // 则 Rescue
                        context.setResultAction(resultAction);
                        otherFbsSelectHereCount -= buriedness;
                        logger.info("Rescuing " +  humanToRescue + ", buriedness=" + buriednessTool.getBuriedness(humanToRescue));
                        return;
                    }
                }
            }
        } else {
            if (mePosArea instanceof Building) // 没有被埋的人的建筑，以后也不用选做目标了
                unavailableTargetTool.setUnavailableAndWait(
                        mePosArea.getID(),
                        UnavailableType.noBuried,
                        Integer.MAX_VALUE,
                        true);
        }
        Debugger.withDebugData(agentInfo,debugData -> {
            debugData.calcTargetOrNot = false;
        });


        var sortedTargetsAndScores = targetProvider.getSortedCandidatesAndScores();
        Debugger.withDebugData(agentInfo, debugData -> {
            debugData.sortedTargetsAndScores = sortedTargetsAndScores;
            debugData.calcTargetOrNot = true;
        });
        //按照得分排序进行显示操作
        for (var t : sortedTargetsAndScores) {
            Area target = t.e0;
            double score = t.e1;
            context.clear();
            if (!fbMediator.isAllowed(target)) { // 竞争失败
                logger.info("Someone better suited to this target" + target + " than me. give up.");
                continue;
            }
            context.setResultTarget(target);
            context.setResultScore(score);
            getAction(mePosArea, target, context, changedEntities);
            if (context.getResultAction() != null) {
                break;
            } else {
                logger.info("Cannot extract an action to do target " + target);
            }
        }

        // TODO 连续三个周期都在原地休息则尝试寻找一个其他的目标
        EntityID myPosAreaId = humanPositionTool.getMyPositionAreaId();
        Area myPosArea = (Area) worldInfo.getEntity(myPosAreaId);
        if(agentInfo.getExecutedAction(-1)  instanceof ActionRest &&agentInfo.getExecutedAction(-2) instanceof ActionRest
                && agentInfo.getExecutedAction(-3) instanceof ActionRest){
            logger.info("I have been rest for 3 times,try to find another target ");
            Point2D myPos = new Point2D(agentInfo.getX(), agentInfo.getY());
            if(myPosArea != null){
                // 向周围没有障碍物的area移动
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
                        context.setResultTarget(area);
                        logger.info("Area：" + areaId + "：blockades is empty：" + blockades);
                        return ;
                    }
                }

                ///////////////////////
                logger.info("try to move to the neighbor area central");
                for (EntityID neighborId : neighbours) {
                    Area neighbor = (Area) worldInfo.getEntity(neighborId);
                    // 条件,选取一个合适的area前往
                    int[] apexes = neighbor.getApexList();
                    // 获取当前Area距离我最近的一条边
                    Line2D closestEdgeOfAreaToMe = HFUTGeometryTool.getClosetLineOnPolygon(myPos, apexes);
                    Point2D midPoint = HFUTGeometryTool.getMidPoint(closestEdgeOfAreaToMe);

                    // 检查中心线的通行情况
                    LineString centerLs = GF.createLineString(new Coordinate[]{
                            new CoordinateXY(agentInfo.getX(), agentInfo.getY()),
                            HFUTGeometryTool.getMidPointCoordinate(closestEdgeOfAreaToMe)
                    });
                    List<EntityID> areas = new ArrayList<>();
                    areas.add(myPosAreaId);
                    areas.add(neighborId);
                    Geometry[] blockades = areas.stream()
                            .map(areaPassableTool::getBlockadePolygonOf)
                            .filter(Objects::nonNull)
                            .toArray(Geometry[]::new);

                    GeometryCollection gc = GF.createGeometryCollection(blockades);
                    Geometry joinedBlockades = gc.union();

                    // 不能到相邻area
                    if(joinedBlockades.intersects(centerLs)){
                        centerLs = GF.createLineString(new Coordinate[]{
                                new CoordinateXY(agentInfo.getX(), agentInfo.getY()),
                                new CoordinateXY(myPosArea.getX(), myPosArea.getY())
                        });
                        if(joinedBlockades.intersects(centerLs)){
                            continue;
                        }
                        // 能到当前area中心
                        context.setResultAction(new ActionMove(List.of(myPosAreaId), myPosArea.getX(), myPosArea.getY()));
                        context.setResultTarget(myPosArea);
                        logger.info("As an alternative, move to myPosArea： " + myPosArea);
                        return ;
                    }

                    ArrayList<EntityID> path = new ArrayList<>();
                    path.add(myPosAreaId);
                    path.add(neighborId);
                    context.setResultAction(new ActionMove(path, (int) midPoint.getX(), (int) midPoint.getY()));
                    context.setResultTarget(neighbor);
                    logger.info("move to the neighbor Area： " + neighbor);
                    return ;
                }
            }
        }
    }

    @Override
    public void afterCalc(CalcContext context) {
        Action resultAction = context.getResultAction();
        Area targetArea = context.getTarget();
        List<EntityID> targetPath = context.getTargetPath();
//        //SearchPathTool Debug
        Painter<?> searchPathpainter = Debugger.getPainterOfAgent(agentInfo);
        if (searchPathpainter != null){
            Area target = context.getTarget();
            if (target != null){
                Path path = searchPathTool.setFromAndTo(agentInfo.getPositionArea(),target)
                        .calc()
                        .getAreaPath();
                if (path != null){
                    for (Area area:path.getRoadList()) {
                        searchPathpainter.addPaintables("FbSearchPathDebug",false,
                                Paintable.shape(area.getShape(),Paintable.addAlpha(Color.ORANGE,40),true));
                    }
                }
            }
        }
    }
    private void getAction(
            @Nonnull Area mePosArea,
            @Nonnull Area target,
            @Nonnull CalcContext context,
            @Nonnull Set<EntityID> changedEntities
    ) {
        // 已经到了 targetArea
        if (mePosArea.equals(target)) {
            logger.info("Already reach " + target + ", and no rescue action is extracted. change another target!");
            unavailableTargetTool.setUnavailableAndWait( // 永远放弃此目标
                    target.getID(),
                    UnavailableType.noBuried,
                    Integer.MAX_VALUE,
                    true);
            return;
        }
        // 否则向 targetArea 移动
        Path resultPath = searchPathTool
                .setFromAndTo(mePosArea, target)
                .calc()
                .getAreaPath();
        if (resultPath == null) {
            logger.info("Cannot find a path to " + target + ", change another target!!");
            if (target instanceof Building)
                unavailableTargetTool.setUnavailableAndWait(
                        target.getID(),
                        UnavailableType.block,
                        Integer.MAX_VALUE,
                        true);
            return;
        }
        List<EntityID> resultIdPath = resultPath.getIDList();

        // 通行性检测：找到路径上第一个堵住的 area，如果在我的视野内，说明肯定走不过去
        // 此时更新 blockedTargets 以通知 pf 来救，并返回 null 要求换一个目标
        EntityID firstImpassableAreaId = pathUtilTool.getFirstImpassableAreaIdOfAreaIdPath(
                (int) agentInfo.getX(), (int) agentInfo.getY(), resultIdPath);
        if (firstImpassableAreaId != null && changedEntities.contains(firstImpassableAreaId)) {
            logger.info("use fix position to try again");
            unavailableTargetTool.setUnavailableAndWait(
                    target.getID(),
                    UnavailableType.block,
                    params.getInt("waitForBlock"),
                    true);
            logger.info("Cannot move to " + target + ", change another target!!");
            return;
        }
        logger.info("I can move to " + target);
        context.setResultAction(new ActionMove(resultPath.getIDList()));
        context.setTargetPath(resultIdPath);
    }

}
