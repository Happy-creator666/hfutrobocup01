package hfutinvincible.tool.human;

import adf.core.agent.communication.MessageManager;
import adf.core.agent.develop.DevelopData;
import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.ScenarioInfo;
import adf.core.agent.info.WorldInfo;
import adf.core.agent.module.ModuleManager;
import hfutinvincible.anno.After;
import hfutinvincible.anno.AgentType;
import hfutinvincible.anno.Autowired;
import hfutinvincible.anno.HfutTool;
import hfutinvincible.message.AgentConditionMessage;
import hfutinvincible.message.CvConditionMessage;
import hfutinvincible.tool.LifeCycle;
import hfutinvincible.tool.comm.MessageManageTool;
import org.apache.log4j.Logger;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

@HfutTool
@AgentType({AmbulanceTeam.class, FireBrigade.class, PoliceForce.class})
public class BuriednessTool implements LifeCycle {
    public final Map<EntityID, BuriednessRecord> recordMap;

    /**
     * 视野范围内掩埋深度改变了的 Human 的 EntityIDs
     */
    private final Set<EntityID> buriednessChangedCvIdsInSight;

    /**
     * 因为被埋导致无法 load 的 cvs（只有 at 使用），每周期清空
     */
    private final Set<EntityID> buriedTargetCvs;

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

    //Tools
    @Autowired
    MessageManageTool messageManageTool;
    @Autowired
    HumanPositionTool humanPositionTool;

    public BuriednessTool(){
        this.recordMap = new HashMap<>();
        this.buriednessChangedCvIdsInSight = new HashSet<>();
        this.buriedTargetCvs = new HashSet<>();
    }

    @Override
    @After({"HumanPositionTool","MessageManageTool"})
    public void updateInfo(MessageManager messageManager) {
        int time = agentInfo.getTime();
        Set<EntityID> changedEntities = worldInfo.getChanged().getChangedEntities();

        this.buriednessChangedCvIdsInSight.clear();
        // 先从视野更新
        changedEntities.stream()
                .map(worldInfo::getEntity)
                .filter(Human.class::isInstance)
                .map(Human.class::cast)
                .forEach(human -> { // buriedness 没有误差，看到的可以直接采信
                    int buriedness = human.isBuriednessDefined() ? human.getBuriedness() : 0;
                    updateBuriedness(
                            human instanceof Civilian,
                            human.getID(),
                            buriedness,
                            time,
                            InfoSource.WITNESS
                    );
                });
        messageManageTool.getReceivedMessages(AgentConditionMessage.class)
                .forEach(m -> updateBuriedness(
                        false,
                        m.getAgentId(),
                        m.getBuriedness(),
                        time - 1,
                        InfoSource.FROM_RADIO
                ));
        messageManageTool.getReceivedMessages(CvConditionMessage.class)
                .forEach(m -> updateBuriedness(
                        true,
                        m.getCvId(),
                        m.getBuriedness(),
                        time - 1,
                        InfoSource.FROM_RADIO
                ));

        buriedTargetCvs.clear();
    }
    /**
     * 添加一个被埋导致无法 load 的目标 cv（只有 at 使用）
     */
    public void addBuriedTargetCv(@Nonnull EntityID buriedCv) {
        buriedTargetCvs.add(buriedCv);
    }

    /**
     * 返回所有被埋导致无法 load 的目标 humans（只有 at 使用）
     */
    @Nonnull
    public Set<EntityID> getBuriedTargetCvs() {
        return buriedTargetCvs;
    }

    /**
     * 查询一个人的掩埋深度
     * @return null - 不知道
     */
    @Nullable
    public Integer getBuriedness(@Nonnull EntityID humanId) {
        BuriednessRecord record = this.recordMap.get(humanId);
        return record == null
                ? null
                : record.getBuriedness();
    }

    /**
     * 返回最新的关于一个人 buriedness 的记录的时间
     * <br>
     * 如果不知道这个人的信息，返回 -1
     */
    public int getUpdateTime(@Nonnull EntityID humanId) {
        BuriednessRecord record = this.recordMap.get(humanId);
        return record == null
                ? -1
                : record.getLastUpdateTime();
    }

    /**
     * 返回最新的关于一个人 buriedness 的记录来自哪里
     */
    @Nullable
    public InfoSource getInfoSource(@Nonnull EntityID humanId) {
        BuriednessRecord record = this.recordMap.get(humanId);
        return record == null
                ? null
                : record.getInfoSource();
    }

    /**
     * 返回本周期所有被埋的人（知道其 buriedness，且数值大于 0）
     */
    @Nonnull
    public Stream<EntityID> getBuriedHumanIds() {
        return this.recordMap.entrySet()
                .stream()
                .filter(e -> e.getValue().getBuriedness() > 0)
                .map(Map.Entry::getKey);
    }

    /**
     * 返回本周期 buriedness 改变的所有 Human 的 EntityID
     */
    @Nonnull
    public final Set<EntityID> getBuriednessChangedCvIdsInSight() {
        return buriednessChangedCvIdsInSight;
    }

    private void updateBuriedness(
            boolean isCivilian,
            @Nonnull EntityID humanId,
            int buriedness,
            int time,
            @Nonnull InfoSource infoSource
    ) {
        // 世界模型里没有这个人，不更新
        Human human = (Human) worldInfo.getEntity(humanId);
        if (human == null)
            return;
        BuriednessRecord record = this.recordMap.get(humanId);
        // 还没有记录
        if (record == null) {
            record = new BuriednessRecord();
            record.setLastUpdateTime(time);
            record.setBuriedness(buriedness);
            record.setInfoSource(infoSource);
            this.recordMap.put(humanId, record);
            human.setBuriedness(buriedness); // 更新世界模型
            logger.info("update: humanId=" + human + ", from=" + null + ", to=" + record);
        } else
            // 已经有记录了
            // 如果传入的记录更新，则更新之
            if (time > record.getLastUpdateTime()) {
                StringBuilder sb = new StringBuilder();
                sb.append("update: humanId=" + human + ", from=" + record);
                // 更新本周期视野内 buriedness 改变的 Cv 的集合
                if (isCivilian && record.getBuriedness() != buriedness && infoSource == InfoSource.WITNESS)
                    this.buriednessChangedCvIdsInSight.add(humanId);
                record.setBuriedness(buriedness);
                record.setLastUpdateTime(time);
                record.setInfoSource(infoSource);
                human.setBuriedness(buriedness); // 更新世界模型
                sb.append(", to=" + record);
                logger.info(sb);
            }
    }
}
