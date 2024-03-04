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
import hfutinvincible.tool.LifeCycle;
import hfutinvincible.tool.misc.OptionsTool;
import org.apache.log4j.Logger;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

@HfutTool
@AgentType({AmbulanceTeam.class, PoliceForce.class, FireBrigade.class})
public class HelpOuchTool implements LifeCycle {
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

    private final Map<EntityID, Integer> helpOuchCountMap;

    //Tools
    @Autowired
    HumanPositionTool humanPositionTool;

    public HelpOuchTool(){
        this.helpOuchCountMap = new HashMap<>();
    }

    @Override
    public void updateInfo(MessageManager messageManager) {
        humanPositionTool.updateInfo(messageManager);

        // 我听到的别人的求救数
        int helpOuchCount = messageManager.getHeardAgentHelpCount();

        // 以我为圆心，听力范围为半径内的所有 area 都有可能是发出求救信号的人所在地点
        int myPosX = (int) agentInfo.getX();
        int myPosY = (int) agentInfo.getY();
        Collection<StandardEntity> goodEntities = worldInfo.getObjectsInRange(myPosX, myPosY, OptionsTool.HEARING_RANGE);

        for (StandardEntity goodEntity : goodEntities) {
            if (!(goodEntity instanceof Area goodArea))
                continue;
            helpOuchCountMap.compute(goodArea.getID(), (k, v) -> v == null ? helpOuchCount : v + helpOuchCount);
        }
    }
    public int getHelpOuchCount(@Nonnull EntityID areaId) {
        return helpOuchCountMap.getOrDefault(areaId, 0);
    }
}
