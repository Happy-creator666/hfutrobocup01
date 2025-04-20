package hfutinvincible.tool.mediator;

import adf.core.agent.develop.DevelopData;
import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.ScenarioInfo;
import adf.core.agent.info.WorldInfo;
import adf.core.agent.module.ModuleManager;
import hfutinvincible.anno.AgentType;
import hfutinvincible.anno.Autowired;
import hfutinvincible.anno.HfutTool;
import hfutinvincible.tool.LifeCycle;
import hfutinvincible.tool.Params;
import hfutinvincible.tool.human.AgentTargetAreaTool;
import hfutinvincible.tool.human.AgentTargetPathTool;
import hfutinvincible.tool.human.HumanPositionTool;
import hfutinvincible.tool.misc.MeasureLengthTool;
import org.apache.log4j.Logger;
import rescuecore2.standard.entities.Area;
import rescuecore2.standard.entities.PoliceForce;
import rescuecore2.worldmodel.EntityID;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.Set;
import java.util.List;
import java.util.Comparator;
import java.util.stream.Collectors;

@HfutTool
@AgentType({PoliceForce.class})
public class PfMediator implements LifeCycle, Mediator<Area> {
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
    @Autowired
    Logger logger;
    
    private Params params = new Params(getClass().getSimpleName());

    // Tools
    @Autowired
    AgentTargetAreaTool agentTargetAreaTool;
    @Autowired
    AgentTargetPathTool agentTargetPathTool;
    @Autowired
    HumanPositionTool humanPositionTool;
    @Autowired
    MeasureLengthTool measureLengthTool;

    // 获取最小 ID 的警察
    private EntityID getMinSelectedId(Set<EntityID> otherPfsSelectHere) {
        return otherPfsSelectHere.stream()
                .min(Comparator.comparingInt(EntityID::getValue))
                .orElse(null);
    }

    // 获取距离目标最近的警察
    private double getMinDistanceToTarget(Set<EntityID> otherPfsSelectHere, Area target) {
        return otherPfsSelectHere.stream()
                .map(humanPositionTool::getPositionAreaId)
                .filter(Objects::nonNull)
                .mapToDouble(id -> measureLengthTool.getManhattanLength(id, target.getID()))
                .min()
                .orElse(Double.MAX_VALUE);
    }

    // 判断目标是否允许
    public boolean isAllowed(@Nonnull Area target) {
        logger.info("------------------Then is the" + agentInfo.getTime() + "time Pf isAllowed------------------");

        // 获取其他选择同一目标区域的警察
        Set<EntityID> otherPfsSelectHere = agentTargetAreaTool.getAgentIdsSelectedHere(target.getID())
                .stream()
                .filter(id -> !id.equals(agentInfo.getID())) // 除了我自己
                .filter(id -> worldInfo.getEntity(id) instanceof PoliceForce)
                .collect(Collectors.toUnmodifiableSet());

        // 如果选择目标的警察数量超过阈值，选择最小 ID 的警察
        if (otherPfsSelectHere.size() > params.getInt("tooManyPfSelectHere")) {
            logger.info("tooManyPfSelectHere: " + otherPfsSelectHere.size());
            EntityID minId = getMinSelectedId(otherPfsSelectHere);
            int myId = agentInfo.getID().getValue();
            logger.info(myId < minId.getValue() ? "choose this target" : "abandon this target");
            return myId < minId.getValue();
        }

        // 判断路径是否完全相同
        List<EntityID> myPath = agentTargetPathTool.getTargetPath(agentInfo.getID());
        Set<List<EntityID>> otherPfsPaths = otherPfsSelectHere.stream()
                .map(agentTargetPathTool::getTargetPath)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (myPath != null && otherPfsPaths.contains(myPath)) {
            double minPathDistance = getMinDistanceToTarget(otherPfsSelectHere, target);
            double meDistance = measureLengthTool.getManhattanLength(humanPositionTool.getMyPositionArea(), target);
            if (meDistance > minPathDistance) {
                logger.info("Another PF has the same path and is closer, giving up!");
                return false;
            }
        }

        // 获取竞争者与目标的最小距离
        double minDistance = getMinDistanceToTarget(otherPfsSelectHere, target);

        // 如果我比最接近其他 pf 还要更接近目标，则抢下此任务
        Area mePosArea = humanPositionTool.getMyPositionArea();
        double meDistance = measureLengthTool.getManhattanLength(mePosArea, target);
        if (minDistance - meDistance > params.getDouble("preemptDistanceThreshold")) {
            logger.info("It's my target!");
            return true;
        }

        logger.info("It's not my target!");
        return false;
    }
}