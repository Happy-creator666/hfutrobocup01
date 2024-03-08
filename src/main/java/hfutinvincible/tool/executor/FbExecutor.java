package hfutinvincible.tool.executor;

import adf.core.agent.action.Action;
import adf.core.agent.action.ambulance.ActionRescue;
import adf.core.agent.action.common.ActionMove;
import adf.core.agent.develop.DevelopData;
import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.ScenarioInfo;
import adf.core.agent.info.WorldInfo;
import adf.core.agent.module.ModuleManager;
import hfutinvincible.anno.After;
import hfutinvincible.anno.AgentType;
import hfutinvincible.anno.Autowired;
import hfutinvincible.anno.HfutTool;
import hfutinvincible.debug.Debugger;
import hfutinvincible.debug.Painter;
import hfutinvincible.debug.paintable.Paintable;
import hfutinvincible.tool.*;
import hfutinvincible.tool.comm.MessageManageTool;
import hfutinvincible.tool.edge.AreaPassableTool;
import hfutinvincible.tool.human.*;
import hfutinvincible.tool.mediator.FbMediator;
import hfutinvincible.tool.misc.OptionsTool;
import hfutinvincible.tool.path.Path;
import hfutinvincible.tool.path.PathUtilTool;
import hfutinvincible.tool.path.SearchPathTool;
import hfutinvincible.tool.targetprovider.FbTargetProvider;
import org.apache.log4j.Logger;
import rescuecore2.standard.entities.Area;
import rescuecore2.standard.entities.Building;
import rescuecore2.standard.entities.FireBrigade;
import rescuecore2.standard.entities.Human;
import rescuecore2.worldmodel.EntityID;

import javax.annotation.Nonnull;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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
    //Params
    private Params params = new Params(getClass().getSimpleName());

    @After({"AgentRestTool"})
    @Override
    public void calc(CalcContext context) {
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
                if(otherFbsSelectHereCount <= buriedness){
                    Action resultAction = new ActionRescue(humanToRescue); // 则 Rescue
                    context.setResultAction(resultAction);
                    otherFbsSelectHereCount -= buriedness;
                    logger.info("Rescuing " +  humanToRescue + ", buriedness=" + buriednessTool.getBuriedness(humanToRescue));
                    return;
                } else {
                    if(otherFbsSelectHereCount <= 0)
                        break;
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

        // 否则选择一个 target
        var sortedTargetsAndScores = targetProvider.getSortedCandidatesAndScores();
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

//            // 调试器上，所有目标着浅红色，并用橙色显示其排序
        Painter<?> isCandidatepainter = Debugger.getPainterOfAgent(agentInfo);
        Painter<?> candidateRankpainter = Debugger.getPainterOfAgent(agentInfo);
        if (isCandidatepainter != null && candidateRankpainter != null) {
            for (int i = 0; i < sortedTargetsAndScores.size(); i++) {
                if(i < 3){
                    Area candidate = sortedTargetsAndScores.get(i).e0;
                    isCandidatepainter.addPaintables("IsCandidateLayer",false,
                            Paintable.shape(candidate.getShape(), OptionsTool.T_RED,true));
                    candidateRankpainter.addPaintables("CandidateRankLayer",false,
                            Paintable.text(candidate.getX(), candidate.getY() + 100, String.valueOf(i), Color.RED));
                } else {
                    Area candidate = sortedTargetsAndScores.get(i).e0;
                    isCandidatepainter.addPaintables("IsCandidateLayer",false,
                            Paintable.shape(candidate.getShape(), OptionsTool.T_RED,true));
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
