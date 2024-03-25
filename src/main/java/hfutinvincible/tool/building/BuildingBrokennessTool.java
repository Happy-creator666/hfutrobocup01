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
import hfutinvincible.message.BuildingBrokenMessage;
import hfutinvincible.tool.LifeCycle;
import hfutinvincible.tool.comm.MessageManageTool;
import hfutinvincible.tool.human.InfoSource;
import org.apache.log4j.Logger;
import rescuecore2.standard.entities.AmbulanceTeam;
import rescuecore2.standard.entities.Building;
import rescuecore2.standard.entities.FireBrigade;
import rescuecore2.standard.entities.PoliceForce;
import rescuecore2.worldmodel.EntityID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

@HfutTool
@AgentType({AmbulanceTeam.class, PoliceForce.class, FireBrigade.class})
public class BuildingBrokennessTool implements LifeCycle {
    public final Map<EntityID, BuildingBrokennessRecord> recordMap;
    private final Set<EntityID> brokennessChangedBuildingIdsInSight;

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

    public BuildingBrokennessTool(){
        this.recordMap = new HashMap<>();
        this.brokennessChangedBuildingIdsInSight = new HashSet<>();
    }

    @Override
    public void updateInfo(MessageManager messageManager) {
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
    public Integer getBuildingBrokenness(@Nonnull EntityID buildingId) {
        BuildingBrokennessRecord record = this.recordMap.get(buildingId);
        return record == null
                ? null
                : record.getBrokenness();
    }

    public int getUpdateTime(@Nonnull EntityID humanId) {
        BuildingBrokennessRecord record = this.recordMap.get(humanId);
        return record == null
                ? -1
                : record.getLastUpdateTime();
    }

    /**
     * 返回最新的关于一个建筑损坏程度的记录来自哪里
     */
    @Nullable
    public InfoSource getInfoSource(@Nonnull EntityID humanId) {
        BuildingBrokennessRecord record = this.recordMap.get(humanId);
        return record == null
                ? null
                : record.getInfoSource();
    }

    @Nonnull
    public Stream<EntityID> getBrokenBuildings(@Nonnull EntityID buildingId) {
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
        BuildingBrokennessRecord record = this.recordMap.get(buildingId);
        if (record == null) {
            record = new BuildingBrokennessRecord();
            record.setLastUpdateTime(time);
            record.setBrokenness(brokenness);
            record.setInfoSource(infoSource);
            this.recordMap.put(buildingId, record);
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
