package hfutinvincible.tool.building;

import adf.core.agent.communication.MessageManager;
import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.WorldInfo;
import hfutinvincible.anno.AgentType;
import hfutinvincible.anno.Autowired;
import hfutinvincible.anno.HfutTool;
import hfutinvincible.tool.LifeCycle;
import hfutinvincible.tool.comm.MessageManageTool;
import hfutinvincible.message.BuildingBrokenMessage;
import hfutinvincible.tool.human.InfoSource;
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
public class BuildingBrokennessTool implements LifeCycle {
    @Autowired
    private AgentInfo agentInfo;
    @Autowired
    private WorldInfo worldInfo;

    public final Map<StandardEntity, BuildingBrokennessRecord> recordMap;
    private final Set<EntityID> brokennessChangedBuildingIdsInSight;

    // Tools
    @Autowired
    private MessageManageTool messageTool;

    public BuildingBrokennessTool() {
        this.recordMap = new HashMap<>();
        this.brokennessChangedBuildingIdsInSight = new HashSet<>();
    }

    @Override
    public void updateInfo(MessageManager messageManager) {

        this.messageTool.updateInfo(messageManager);

        int time = agentInfo.getTime();
        Set<EntityID> changedEntities = worldInfo.getChanged().getChangedEntities();

        // 先从视野更新
        this.brokennessChangedBuildingIdsInSight.clear();
        changedEntities.stream()
                .map(worldInfo::getEntity)
                .filter(Building.class::isInstance)
                .map(Building.class::cast)
                .forEach(building -> { // brokenness 没有误差
                    int brokenness = building.isBrokennessDefined() ? building.getBrokenness() : 0;
                    updateBuildingBrokenness(
                            building.getID(),
                            brokenness,
                            time,
                            InfoSource.WITNESS
                    );
                });

        // 然后从消息更新
        messageTool.getReceivedMessages(BuildingBrokenMessage.class)
                .stream()
                .filter(m -> !changedEntities.contains(m.getBuildingId()))
                .forEach(m -> updateBuildingBrokenness(
                        m.getBuildingId(),
                        m.getBuildingBrokenness(),
                        time - 1,
                        InfoSource.FROM_RADIO
                ));
    }

    @Nullable
    public Integer getBuildingBrokenness(@Nonnull StandardEntity building) {
        BuildingBrokennessRecord record = this.recordMap.get(building);
        return record == null
                ? null
                : record.getBrokenness();
    }

    public int getUpdateTime(@Nonnull StandardEntity human) {
        BuildingBrokennessRecord record = this.recordMap.get(human);
        return record == null
                ? -1
                : record.getLastUpdateTime();
    }

    /**
     * 返回最新的关于一个建筑损坏程度的记录来自哪里
     */
    @Nullable
    public InfoSource getInfoSource(@Nonnull StandardEntity human) {
        BuildingBrokennessRecord record = this.recordMap.get(human);
        return record == null
                ? null
                : record.getInfoSource();
    }

    @Nonnull
    public Stream<StandardEntity> getBrokenBuildings(@Nonnull EntityID buildingId) {
        return this.recordMap.entrySet()
                .stream()
                .filter(e -> e.getValue().getBrokenness() > 0)
                .map(Map.Entry::getKey);
    }

    /**
     * 返回本周期我视野范围内损坏程度改变的建筑
     */
    @Nonnull
    public Set<EntityID> getBrokennessChangedBuildingIdsInSight() {
        return brokennessChangedBuildingIdsInSight;
    }

    private void updateBuildingBrokenness(
            @Nonnull EntityID buildingId,
            int brokenness,
            int time,
            @Nonnull InfoSource infoSource
    ) {
        Building building = (Building) worldInfo.getEntity(buildingId);
        if (building == null)
            return;
        BuildingBrokennessRecord record = this.recordMap.get(building);
        if (record == null) {
            record = new BuildingBrokennessRecord();
            record.setLastUpdateTime(time);
            record.setBrokenness(brokenness);
            record.setInfoSource(infoSource);
            this.recordMap.put(building, record);
        } else {
            // 已经有记录了
            // 如果传入的记录更新，则更新之
            if (time > record.getLastUpdateTime()) {
                // 更新本周期视野范围内 brokenness 改变的 building 集合
                if (record.getBrokenness() != brokenness && infoSource == InfoSource.WITNESS)
                    this.brokennessChangedBuildingIdsInSight.add(buildingId);
                record.setLastUpdateTime(time);
                record.setBrokenness(brokenness);
                record.setInfoSource(infoSource);
            }
        }
    }
}