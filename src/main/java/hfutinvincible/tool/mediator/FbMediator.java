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
        EntityID myID = agentInfo.getID();
        int currentTime = agentInfo.getTime();

        // 获取选定该目标的其他消防员
        Set<EntityID> otherFbsSelectHere = agentTargetAreaTool.getAgentIdsSelectedHere(target.getID())
                .stream()
                .filter(id -> !id.equals(myID)) // 排除自己
                .filter(id -> worldInfo.getEntity(id) instanceof FireBrigade)
                .collect(Collectors.toSet());

        // === 任务锁定机制 ===
        // 获取我上次分配的任务
//        EntityID lastAssignedTarget = agentTargetAreaTool.getTargetAreaId(myID);
//        int lastAssignedTime = agentTargetAreaTool.getUpdateTime(myID);

//        // 如果我之前已经选择了这个目标，并且时间未超过锁定时长，就保持不变
//        int taskLockTime = params.getInt("FbtaskLockTime");
//        if (lastAssignedTarget != null && lastAssignedTarget.equals(target.getID()) && (currentTime - lastAssignedTime < taskLockTime)) {
//            return true; // 任务已锁定，不变更
//        }
        // 太多人选这个目标，我不选（多个消防挖人更快）

        // 【关键判断】如果该区域的消防员数量超过所需数量，则拒绝选择
        // === 避免集体同时放弃 ===
        // 如果消防员数量刚刚超过限制，不是所有人都应该立即放弃
        if (otherFbsSelectHere.size() > params.getInt("tooManyFbSelectHere")) {
            // 采用一定的概率来保持当前目标，防止所有人一起撤离又一起回来
            double stayProbability = params.getDouble("stayProbability"); // 30% 的概率继续留在目标
            if (Math.random() < stayProbability) {
                return true; // 继续留在当前目标
            } else {
                return false; // 寻找新目标
            }
        }

        // 获取该区域内的可救援埋压者 (转换为 EntityID)
        Set<EntityID> rescuableBuriedHumanIDs = worldInfo.getBuriedHumans(target.getID()).stream()
                .map(human -> human.getID())  // 转换为 EntityID
                .collect(Collectors.toSet());

        // 计算该区域内的总埋压程度
        int totalBuriedness = rescuableBuriedHumanIDs.stream()
                .mapToInt(buriednessTool::getBuriedness)
                .sum();

        if(otherFbsSelectHere.size() <= totalBuriedness){
            return true;
        }
        else {
            double stayProbability = params.getDouble("stayProbability"); // 30% 的概率继续留在目标
            if (Math.random() < stayProbability) {
                return true; // 继续留在当前目标
            } else {
                return false; // 寻找新目标
            }
        }
    }

//    public boolean isAllowed(@Nonnull Area target) {
//        // 其他和我选择同一 area 的 pf 是我的竞争者
//        Set<EntityID> otherFbsSelectHere = agentTargetAreaTool.getAgentIdsSelectedHere(target.getID())
//                .stream()
//                .filter(id -> !id.equals(agentInfo.getID())) // 除了我自己
//                .filter(id -> worldInfo.getEntity(id) instanceof FireBrigade)
//                .collect(Collectors.toSet());
//
//        // 太多人选这个目标，我不选（多个消防挖人更快） TODO 根据目标区域上可以救的人数计算
//        if (otherFbsSelectHere.size() > params.getInt("tooManyFbSelectHere"))
//            return false;
//
//        return true;
//    }
}
