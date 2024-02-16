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

import java.util.List;
import java.util.Objects;
import java.util.Set;

@Disabled
@HfutTool
@AgentType({PoliceForce.class})
public class CvConditionMessageTest implements LifeCycle {

    @Autowired
    private AgentInfo agentInfo;
    @Autowired
    private WorldInfo worldInfo;
    @Autowired
    private MessageManageTool messageManageTool;

    @Override
    public void afterCalc(CalcContext context) {
        // TODO：暂时无法获取Civilian,需要视野内遇到添加到世界模型中？

        /*Human human = (Human) worldInfo.getEntity(humanId);
        // Agent 应该我都知道，如果 human 为 null，只可能是 cv
        // 创建这个 Civilian 并将其加入世界模型
        if (human == null) {
            human = new Civilian(humanId);
            worldInfo.addEntity(human);
        }
        CvConditionMessage cvConditionMessage = new CvConditionMessage(true, cvId, cvPos, timeToDead, buriedness, stuckType);
        messageManageTool.addMessage(cvConditionMessage);*/

        /*//从地图中找到一个Civilian，并添加到世界模型中
        Human human = new Civilian(new EntityID(1557027963));
        worldInfo.addEntity(human);
        StandardEntity cvPos = worldInfo.getEntity(new EntityID(254));
        CvConditionMessage cvConditionMessage = new CvConditionMessage(true, human.getID(), cvPos, 100, 0, StuckType.NOT_STUCK);
        messageManageTool.addMessage(cvConditionMessage);*/

    }
}
