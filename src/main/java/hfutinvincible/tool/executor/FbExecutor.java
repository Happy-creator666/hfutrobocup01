package hfutinvincible.tool.executor;

import adf.core.agent.action.Action;
import adf.core.agent.action.common.ActionMove;
import adf.core.agent.action.common.ActionRest;
import adf.core.agent.action.fire.ActionRescue;
import adf.core.agent.develop.DevelopData;
import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.ScenarioInfo;
import adf.core.agent.info.WorldInfo;
import adf.core.agent.module.ModuleManager;
import hfutinvincible.anno.AgentType;
import hfutinvincible.anno.Autowired;
import hfutinvincible.anno.HfutTool;
import hfutinvincible.brain.ResultCollector;
import hfutinvincible.debug.CandidateRankLayer;
import hfutinvincible.debug.HFUTDebugger;
import hfutinvincible.debug.IsCandidateLayer;
import hfutinvincible.tool.*;
import hfutinvincible.tool.comm.MessageManageTool;
import hfutinvincible.tool.edge.AreaPassableTool;
import hfutinvincible.tool.human.*;
import hfutinvincible.tool.mediator.FbMediator;
import hfutinvincible.tool.misc.OptionsTool;
import hfutinvincible.tool.path.Path;
import hfutinvincible.tool.path.PathUtilTool;
import hfutinvincible.tool.path.SearchPathTool;
import hfutinvincible.tool.targetprovider.FbCriteriaBasedTargetProvider;
import org.apache.log4j.Logger;
import org.locationtech.jts.geom.Coordinate;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;

import javax.annotation.Nonnull;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@HfutTool
@AgentType({AmbulanceTeam.class, FireBrigade.class, PoliceForce.class})
public class FbExecutor implements LifeCycle {
    @Autowired
    private AgentInfo agentInfo;
    @Autowired
    private WorldInfo worldInfo;
    @Autowired
    private Logger logger;

    // Tools
    @Autowired
    private FbCriteriaBasedTargetProvider targetProvider;
    @Autowired
    private SearchPathTool searchPathTool;
    @Autowired
    private AgentTargetAreaTool agentTargetAreaTool;
    @Autowired
    private BuriednessTool buriednessTool;
    @Autowired
    private StuckInfoTool stuckInfoTool;
    @Autowired
    private TimeToDeadTool timeToDeadTool;
    @Autowired
    private HumanPositionTool humanPositionTool;
    @Autowired
    private MessageManageTool messageTool;
    @Autowired
    private PathUtilTool pathUtilTool;
    @Autowired
    private UnavailableTargetTool unavailableTargetTool;
    @Autowired
    private FbMediator fbMediator;
    @Autowired
    private FbAtStuckEscapeTool fbAtStuckEscapeTool;
    @Autowired
    private AgentRestTool agentRestTool;
    @Autowired
    private AreaPassableTool areaPassableTool;

    protected final Params params;

    public FbExecutor() {
        this.params = new Params(getClass().getSimpleName());


        onCalcAfter(resultCollector -> {
//            ArrayList<EntityID> init = new ArrayList<>();
//            init.add(humanPositionTool.getMyPositionAreaId());
//            resultCollector.setResultAction(new ActionMove(init));

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
                        resultCollector.setResultAction(resultAction);
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
                resultCollector.clear();
                if (!fbMediator.isAllowed(target)) { // 竞争失败
                    logger.info("Someone better suited to this target" + target + " than me. give up.");
                    continue;
                }
                resultCollector.setResultTarget(target);
                resultCollector.setResultScore(score);
                getAction(mePosArea, target, resultCollector, changedEntities);
                if (resultCollector.getResultAction() != null) {
                    break;
                } else {
                    logger.info("Cannot extract an action to do target " + target);
                }
            }

//            // 调试器上，所有目标着浅红色，并用橙色显示其排序
            var isCandidateLayer = HFUTDebugger.getMiscLayerOf(agentInfo, IsCandidateLayer.class);
            var candidateRankLayer = HFUTDebugger.getMiscLayerOf(agentInfo, CandidateRankLayer.class);
            if (isCandidateLayer != null && candidateRankLayer != null) {
                for (int i = 0; i < sortedTargetsAndScores.size(); i++) {
                    if(i < 3){
                        Area candidate = sortedTargetsAndScores.get(i).e0;
                        isCandidateLayer.addShape(candidate.getShape(), OptionsTool.T_RED);
                        candidateRankLayer.addText(candidate.getX(), candidate.getY() + 100, String.valueOf(i), Color.RED);
                    } else {
                        Area candidate = sortedTargetsAndScores.get(i).e0;
                        isCandidateLayer.addShape(candidate.getShape(), OptionsTool.T_RED);
                        candidateRankLayer.addText(candidate.getX(), candidate.getY() + 100, String.valueOf(i), Color.ORANGE);
                    }
                }
            }
        }, () -> agentRestTool, () -> fbAtStuckEscapeTool);

    }

    @Override
    public void afterCalc(CalcContext context) {

        Action resultAction = context.getResultAction();
        Area targetArea = context.getTarget();
        List<EntityID> targetPath = context.getTargetPath();

//            MiscLayer actionLayer = HFUTDebugger.getMiscLayerOf(agentInfo, FbActionLayer.class);
//
//            if (actionLayer != null) {
//                // 如果选择了某个 area 作为目标，调试器上标红
//                if (targetArea != null)
//                    actionLayer.addPointCross(targetArea.getX(), targetArea.getY(), Color.RED, 4000);
//
//                // 如果指定了移动路径，调试器上标黄
//                if (targetPath != null)
//                    targetPath.stream()
//                            .map(worldInfo::getEntity)
//                            .map(Area.class::cast)
//                            .filter(Objects::nonNull)
//                            .map(Area::getShape)
//                            .forEach(shape -> actionLayer.addShape(shape, OptionsTool.T_YELLOW));
//
//                // 如果我在救人，则被救的人头上标记 rescued，并画一个我指向被救的人的箭头
//                if (resultAction instanceof ActionRescue ar) {
//                    EntityID rescuedHumanId = ar.getTarget();
//                    Human rescuedHuman = (Human) worldInfo.getEntity(rescuedHumanId);
//                    if (rescuedHuman != null && rescuedHuman.isXDefined() && rescuedHuman.isYDefined())
//                        actionLayer.addText(rescuedHuman.getX(), rescuedHuman.getY(), "rescued", Color.RED)
//                                .addArrow(agentInfo.getX(), agentInfo.getY(), rescuedHuman.getX(), rescuedHuman.getY(), OptionsTool.T_RED);
//                }
//            }
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