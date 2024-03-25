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
import hfutinvincible.message.TargetPathMessage;
import hfutinvincible.tool.CalcContext;
import hfutinvincible.tool.LifeCycle;
import hfutinvincible.tool.comm.MessageManageTool;
import org.apache.log4j.Logger;
import rescuecore2.standard.entities.AmbulanceTeam;
import rescuecore2.standard.entities.FireBrigade;
import rescuecore2.standard.entities.Human;
import rescuecore2.standard.entities.PoliceForce;
import rescuecore2.worldmodel.EntityID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

@HfutTool
@AgentType({AmbulanceTeam.class, FireBrigade.class, PoliceForce.class})
public class AgentTargetPathTool implements LifeCycle {
    public Map<EntityID, AgentTargetPathRecord> recordMap;

    public Set<EntityID> areasOnOtherPfCleanPaths;

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
    @Autowired
    MessageManageTool messageManageTool;

    public AgentTargetPathTool(){
        this.recordMap = new HashMap<>();
        this.areasOnOtherPfCleanPaths = new HashSet<>();
    }

    @Override
    public void updateInfo(MessageManager messageManager) {
        int time = agentInfo.getTime();
        messageManageTool.getReceivedMessages(TargetPathMessage.class)
                .stream()
                .filter(message -> !message.getAgentId().equals(agentInfo.getID()))//排除我自己
                .forEach(message -> updateTargetPath(message.getAgentId(), message.getPathIds(), time - 1, InfoSource.FROM_VOICE));
    }

    @Override
    public void afterCalc(CalcContext context) {
        //根据我这个周期的决策进行更新
        int time = agentInfo.getTime();
        List<EntityID> resultPath = context.getTargetPath();
        updateTargetPath(agentInfo.getID(), resultPath, time, InfoSource.MY_SELF);
    }


    /**
     * 获得一个 agent 的目标移动路径
     */
    @Nullable
    public List<EntityID> getTargetPath(@Nonnull EntityID agentId) {
        AgentTargetPathRecord record = recordMap.get(agentId);
        if (record == null)
            return null;
        return record.getTargetPath();
    }

    /**
     * 返回最新的关于一个人 targetPath 的记录的时间
     * <br>
     * 如果不知道这个人的信息，返回 -1
     */
    public int getUpdateTime(@Nonnull EntityID humanId) {
        AgentTargetPathRecord record = this.recordMap.get(humanId);
        return record == null
                ? -1
                : record.getLastUpdateTime();
    }

    /**
     * 返回最新的关于一个人 targetPath 的记录来自哪里
     */
    @Nullable
    public InfoSource getInfoSource(@Nonnull EntityID humanId) {
        AgentTargetPathRecord record = this.recordMap.get(humanId);
        return record == null
                ? null
                : record.getInfoSource();
    }

    /**
     * 返回我知道的所有位于别的 pf 的清理路径上的 areas
     */
    @Nonnull
    public Set<EntityID> getAreasOnOtherPfCleanPaths() {
        return areasOnOtherPfCleanPaths;
    }

    private void updateTargetPath(
            @Nonnull EntityID humanId,
            @Nullable List<EntityID> targetPath,
            int time,
            @Nonnull InfoSource infoSource
    ) {
        Human human = (Human) worldInfo.getEntity(humanId);
        if (human == null)
            return;
        AgentTargetPathRecord record = recordMap.get(humanId);
        // 还没有记录
        if (record == null) {
            record = new AgentTargetPathRecord();
            record.setLastUpdateTime(time);
            record.setTargetPath(targetPath);
            record.setInfoSource(infoSource);
            // 更新 areasOnOtherPfCleanPaths
            if (targetPath != null && !humanId.equals(agentInfo.getID()))
                areasOnOtherPfCleanPaths.addAll(targetPath);
            logger.info("update: humanId=" + human + ", from=" + null + ", to=" + record);
            recordMap.put(humanId, record);
        } else
            // 已经有记录了
            // 如果传入的记录更新，则更新之
            if (time > record.getLastUpdateTime()) {
                StringBuilder sb = new StringBuilder();
                sb.append("update: humanId=" + human + ", from=" + record);
                // 更新 areasOnOtherPfCleanPaths
                if (targetPath != null && !humanId.equals(agentInfo.getID())) {
                    List<EntityID> oldTargetPath = record.getTargetPath();
                    if (oldTargetPath != null)
                        oldTargetPath.forEach(areasOnOtherPfCleanPaths::remove);
                    areasOnOtherPfCleanPaths.addAll(targetPath);
                }
                record.setTargetPath(targetPath);
                record.setLastUpdateTime(time);
                record.setInfoSource(infoSource);
                sb.append(", to=" + record);
                logger.info(sb);
            }
    }
}
