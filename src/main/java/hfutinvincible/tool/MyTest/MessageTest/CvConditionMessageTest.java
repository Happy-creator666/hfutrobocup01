package hfutinvincible.tool.MyTest.MessageTest;

import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.WorldInfo;
import hfutinvincible.anno.AgentType;
import hfutinvincible.anno.Autowired;
import hfutinvincible.anno.Disabled;
import hfutinvincible.anno.HfutTool;
import hfutinvincible.message.CvConditionMessage;
import hfutinvincible.tool.CalcContext;
import hfutinvincible.tool.LifeCycle;
import hfutinvincible.tool.comm.MessageManageTool;
import hfutinvincible.tool.human.StuckType;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;

import java.util.Set;

@Disabled
@HfutTool
@AgentType({PoliceForce.class})
public class CvConditionMessageTest implements LifeCycle {

    @Autowired
    AgentInfo agentInfo;
    @Autowired
    WorldInfo worldInfo;
    @Autowired
    MessageManageTool messageManageTool;

    @Override
    public void afterCalc(CalcContext context) {

        System.out.println("00");

        //TODO：怎么获取Civilian？

        Civilian civilian = worldInfo.getAllEntities().stream().filter(Civilian.class::isInstance)
                .map(Civilian.class::cast).findAny().get();
        EntityID cvId = civilian.getID();
        StandardEntity cvPos = worldInfo.getEntity(civilian.getPosition());
        int timeToDead = 100;
        int buriedness = 0;
        StuckType stuckType = StuckType.NOT_STUCK;

        CvConditionMessage cvConditionMessage = new CvConditionMessage(true, cvId, cvPos, timeToDead, buriedness, stuckType);
        messageManageTool.addMessage(cvConditionMessage);
    }
}
