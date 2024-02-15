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
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Disabled  // 初步测试没问题
@HfutTool
@AgentType({PoliceForce.class, FireBrigade.class, AmbulanceTeam.class})
public class BuildingBrokenMessageTest implements LifeCycle {

    @Autowired
    AgentInfo agentInfo;
    @Autowired
    WorldInfo worldInfo;
    @Autowired
    MessageManageTool messageManageTool;

    @Override
    public void afterCalc(CalcContext context) {
//        Building building = worldInfo.getAllEntities().stream().filter(Building.class::isInstance)
//                .map(Building.class::cast).findAny().get();
//        EntityID buildingId = building.getID();
//        int brokenness = building.isBrokennessDefined() ? building.getBrokenness() : 0;

        List<Building> buildingList = worldInfo.getAllEntities().stream().filter(Building.class::isInstance)
                .map(Building.class::cast).toList();
        int nextInt = ThreadLocalRandom.current().nextInt(buildingList.size());
        Building building = buildingList.get(nextInt);
        EntityID buildingId = building.getID();


//      int brokenness = building.isBrokennessDefined() ? building.getBrokenness() : 0;
        int brokenness =building.getBrokenness();

        BuildingBrokenMessage buildingBrokenMessage = new BuildingBrokenMessage(true, buildingId, brokenness);
        messageManageTool.addMessage(buildingBrokenMessage);
    }
}
