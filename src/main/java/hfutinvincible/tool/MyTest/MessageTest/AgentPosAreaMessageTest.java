package hfutinvincible.tool.MyTest.MessageTest;

import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.WorldInfo;
import hfutinvincible.anno.AgentType;
import hfutinvincible.anno.Autowired;
import hfutinvincible.anno.Disabled;
import hfutinvincible.anno.HfutTool;
import hfutinvincible.message.AgentPosAreaMessage;
import hfutinvincible.tool.CalcContext;
import hfutinvincible.tool.LifeCycle;
import hfutinvincible.tool.comm.MessageManageTool;
import rescuecore2.standard.entities.PoliceForce;
import rescuecore2.worldmodel.EntityID;

@Disabled
@HfutTool
@AgentType({PoliceForce.class})
public class AgentPosAreaMessageTest implements LifeCycle {

    @Autowired
    AgentInfo agentInfo;
    @Autowired
    WorldInfo worldInfo;
    @Autowired
    MessageManageTool messageManageTool;

    @Override
    public void afterCalc(CalcContext context) {

        EntityID agentId = agentInfo.getID();
        EntityID agentPosAreaId = agentInfo.getPositionArea().getID();

        AgentPosAreaMessage agentPosAreaMessage = new AgentPosAreaMessage(true, agentId, agentPosAreaId);
        messageManageTool.addMessage(agentPosAreaMessage);
    }
}
