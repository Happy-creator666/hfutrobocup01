package hfutinvincible.tool.executor;


import adf.core.agent.action.Action;
import adf.core.agent.action.ambulance.ActionLoad;
import adf.core.agent.action.ambulance.ActionUnload;
import adf.core.agent.action.common.ActionMove;
import adf.core.agent.action.common.ActionRest;
import adf.core.agent.develop.DevelopData;
import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.ScenarioInfo;
import adf.core.agent.info.WorldInfo;
import adf.core.agent.module.ModuleManager;
import adf.core.agent.precompute.PrecomputeData;
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
import hfutinvincible.tool.area.ToAwt;
import hfutinvincible.tool.building.BuildingBrokennessTool;
import hfutinvincible.tool.building.RefugeConditionTool;
import hfutinvincible.tool.comm.MessageManageTool;
import hfutinvincible.tool.edge.AreaPassableTool;
import hfutinvincible.tool.human.*;
import hfutinvincible.tool.mediator.AtMediator;
import hfutinvincible.tool.misc.OptionsTool;
import hfutinvincible.tool.path.Path;
import hfutinvincible.tool.path.PathUtilTool;
import hfutinvincible.tool.path.SearchPathTool;
import hfutinvincible.tool.targetprovider.AtTargetProvider;
import org.apache.log4j.Logger;
import org.locationtech.jts.geom.Geometry;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;

import javax.annotation.Nonnull;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;

@HfutTool
@AgentType({AmbulanceTeam.class})
public class   AtExecutor implements LifeCycle {

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
    @Autowired
    SearchPathTool searchPathTool;
    @Autowired
    AtTargetProvider targetProvider;
    @Autowired
    BuildingBrokennessTool buildingBrokennessTool;
    @Autowired
    RefugeConditionTool refugeConditionTool;
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
    AreaPassableTool areaPassableTool;
    @Autowired
    PathUtilTool pathUtilTool;
    @Autowired
    UnavailableTargetTool unavailableTargetTool;
    @Autowired
    AtMediator atMediator;
//    @Autowired
//    FbAtStuckEscapeTool fbAtStuckEscapeTool;
    @Autowired
    AgentRestTool agentRestTool;


    //Params
    private final Params params = new Params(getClass().getSimpleName());

    @Override
    public void resume(PrecomputeData precomputeData) {
        params.registerParamsChangedListener(() -> {
            DebugData debugData = Debugger.getDebugData(agentInfo);
            if(debugData == null) return;
            if (!debugData.calcTargetOrNot) return;
            logger.info("params modified,recalculate sortedTargetsAndScores");
            debugData.sortedTargetsAndScores = targetProvider.getSortedCandidatesAndScores();
            logger.debug("newSortedTargetsAndScores=" + debugData.sortedTargetsAndScores);
            Debugger.registerOrRefreshAgent(worldInfo, agentInfo, scenarioInfo);
        });
    }


