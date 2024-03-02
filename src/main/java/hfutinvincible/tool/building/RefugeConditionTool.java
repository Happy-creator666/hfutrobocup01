package hfutinvincible.tool.building;

import adf.core.agent.communication.MessageManager;
import adf.core.agent.develop.DevelopData;
import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.ScenarioInfo;
import adf.core.agent.info.WorldInfo;
import adf.core.agent.module.ModuleManager;
import hfutinvincible.anno.AgentType;
import hfutinvincible.anno.Autowired;
import hfutinvincible.anno.HfutTool;
import hfutinvincible.message.RefugeConditionMessage;
import hfutinvincible.tool.LifeCycle;
import hfutinvincible.tool.comm.MessageManageTool;
import hfutinvincible.tool.edge.AreaPassableTool;
import hfutinvincible.tool.human.InfoSource;
import org.apache.log4j.Logger;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@HfutTool
@AgentType({AmbulanceTeam.class, PoliceForce.class, FireBrigade.class})
public class RefugeConditionTool implements LifeCycle {
    public final Map<EntityID, RefugeRecord> recordMap;
    private final Set<EntityID> changedRefugeIdsInSight;

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
    MessageManageTool messageTool;
    @Autowired
    AreaPassableTool areaPassableTool;
    public RefugeConditionTool(){
        this.recordMap = new HashMap<>();
        this.changedRefugeIdsInSight = new HashSet<>();
    }

    @Override
    public void updateInfo(MessageManager messageManager) {
        this.messageTool.updateInfo(messageManager);

        int time = agentInfo.getTime();

        // 只有 AT 能看到 Refuge 的详细信息
        this.changedRefugeIdsInSight.clear();
        if (agentInfo.me() instanceof AmbulanceTeam) {
            worldInfo.getEntitiesOfType(StandardEntityURN.REFUGE)
                    .stream()
                    .map(Refuge.class::cast)
                    .forEach(refuge -> {
                        int occupiedBedNum = refuge.getOccupiedBeds();
                        int totalBedNum = refuge.getBedCapacity();
                        int waitingNum = refuge.getWaitingListSize();
                        boolean isFull = occupiedBedNum == totalBedNum;
                        updateRefugeCondition(
                                refuge.getID(),
                                isFull,
                                isFull ? waitingNum : occupiedBedNum,
                                time,
                                InfoSource.WITNESS
                        );
                    });
        }

        // 从消息更新
        messageTool.getReceivedMessages(RefugeConditionMessage.class)
                .forEach(m -> {
                    boolean isFull = m.isFull();
                    updateRefugeCondition(
                            m.getRefugeId(),
                            isFull,
                            isFull ? m.getWaitingNum() : m.getOccupiedNum(),
                            time - 1,
                            InfoSource.FROM_RADIO
                    );
                });
    }
    /**
     * 返回一个 Refuge 是否已经满了
     * @return null 表示不知道
     */
    @Nullable
    public Boolean isFull(@Nonnull EntityID refugeId) {
        Refuge refuge = (Refuge) worldInfo.getEntity(refugeId);
        assert refuge != null;
        var record = this.recordMap.get(refugeId);
        if (record == null)
            return null;
        return record.isFull();
    }

    /**
     * 返回一个 Refuge 被占用的床位数
     * @return null 表示不知道
     */
    @Nullable
    public Integer getOccupiedBedNum(@Nonnull EntityID refugeId) {
        Refuge refuge = (Refuge) worldInfo.getEntity(refugeId);
        assert refuge != null && refuge.isBedCapacityDefined();
        var record = this.recordMap.get(refugeId);
        if (record == null)
            return null;
        return record.isFull()
                ? refuge.getBedCapacity()
                : record.getOccupiedNumOrWaitingNum();
    }

    /**
     * 返回在一个 Refuge 外面排队的人数
     * @return null 表示不知道
     */
    @Nullable
    public Integer getWaitingListNum(@Nonnull EntityID refugeId) {
        Refuge refuge = (Refuge) worldInfo.getEntity(refugeId);
        assert refuge != null && refuge.isBedCapacityDefined();
        var record = this.recordMap.get(refugeId);
        if (record == null)
            return null;
        return record.isFull()
                ? record.getOccupiedNumOrWaitingNum()
                : 0;
    }

    /**
     * 获得一个 refuge 的空床位数
     * @return null 表示不知道
     */
    public Integer getFreeBedsNum(@Nonnull EntityID refugeId) {
        Refuge refuge = (Refuge) worldInfo.getEntity(refugeId);
        assert refuge != null && refuge.isBedCapacityDefined();
        var record = this.recordMap.get(refugeId);
        if (record == null)
            return null;
        return record.isFull()
                ? 0
                : refuge.getBedCapacity() - record.getOccupiedNumOrWaitingNum();
    }

    /**
     * 返回最新的关于一个 Refuge 的记录来自哪里
     */
    @Nullable
    public InfoSource getInfoSource(@Nonnull EntityID humanId) {
        RefugeRecord record = this.recordMap.get(humanId);
        return record == null
                ? null
                : record.getInfoSource();
    }

    /**
     * 返回本周期视野内所有情况改变的 Refuge 的 EntityID
     */
    @Nonnull
    public Set<EntityID> getChangedRefugeIdsInSight() {
        return changedRefugeIdsInSight;
    }

    private void updateRefugeCondition(
            @Nonnull EntityID refugeId,
            boolean isFull,
            int occupiedNumOrWaitingNum,
            int time,
            @Nonnull InfoSource infoSource
    ) {
        // 世界模型里面没有这个 refuge，不更新
        Refuge refuge = (Refuge) worldInfo.getEntity(refugeId);
        if (refuge == null)
            return;
        RefugeRecord record = this.recordMap.get(refugeId);
        // 还没有记录
        if (record == null) {
            record = new RefugeRecord();
            record.setFull(isFull);
            record.setOccupiedNumOrWaitingNum(occupiedNumOrWaitingNum);
            record.setLastUpdateTime(time);
            record.setInfoSource(infoSource);
            this.recordMap.put(refugeId, record);
        } else {
            // 已经有记录了
            if (time > record.getLastUpdateTime()) {
                if (infoSource == InfoSource.WITNESS &&
                        (record.isFull() != isFull || record.getOccupiedNumOrWaitingNum() != occupiedNumOrWaitingNum))
                    this.changedRefugeIdsInSight.add(refugeId);
                record.setFull(isFull);
                record.setOccupiedNumOrWaitingNum(occupiedNumOrWaitingNum);
                record.setLastUpdateTime(time);
                record.setInfoSource(infoSource);
            }
        }
    }
}
