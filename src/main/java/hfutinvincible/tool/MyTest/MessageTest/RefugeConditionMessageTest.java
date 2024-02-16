package hfutinvincible.tool.MyTest.MessageTest;

import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.WorldInfo;
import hfutinvincible.anno.AgentType;
import hfutinvincible.anno.Autowired;
import hfutinvincible.anno.Disabled;
import hfutinvincible.anno.HfutTool;
import hfutinvincible.message.RefugeConditionMessage;
import hfutinvincible.tool.CalcContext;
import hfutinvincible.tool.LifeCycle;
import hfutinvincible.tool.comm.MessageManageTool;
import rescuecore2.standard.entities.PoliceForce;
import rescuecore2.standard.entities.Refuge;

@Disabled
@HfutTool
@AgentType({PoliceForce.class})
public class RefugeConditionMessageTest implements LifeCycle {

    @Autowired
    private AgentInfo agentInfo;
    @Autowired
    private WorldInfo worldInfo;
    @Autowired
    private MessageManageTool messageManageTool;

    @Override
    public void afterCalc(CalcContext context) {

        Refuge refuge = worldInfo.getAllEntities().stream().filter(Refuge.class::isInstance).map(Refuge.class::cast).findAny().get();
        RefugeConditionMessage refugeConditionMessage = new RefugeConditionMessage(true, refuge);
        messageManageTool.addMessage(refugeConditionMessage);
    }
}
