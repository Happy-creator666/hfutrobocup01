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
@AgentType({PoliceForce.class})
public class TargetBlockMessageTest implements LifeCycle {

    @Autowired
    private AgentInfo agentInfo;
    @Autowired
    private WorldInfo worldInfo;
    @Autowired
    private MessageManageTool messageManageTool;

    @Override
    public void afterCalc(CalcContext context) {

        EntityID meId = agentInfo.getID();
        Area meArea = agentInfo.getPositionArea();
        EntityID fromAreaId = meArea.getID();
        EntityID toAreaId = context.getTarget();
        if(toAreaId == null){
            return;
        }
        TargetBlockMessage targetBlockMessage = new TargetBlockMessage(true, meId, fromAreaId, toAreaId);
        messageManageTool.addMessage(targetBlockMessage);
    }
}
