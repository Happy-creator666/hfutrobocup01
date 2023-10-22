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
import hfutinvincible.tool.human.HumanPositionTool;
import hfutinvincible.tool.misc.MeasureLengthTool;
import org.apache.log4j.Logger;
import rescuecore2.standard.entities.AmbulanceTeam;
import rescuecore2.standard.entities.Area;
import rescuecore2.standard.entities.FireBrigade;
import rescuecore2.standard.entities.PoliceForce;
import rescuecore2.worldmodel.EntityID;

import javax.annotation.Nonnull;
import java.util.Set;
import java.util.stream.Collectors;

@HfutTool
@AgentType({AmbulanceTeam.class, FireBrigade.class, PoliceForce.class})
public class PfMediator implements LifeCycle, Mediator<Area> {
    @Autowired
    private AgentInfo agentInfo;
    @Autowired
    private WorldInfo worldInfo;
    @Autowired
    private Logger logger;

    // Tools
    @Autowired
    private AgentTargetAreaTool agentTargetAreaTool;
    @Autowired
    private HumanPositionTool humanPositionTool;
    @Autowired
    private MeasureLengthTool measureLengthTool;

    protected final Params params;

    public PfMediator() {
        this.params = new Params(getClass().getSimpleName());

    }
    //TODO 修改
    @Override
    public boolean isAllowed(@Nonnull Area target) {
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
//
//        // 竞争者距离 area 的最小距离
//        double minDistance = otherPfsSelectHere.stream().map(humanPositionTool::getPositionAreaId)
//                .filter(Objects::nonNull)
//                .mapToDouble(id -> measureLengthTool.getManhattanLength(id, target.getID()))
//                .min()
//                .orElse(Double.MAX_VALUE);
//
//
//        // 如果我比最接近其他 pf 还要更接近这个 area 一段距离，则我抢下此任务
//        Area mePosArea = humanPositionTool.getMyPositionArea();
//        double meDistance = measureLengthTool.getManhattanLength(mePosArea, target);
//        if (minDistance - meDistance > params.getDouble("preemptDistanceThreshold"))
//            return true;
        return true;
    }
}