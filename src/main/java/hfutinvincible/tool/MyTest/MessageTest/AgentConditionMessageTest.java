package hfutinvincible.tool.MyTest.MessageTest;

import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.WorldInfo;
import hfutinvincible.anno.AgentType;
import hfutinvincible.anno.Autowired;
import hfutinvincible.anno.Disabled;
import hfutinvincible.anno.HfutTool;
import hfutinvincible.message.AgentConditionMessage;
import hfutinvincible.tool.CalcContext;
import hfutinvincible.tool.LifeCycle;
import hfutinvincible.tool.comm.MessageManageTool;
import hfutinvincible.tool.human.StuckType;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;

@Disabled // 初步测试没问题
@HfutTool
@AgentType({PoliceForce.class, FireBrigade.class, AmbulanceTeam.class})
public class AgentConditionMessageTest implements LifeCycle {

    @Autowired
    AgentInfo agentInfo;
    @Autowired
    WorldInfo worldInfo;
    @Autowired
    MessageManageTool messageManageTool;

    @Override
    public void afterCalc(CalcContext context) {

        EntityID agentId = agentInfo.getID();
        StandardEntity mePos = worldInfo.getEntity(agentInfo.getPosition());

        int timeToDead = 100; //假定还能存活100周期
        int buriedness = 0; //警察掩埋设为0
        StuckType stuckType = StuckType.NOT_STUCK;
        //假定目标为266Area
        EntityID targetAreaId = new EntityID(266);

        AgentConditionMessage agentConditionMessage = new AgentConditionMessage(agentId, true, mePos,
                timeToDead, buriedness, stuckType, targetAreaId);
        messageManageTool.addMessage(agentConditionMessage);

    }
}
