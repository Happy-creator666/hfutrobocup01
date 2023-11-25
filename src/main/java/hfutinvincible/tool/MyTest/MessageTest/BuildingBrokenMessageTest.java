package hfutinvincible.tool.MyTest.MessageTest;

import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.WorldInfo;
import hfutinvincible.anno.AgentType;
import hfutinvincible.anno.Autowired;
import hfutinvincible.anno.Disabled;
import hfutinvincible.anno.HfutTool;
import hfutinvincible.message.BuildingBrokenMessage;
import hfutinvincible.tool.CalcContext;
import hfutinvincible.tool.LifeCycle;
import hfutinvincible.tool.comm.MessageManageTool;
import rescuecore2.standard.entities.Building;
import rescuecore2.standard.entities.PoliceForce;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.worldmodel.EntityID;

@Disabled
@HfutTool
@AgentType({PoliceForce.class})
public class BuildingBrokenMessageTest implements LifeCycle {

    @Autowired
    AgentInfo agentInfo;
    @Autowired
    WorldInfo worldInfo;
    @Autowired
    MessageManageTool messageManageTool;

    @Override
    public void afterCalc(CalcContext context) {

        Building building = worldInfo.getAllEntities().stream().filter(Building.class::isInstance)
                .map(Building.class::cast).findAny().get();
        EntityID buildingId = building.getID();
        int brokenness = building.isBrokennessDefined() ? building.getBrokenness() : 0;
        BuildingBrokenMessage buildingBrokenMessage = new BuildingBrokenMessage(true, buildingId, brokenness);
        messageManageTool.addMessage(buildingBrokenMessage);
    }
}
