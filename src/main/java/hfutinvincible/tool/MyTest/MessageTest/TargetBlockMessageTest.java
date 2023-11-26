package hfutinvincible.tool.MyTest.MessageTest;

import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.WorldInfo;
import hfutinvincible.anno.AgentType;
import hfutinvincible.anno.Autowired;
import hfutinvincible.anno.Disabled;
import hfutinvincible.anno.HfutTool;
import hfutinvincible.message.TargetBlockMessage;
import hfutinvincible.tool.CalcContext;
import hfutinvincible.tool.LifeCycle;
import hfutinvincible.tool.comm.MessageManageTool;
import rescuecore2.standard.entities.AmbulanceTeam;
import rescuecore2.standard.entities.Area;
import rescuecore2.standard.entities.FireBrigade;
import rescuecore2.standard.entities.PoliceForce;
import rescuecore2.worldmodel.EntityID;

@Disabled
@HfutTool
@AgentType({FireBrigade.class, AmbulanceTeam.class})
public class TargetBlockMessageTest implements LifeCycle {

    @Autowired
    AgentInfo agentInfo;
    @Autowired
    WorldInfo worldInfo;
    @Autowired
    MessageManageTool messageManageTool;

    @Override
    public void afterCalc(CalcContext context) {

        EntityID meId = agentInfo.getID();
        Area meArea = agentInfo.getPositionArea();
        EntityID fromAreaId = meArea.getID();
        EntityID toAreaId = meArea.getNeighbours().stream().findAny().get();

        TargetBlockMessage targetBlockMessage =new TargetBlockMessage(true, meId, fromAreaId, toAreaId);
        messageManageTool.addMessage(targetBlockMessage);
    }
}
