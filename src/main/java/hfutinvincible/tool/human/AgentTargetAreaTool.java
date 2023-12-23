package hfutinvincible.tool.human;

import adf.core.agent.communication.MessageManager;
import adf.core.agent.develop.DevelopData;
import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.ScenarioInfo;
import adf.core.agent.info.WorldInfo;
import adf.core.agent.module.ModuleManager;
import hfutinvincible.anno.AgentType;
import hfutinvincible.anno.Autowired;
import hfutinvincible.anno.HfutTool;
import hfutinvincible.message.AgentConditionMessage;
import hfutinvincible.tool.CalcContext;
import hfutinvincible.tool.LifeCycle;
import hfutinvincible.tool.comm.MessageManageTool;
import hfutinvincible.tool.path.InPartitionSearchPathTool;
import hfutinvincible.tool.path.PathUtilTool;
import org.apache.log4j.Logger;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;

@HfutTool
@AgentType({AmbulanceTeam.class, PoliceForce.class, FireBrigade.class})
public class AgentTargetAreaTool implements LifeCycle {
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

    public Map<EntityID, AgentTargetAreaRecord> recordMap;

    /** 记录每个 area 被哪些 agent 选中，考虑 PF, AT 和 FB */
    private final Map<EntityID, Set<EntityID>> agentSelectMap;

    /**
     * 视野范围内 targetAreaId 改变了的 Human 的 EntityIDs
     */
    private final Set<EntityID> targetAreaIdChangedAgentIdsInSight;

    //Tools
    @Autowired
    MessageManageTool messageTool;
    @Autowired HumanPositionTool humanPositionTool;
    @Autowired
    InPartitionSearchPathTool inPartitionSearchPathTool;
    @Autowired
    PathUtilTool pathUtilTool;

    public AgentTargetAreaTool(){
        this.recordMap = new HashMap<>();
        this.agentSelectMap = new HashMap<>();
        this.targetAreaIdChangedAgentIdsInSight = new HashSet<>();
    }

    @Override
    public void updateInfo(MessageManager messageManager) {
        this.humanPositionTool.updateInfo(messageManager);
        this.messageTool.updateInfo(messageManager);

        int time = agentInfo.getTime();


        // 从消息更新
        this.targetAreaIdChangedAgentIdsInSight.clear();
        messageTool.getReceivedMessages(AgentConditionMessage.class)
                .forEach(message -> {
                    InfoSource infoSource = message.isRadio()
                            ? InfoSource.FROM_RADIO
                            : InfoSource.FROM_VOICE;
                    updateTargetArea(message.getAgentId(), message.getTargetAreaId(), time - 1, infoSource);
                });

        // 更新 agentSelectMap
        this.agentSelectMap.clear();
        worldInfo.getEntityIDsOfType(StandardEntityURN.POLICE_FORCE,
                        StandardEntityURN.AMBULANCE_TEAM, StandardEntityURN.FIRE_BRIGADE)
                .forEach(agentId -> {
                    EntityID targetAreaId = getTargetAreaId(agentId);
                    if (targetAreaId == null)
                        return;
                    this.agentSelectMap.compute(targetAreaId, (k, v) -> {
                        Set<EntityID> ret = v == null ? new HashSet<>() : v;
                        ret.add(agentId);
                        return ret;
                    });


                });
    }

    @Override
    public void afterCalc(CalcContext context) {
        // 根据我这个周期的决策更新
        int time = agentInfo.getTime();
        Area targetArea = context.getTarget();
        EntityID targetAreaId = targetArea == null
                ? null
                : targetArea.getID();
        updateTargetArea(agentInfo.getID(), targetAreaId, time, InfoSource.MY_SELF);
    }

    /**
     * 返回选择一个 area 为目标的所有 at 的 EntityID
     */
    @Nonnull
    public Set<EntityID> getAtIdsSelectHere(@Nonnull EntityID areaId) {
        return getAgentIdsSelectedHere(areaId)
                .stream()
                .filter(agentId -> worldInfo.getEntity(agentId) instanceof AmbulanceTeam)
                .collect(Collectors.toSet());
    }

    /**
     * 返回选择一个 area 为目标，且 id 比我小的所有 at 的 EntityID
     */
    @Nonnull
    public Set<EntityID> getAtIdsSelectHereWhoseIdLtMe(@Nonnull EntityID areaId) {
        int meRawId = agentInfo.getID().getValue();
        return getAgentIdsSelectedHere(areaId)
                .stream()
                .filter(agentId -> agentId.getValue() < meRawId)
                .filter(agentId -> worldInfo.getEntity(agentId) instanceof AmbulanceTeam)
                .collect(Collectors.toSet());
    }


    /**
     * 返回选择一个 area 为目标，且 id 比我大的所有 at 的 EntityID
     */
    @Nonnull
    public Set<EntityID> getAtIdsSelectHereWhoseIdGtMe(@Nonnull EntityID areaId) {
        int meRawId = agentInfo.getID().getValue();
        return getAgentIdsSelectedHere(areaId)
                .stream()
                .filter(agentId -> agentId.getValue() > meRawId)
                .filter(agentId -> worldInfo.getEntity(agentId) instanceof AmbulanceTeam)
                .collect(Collectors.toSet());
    }

    /**
     * 返回选择一个 area 为目标的所有 fb 的 EntityID
     */
    @Nonnull
    public Set<EntityID> getFbIdsSelectHere(@Nonnull EntityID areaId) {
        return getAgentIdsSelectedHere(areaId)
                .stream()
                .filter(agentId -> worldInfo.getEntity(agentId) instanceof FireBrigade)
                .collect(Collectors.toSet());
    }

