package hfutinvincible.tool.MyTest.MessageTest;

import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.WorldInfo;
import hfutinvincible.anno.AgentType;
import hfutinvincible.anno.Autowired;
import hfutinvincible.anno.Disabled;
import hfutinvincible.anno.HfutTool;
import hfutinvincible.message.TargetPathMessage;
import hfutinvincible.tool.CalcContext;
import hfutinvincible.tool.LifeCycle;
import hfutinvincible.tool.comm.MessageManageTool;
import rescuecore2.standard.entities.PoliceForce;
import rescuecore2.worldmodel.EntityID;

import java.util.List;

@Disabled
@HfutTool
@AgentType({PoliceForce.class})
public class TargetPathMessageTest implements LifeCycle {

    @Autowired
    private AgentInfo agentInfo;
    @Autowired
    private WorldInfo worldInfo;
    @Autowired
    private MessageManageTool messageManageTool;

    @Override
    public void afterCalc(CalcContext context) {

        EntityID meId = agentInfo.getID();
        EntityID target = context.getTarget();
        if(target == null){
            return;
        }
        //测试路径
        List<EntityID> pathIds = List.of(agentInfo.getPositionArea().getID(), target, new EntityID(255));
        TargetPathMessage targetPathMessage = new TargetPathMessage(meId, pathIds);
        messageManageTool.addMessage(targetPathMessage);
    }
}
