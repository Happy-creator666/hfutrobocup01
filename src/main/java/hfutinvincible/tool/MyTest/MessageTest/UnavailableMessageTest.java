package hfutinvincible.tool.MyTest.MessageTest;

import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.WorldInfo;
import hfutinvincible.anno.AgentType;
import hfutinvincible.anno.Autowired;
import hfutinvincible.anno.Disabled;
import hfutinvincible.anno.HfutTool;
import hfutinvincible.message.UnavailableMessage;
import hfutinvincible.tool.CalcContext;
import hfutinvincible.tool.LifeCycle;
import hfutinvincible.tool.UnavailableType;
import hfutinvincible.tool.comm.MessageManageTool;
import rescuecore2.standard.entities.FireBrigade;
import rescuecore2.standard.entities.PoliceForce;

@Disabled
@HfutTool
@AgentType({PoliceForce.class})
public class UnavailableMessageTest implements LifeCycle {

    @Autowired
    AgentInfo agentInfo;
    @Autowired
    WorldInfo worldInfo;
    @Autowired
    MessageManageTool messageManageTool;

    @Override
    public void afterCalc(CalcContext context) {
        if(context.getTarget() != null){
            UnavailableMessage unavailableMessage =
                    new UnavailableMessage(true, context.getTarget(), UnavailableType.noBuried);
            messageManageTool.addMessage(unavailableMessage);
        }
    }
}
