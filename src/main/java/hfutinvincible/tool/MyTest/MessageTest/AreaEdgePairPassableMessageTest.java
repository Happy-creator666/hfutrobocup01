package hfutinvincible.tool.MyTest.MessageTest;


import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.WorldInfo;
import hfutinvincible.anno.AgentType;
import hfutinvincible.anno.Autowired;
import hfutinvincible.anno.Disabled;
import hfutinvincible.anno.HfutTool;
import hfutinvincible.message.AreaEdgePairPassableMessage;
import hfutinvincible.tool.CalcContext;
import hfutinvincible.tool.LifeCycle;
import hfutinvincible.tool.comm.MessageManageTool;
import rescuecore2.standard.entities.AmbulanceTeam;
import rescuecore2.standard.entities.Area;
import rescuecore2.standard.entities.FireBrigade;
import rescuecore2.standard.entities.PoliceForce;
import rescuecore2.worldmodel.EntityID;

import java.util.List;

//@Disabled
@HfutTool
@AgentType({PoliceForce.class, FireBrigade.class, AmbulanceTeam.class})
public class AreaEdgePairPassableMessageTest implements LifeCycle {

    @Autowired
    AgentInfo agentInfo;
    @Autowired
    WorldInfo worldInfo;
    @Autowired
    MessageManageTool messageManageTool;

    @Override
    public void afterCalc(CalcContext context) {
        EntityID agentPosAreaId = agentInfo.getPositionArea().getID();
        Area area = (Area) worldInfo.getEntity(agentPosAreaId);
        messageManageTool.addMessage(new AreaEdgePairPassableMessage(true, area, List.of(true,false,true)));//随便加入的list
    }
}
