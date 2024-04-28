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
import hfutinvincible.tool.human.BuriednessTool;
import hfutinvincible.tool.human.HumanPositionTool;
import hfutinvincible.tool.human.TimeToDeadTool;
import hfutinvincible.tool.misc.MeasureLengthTool;
import org.apache.log4j.Logger;
import rescuecore2.standard.entities.Area;
import rescuecore2.standard.entities.FireBrigade;
import rescuecore2.worldmodel.EntityID;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@HfutTool
@AgentType({FireBrigade.class})
public class FbMediator implements LifeCycle,Mediator<Area> {
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
    AgentTargetAreaTool agentTargetAreaTool;
    @Autowired
    HumanPositionTool humanPositionTool;
    @Autowired
    MeasureLengthTool measureLengthTool;
    @Autowired
    TimeToDeadTool timeToDeadTool;
    @Autowired
    BuriednessTool buriednessTool;
    //Params
    private Params params = new Params(getClass().getSimpleName());
    @Override
    public boolean isAllowed(@Nonnull Area target) {
        // 其他和我选择同一 area 的 pf 是我的竞争者
        Set<EntityID> otherFbsSelectHere = agentTargetAreaTool.getAgentIdsSelectedHere(target.getID())
                .stream()
                .filter(id -> !id.equals(agentInfo.getID())) // 除了我自己
                .filter(id -> worldInfo.getEntity(id) instanceof FireBrigade)
                .collect(Collectors.toSet());

        // 太多人选这个目标，我不选（多个消防挖人更快） TODO 根据目标区域上可以救的人数计算
        if (otherFbsSelectHere.size() > params.getInt("tooManyFbSelectHere"))
            return false;

        // 竞争者距离 area 的最小距离
        double minDistance = otherFbsSelectHere.stream().map(humanPositionTool::getPositionAreaId)
                .filter(Objects::nonNull)
                .mapToDouble(id -> measureLengthTool.getManhattanLength(id, target.getID()))
                .min()
                .orElse(Double.MAX_VALUE);

        // 如果我比最接近其他 pf 还要更接近这个 area 一段距离，则我抢下此任务
        Area mePosArea = humanPositionTool.getMyPositionArea();
        double meDistance = measureLengthTool.getManhattanLength(mePosArea, target);
        if (minDistance - meDistance > params.getDouble("preemptDistanceThreshold"))
            return true;
        return false;
    }
}