    @After({"AgentRestTool","FbAtStuckEscapeTool2"})
    @Override
    public void calc(CalcContext context) {
        final Area mePosArea = humanPositionTool.getMyPositionArea();
        final EntityID mePosAreaId = mePosArea.getID();
        final Set<EntityID> changedEntities = worldInfo.getChanged().getChangedEntities();
        final Action prevAction = agentInfo.getExecutedAction(-1);  //获得上一个周期的行为

        // pre-check
        Human humanOnBoard = agentInfo.someoneOnBoard();
        if (humanOnBoard != null) { // 如果现在车上有人
            if (prevAction instanceof ActionUnload) // TODO 有问题
                humanOnBoard.setPosition(mePosAreaId);
            else if (mePosArea instanceof Refuge // 到 refuge 了
                    || humanOnBoard.isHPDefined() && humanOnBoard.getHP() == 0) { // 或者车上的人已经死了
                logger.info("Unload " + humanOnBoard);
                context.setResultAction(new ActionUnload()); // 把人卸下去
                return;
            }
        }
        // 车上没人
        else if (!(mePosArea instanceof Refuge)) { // 我不在 refuge 上，则可以装人
            // 获得我所在 area 上除我自己外其他人
            Set<EntityID> otherHumanIdsOnMyArea = humanPositionTool.getHumanIdsOnArea(mePosAreaId);
            otherHumanIdsOnMyArea.remove(agentInfo.getID());
            List<EntityID> buriedHumanIds = new ArrayList<>(); // 所有被埋的人
            List<EntityID> timeToDeadNotSureHumanIds = new ArrayList<>(); // 所有timeToDead 不确定的人
            List<EntityID> rescuableHumanIds = new ArrayList<>(); // 所有可以救的人
            for (EntityID humanId : otherHumanIdsOnMyArea) {
                // 如果上次也在装这个人，则说明没装成功，放弃 TODO 冗余检测，如果逻辑没问题，不会出现这个错误
                if (prevAction instanceof ActionLoad al && Objects.equals(al.getTarget(), humanId))
                    continue;
                // at 只能 load civilian
                if(!(worldInfo.getEntity(humanId) instanceof Civilian))
                    continue;
                // 这个人被装在其他 at 上
                EntityID humanPosId = humanPositionTool.getPosition(humanId);
                if (humanPosId == null || worldInfo.getEntity(humanPosId) instanceof AmbulanceTeam)
                    continue;
                Integer buriedness = buriednessTool.getBuriedness(humanId);
                if (buriedness == null) { // IMPOSSIBLE BRANCH
                    logger.error("IMPOSSIBLE! I should always know the buriedness of human in the same area as me.");
                    continue;
                }
                if (buriedness != 0) { // 有人仍然被埋，我不能 load
                    buriednessTool.addBuriedTargetCv(humanId);
                    buriedHumanIds.add(humanId);
                    logger.info("human " + humanId + " is still buried. ignore.");
                    continue;
                }
                Integer timeToDead = timeToDeadTool.getTimeToDeadNowEstimate(humanId);
                InfoSource timeToDeadSource = timeToDeadTool.getInfoSource(humanId);
                if (timeToDead == null) { // IMPOSSIBLE BRANCH
                    logger.error("IMPOSSIBLE! I should always know the timeToDead of human in the same area as me.");
                    continue;
                }
                // 一种猜测，一个市民可以活过周期模拟，并不代表他健康，反而，这个市民在模拟周期的末期，因为及时得到了“救治”，
                // 还算比较健康，最后得到的总分会相对高些
                if (timeToDead >= OptionsTool.KERNEL_TIMESTEPS) {
                    if (timeToDeadSource == InfoSource.WITNESS) // 是看到的，则可能不准确 TODO 是否不合理，因为根据 stayCount 修改后这里还是会判断不准确
                        timeToDeadNotSureHumanIds.add(humanId);
                    logger.info("human " + humanId + " is not hurt. ignore.");
                    continue;
                }
                // TODO 减去 buriedness，再加其他一些判断
                if (timeToDead < params.getDouble("ignoreTimeToDead")) {
                    logger.info("human " + humanId + " is almost died. ignore.");
                    continue;
                }
                logger.info("timeToDead: " + humanId + "->" + timeToDead + "(KERNEL_TIMESTEPS: " + OptionsTool.KERNEL_TIMESTEPS + ")");
                rescuableHumanIds.add(humanId);
            }
            boolean hasRescuableHuman = !rescuableHumanIds.isEmpty();
            boolean hasBuriedHuman = !buriedHumanIds.isEmpty();
            boolean hasTimeToDeadNotSureHuman = !timeToDeadNotSureHumanIds.isEmpty();
            // 有可以救的人，则救之
            if (hasRescuableHuman) {
                logger.info("the civilian I rescue is " + rescuableHumanIds.get(0) + ", his timeToDead is " + timeToDeadTool.getTimeToDeadNowEstimate(rescuableHumanIds.get(0)));
                context.setResultAction(new ActionLoad(rescuableHumanIds.get(0)));
                return;
            }
            // 有被埋的人
            if (hasBuriedHuman) {
                int minBuriedness = buriedHumanIds.stream()
                        .mapToInt(humanId -> {
                            Integer x = buriednessTool.getBuriedness(humanId);
                            return x == null ? Integer.MAX_VALUE : x;
                        })
                        .min()
                        .orElse(Integer.MAX_VALUE);
                if (minBuriedness != Integer.MAX_VALUE) {
                    Set<EntityID> fbIdsSelectHere = agentTargetAreaTool.getFbIdsSelectHere(mePosAreaId);
                    int timeToWait = fbIdsSelectHere.isEmpty() ?
                            minBuriedness / params.getInt("buriednessScaleFactor") + params.getInt("waitForBuriedAdditional") :
                            (minBuriedness / fbIdsSelectHere.size()) + 1;

                    unavailableTargetTool.setUnavailableAndWait(
                            mePosArea.getID(),
                            UnavailableType.buried,
                            timeToWait,
                            true);
                }
            }
            // 有 timeToDead 不确定的人
            if (hasTimeToDeadNotSureHuman) {
                // 之前曾被标记为 noHurtNotSure，说明来看过一次，这次转换为 noHurtDefinitely，之后也不来看了
                if (unavailableTargetTool.wasEverUnavailable(mePosAreaId, UnavailableType.noHurtNotSure))
                    unavailableTargetTool.setUnavailableAndWait(
                            mePosAreaId,
                            UnavailableType.noHurtDefinitely,
                            Integer.MAX_VALUE,
                            true);
                    // 否则先标记为 noHurtNotSure，等会儿在来看看
                else unavailableTargetTool.setUnavailableAndWait(
                        mePosArea.getID(),
                        UnavailableType.noHurtNotSure,
                        params.getInt("waitForNoHurtNotSure"),
                        true);
            }
            // 这个地方没有任何价值再来
            if (!hasRescuableHuman && !hasBuriedHuman && !hasTimeToDeadNotSureHuman) {
                unavailableTargetTool.setUnavailableAndWait(
                        mePosAreaId,
                        UnavailableType.noHurtDefinitely,
                        Integer.MAX_VALUE,
                        true);
            }
        }
        Debugger.withDebugData(agentInfo ,debugData -> {
            debugData.calcTargetOrNot = false;
        });

        // 否则选择一个 target
        var sortedTargetsAndScores = targetProvider.getSortedCandidatesAndScores();
        Debugger.withDebugData(agentInfo ,debugData -> {
            debugData.sortedTargetsAndScores = sortedTargetsAndScores;
            debugData.calcTargetOrNot = true;
        });
        for (var t : sortedTargetsAndScores) {
            Area target = t.e0;
            double score = t.e1;
            context.clear();
            if (!atMediator.isAllowed(target)) { // 竞争失败
                logger.info("Someone better suited to this target than me. give up.");
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
            // 向周围没有障碍物的area移动
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
                        context.setResultTarget(area);
                        logger.info("Area：" + areaId + "：blockades is empty：" + blockades);
                        return ;
                    }
                }
            }
        }

        logger.info(context);
        // 调试器上，所有目标着浅红色，并用橙色显示其排序
        Painter<?> isCandidatepainter = Debugger.getPainterOfAgent(agentInfo);
        Painter<?> candidateRankpainter = Debugger.getPainterOfAgent(agentInfo);
        if (isCandidatepainter != null && candidateRankpainter != null) {
            for (int i = 0; i < sortedTargetsAndScores.size(); i++) {
                if(i < 3){
                    Area candidate = sortedTargetsAndScores.get(i).e0;
                    isCandidatepainter.addPaintables("IsCandidateLayer",false,
                            Paintable.shape(candidate.getShape(),OptionsTool.T_BLUE,true));
                    candidateRankpainter.addPaintables("CandidateRankLayer",false,
                            Paintable.text(candidate.getX(), candidate.getY() + 100, String.valueOf(i), Color.RED));
                }
                else{
                    Area candidate = sortedTargetsAndScores.get(i).e0;
                    isCandidatepainter.addPaintables("IsCandidateLayer",false,
                            Paintable.shape(candidate.getShape(),OptionsTool.T_BLUE,true));
                    candidateRankpainter.addPaintables("CandidateRankLayer",false,
                            Paintable.text(candidate.getX(), candidate.getY() + 100, String.valueOf(i), Color.ORANGE));
                }
            }
        }
    }


    @Override
    public void afterCalc(CalcContext context) {
        Action resultAction = context.getResultAction();
        Area targetArea = context.getTarget();
        List<EntityID> targetPath = context.getTargetPath();
        Painter<?> searchPathpainter = Debugger.getPainterOfAgent(agentInfo);
        if (searchPathpainter != null){
            Area target = context.getTarget();
            if (target != null){
                Path path = searchPathTool.setFromAndTo(agentInfo.getPositionArea(),target)
                        .calc()
                        .getAreaPath();
                if (path != null){
                    for (Area area:path.getRoadList()) {
                        searchPathpainter.addPaintables("AtSearchPathDebug",false,
                                Paintable.shape(area.getShape(),Paintable.addAlpha(Color.ORANGE,40),true));
                    }
                }
            }
        }
    }
    public void getAction(
            @Nonnull Area mePosArea,
            @Nonnull Area target,
            @Nonnull CalcContext context,
            @Nonnull Set<EntityID> changedEntities
    ) {
        // 如果已经到了 targetArea，还提取不出动作，则返回 null 换一个目标
        if (mePosArea.equals(target)) {
            logger.info("already reached target " + target + " but no action is extracted. change another target to do.");
            return;
        }

        // 否则尝试向 targetArea 移动
        Path resultPath = searchPathTool
                .setFromAndTo(mePosArea, target)
                .calc()
                .getAreaPath();
        logger.info("----------------Time ---"+agentInfo.getTime()+"-----"+resultPath+"----------");
        if (resultPath == null) {
            logger.info("Cannot find a path to " + target + ", change another target!!");
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
            unavailableTargetTool.setUnavailableAndWait(
                    target.getID(),
                    UnavailableType.block,
                    params.getInt("waitForBlock"),
                    true);
            logger.info("Cannot move to " + target + ", change another target!!");
            // -------------------------
//            Painter<?> painter = Debugger.getPainterOfAgent(agentInfo);
//            if (painter != null) {
//                Geometry cfreePolygon = areaPassableTool.getCfreePolygonOf(firstImpassableAreaId);
//                List<Shape> cfreePolygonShape = ToAwt.from(cfreePolygon);
//                if (cfreePolygonShape != null) {
//                    for (Shape s : cfreePolygonShape)
//                        painter.addPaintables("AtActionLayer",false,
//                                        Paintable.shape(s,new Color(255, 0, 0, 30),true));
//                }
//            }
            logger.info("Cannot move to " + target + "(block by area: " + firstImpassableAreaId + ", change anoth er target!!");
            // ---------------------------
            return;
        }

        logger.info("I can move to " + target);
        context.setResultAction(new ActionMove(resultIdPath));
        context.setTargetPath(resultIdPath);
    }
}