    /**
     * 返回选择一个 area 为目标，且 id 比我小的所有 fb 的 EntityID
     */
    @Nonnull
    public Set<EntityID> getFbIdsSelectHereWhoseIdLtMe(@Nonnull EntityID areaId) {
        int meRawId = agentInfo.getID().getValue();
        return getAgentIdsSelectedHere(areaId)
                .stream()
                .filter(agentId -> agentId.getValue() < meRawId)
                .filter(agentId -> worldInfo.getEntity(agentId) instanceof FireBrigade)
                .collect(Collectors.toSet());
    }

    /**
     * 返回选择一个 area 为目标，且 id 比我大的所有 fb 的 EntityID
     */
    @Nonnull
    public Set<EntityID> getFbIdsSelectHereWhoseIdGtMe(@Nonnull EntityID areaId) {
        int meRawId = agentInfo.getID().getValue();
        return getAgentIdsSelectedHere(areaId)
                .stream()
                .filter(agentId -> agentId.getValue() > meRawId)
                .filter(agentId -> worldInfo.getEntity(agentId) instanceof FireBrigade)
                .collect(Collectors.toSet());
    }

    /**
     * 返回选择一个 area 为目标的所有 pf 的 EntityID
     */
    @Nonnull
    public Set<EntityID> getPfIdsSelectHere(@Nonnull EntityID areaId) {
        return getAgentIdsSelectedHere(areaId)
                .stream()
                .filter(agentId -> worldInfo.getEntity(agentId) instanceof PoliceForce)
                .collect(Collectors.toSet());
    }

    /**
     * 返回选择一个 area 为目标，且 id 比我小的所有 pf 的 EntityID
     */
    @Nonnull
    public Set<EntityID> getPfIdsSelectHereWhoseIdLtMe(@Nonnull EntityID areaId) {
        int meRawId = agentInfo.getID().getValue();
        return getAgentIdsSelectedHere(areaId)
                .stream()
                .filter(agentId -> agentId.getValue() < meRawId)
                .filter(agentId -> worldInfo.getEntity(agentId) instanceof PoliceForce)
                .collect(Collectors.toSet());
    }

    /**
     * 返回选择一个 area 为目标，且 id 比我大的所有 pf 的 EntityID
     */
    @Nonnull
    public Set<EntityID> getPfIdsSelectHereWhoseIdGtMe(@Nonnull EntityID areaId) {
        int meRawId = agentInfo.getID().getValue();
        return getAgentIdsSelectedHere(areaId)
                .stream()
                .filter(agentId -> agentId.getValue() > meRawId)
                .filter(agentId -> worldInfo.getEntity(agentId) instanceof PoliceForce)
                .collect(Collectors.toSet());
    }

    /**
     * 查询一个 Agent 的目标
     * @return null - 不知道
     */
    @Nullable
    public EntityID getTargetAreaId(@Nonnull EntityID agentId) {
        AgentTargetAreaRecord record = this.recordMap.get(agentId);
        return record == null
                ? null
                : record.getTargetAreaId();
    }

    /**
     * 返回最新的关于一个人 targetAreaId 的记录的时间
     * <br>
     * 如果不知道这个人的信息，返回 -1
     */
    public int getUpdateTime(@Nonnull EntityID humanId) {
        AgentTargetAreaRecord record = this.recordMap.get(humanId);
        return record == null
                ? -1
                : record.getLastUpdateTime();
    }

    /**
     * 返回最新的关于一个人 targetAreaId 的记录来自哪里
     */
    @Nullable
    public InfoSource getInfoSource(@Nonnull EntityID humanId) {
        AgentTargetAreaRecord record = this.recordMap.get(humanId);
        return record == null
                ? null
                : record.getInfoSource();
    }

    /**
     * 获得以 areaId 为目标的所有 agents
     */
    @Nonnull
    public final Set<EntityID> getAgentIdsSelectedHere(@Nonnull EntityID areaId) {
        return this.agentSelectMap.getOrDefault(areaId, Set.of());
    }

    /**
     * 返回本周期 targetAreaId 改变的所有 Human 的 EntityID
     */
    public final Set<EntityID> getTargetAreaIdChangedAgentIdsInSight() {
        return this.targetAreaIdChangedAgentIdsInSight;
    }

    private void updateTargetArea(
            @Nonnull EntityID humanId,
            @Nullable EntityID targetAreaId,
            int time,
            @Nonnull InfoSource infoSource
    ) {
        // 世界模型里没有这个人，不更新
        Human human = (Human) worldInfo.getEntity(humanId);
        if (human == null)
            return;
        AgentTargetAreaRecord record = recordMap.get(humanId);
        // 还没有记录
        if (record == null) {
            record = new AgentTargetAreaRecord();
            record.setLastUpdateTime(time);
            record.setTargetAreaId(targetAreaId);
            record.setInfoSource(infoSource);
            logger.info("update: humanId=" + human + ", from=" + null + ", to=" + record);
            this.recordMap.put(humanId, record);
        } else
            // 已经有记录了
            // 如果传入的记录更新，则更新之
            if (time > record.getLastUpdateTime()) {
                // 更新本周期视野内 targetAreaId 改变的人的集合
                StringBuilder sb = new StringBuilder();
                sb.append("update: humanId=" + human + ", from=" + record);
                if (Objects.equals(targetAreaId, record.getTargetAreaId()) && infoSource == InfoSource.WITNESS)
                    this.targetAreaIdChangedAgentIdsInSight.add(humanId);
                record.setTargetAreaId(targetAreaId);
                record.setLastUpdateTime(time);
                record.setInfoSource(infoSource);
                sb.append(", to=" + record);
                logger.info(sb);
            }
    }
}
