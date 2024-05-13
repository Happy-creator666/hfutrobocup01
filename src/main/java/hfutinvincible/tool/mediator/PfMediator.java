package hfutinvincible.tool.mediator;

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
import hfutinvincible.tool.MeasureToolTest;
import hfutinvincible.tool.Params;
import hfutinvincible.tool.human.AgentTargetAreaTool;
import hfutinvincible.tool.human.HumanPositionTool;
import hfutinvincible.tool.misc.MeasureLengthTool;
import org.apache.log4j.Logger;
import rescuecore2.standard.entities.Area;
import rescuecore2.standard.entities.PoliceForce;
import rescuecore2.worldmodel.EntityID;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@HfutTool
@AgentType({PoliceForce.class})
public class PfMediator implements LifeCycle,Mediator<Area> {
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
    //Tools
    @Autowired
    AgentTargetAreaTool agentTargetAreaTool;
    @Autowired
    HumanPositionTool humanPositionTool;
    @Autowired
    MeasureLengthTool measureLengthTool;

    //TODO 修改
    public boolean isAllowed(@Nonnull Area target) {
        logger.info("------------------Then is the" + agentInfo.getTime() + "time Pf isAllowed------------------");

        // 其他和我选择同一 area 的 pf 是我的竞争者
        Set<EntityID> otherPfsSelectHere = agentTargetAreaTool.getAgentIdsSelectedHere(target.getID())
                .stream()
                .filter(id -> !id.equals(agentInfo.getID())) // 除了我自己
                .filter(id -> worldInfo.getEntity(id) instanceof PoliceForce)
                .collect(Collectors.toUnmodifiableSet());
        // 太多人选这个目标，我不选
        if (otherPfsSelectHere.size() > params.getInt("tooManyPfSelectHere")) {
            logger.info("tooManyPfSelectHere: " + otherPfsSelectHere.size());
//            return false;
            int minId = Integer.MAX_VALUE;
            int myId = agentInfo.getID().getValue();
            for (EntityID entityId : otherPfsSelectHere) {
                if(entityId.getValue() < minId){
                    minId = entityId.getValue();
                }
            }
            logger.info(myId < minId ? "choose this target" : "abandon this target");
            return myId < minId;
        }

        //Todo 观察bug发现两个 pf 同时有相同的path以及相同目标的情况 想要添加判断若有两个path相同的pf则选择更为靠近的Pf进行

        // 当前我选择的目标，如果其他 pf 也选择这个目标，则我放弃此任务

        return true;
    }
}
