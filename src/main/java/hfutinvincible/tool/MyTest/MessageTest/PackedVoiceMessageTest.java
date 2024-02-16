package hfutinvincible.tool.MyTest.MessageTest;

import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.ScenarioInfo;
import adf.core.agent.info.WorldInfo;
import hfutinvincible.anno.AgentType;
import hfutinvincible.anno.Autowired;
import hfutinvincible.anno.Disabled;
import hfutinvincible.anno.HfutTool;
import hfutinvincible.message.*;
import hfutinvincible.tool.CalcContext;
import hfutinvincible.tool.LifeCycle;
import hfutinvincible.tool.UnavailableType;
import hfutinvincible.tool.comm.MessageManageTool;
import hfutinvincible.tool.human.StuckType;
import rescuecore2.standard.entities.Area;
import rescuecore2.standard.entities.PoliceForce;
import rescuecore2.worldmodel.EntityID;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Disabled
@HfutTool
@AgentType({PoliceForce.class})
public class PackedVoiceMessageTest implements LifeCycle {
    @Autowired
    private AgentInfo agentInfo;
    @Autowired
    private WorldInfo worldInfo;
    @Autowired
    private ScenarioInfo scenarioInfo;
    @Autowired
    private MessageManageTool messageManageTool;

    private final List<BaseMessage> messagesToSendByVoice = new ArrayList<>();

    @Override
    public void afterCalc(CalcContext context) {
        //清空
        messagesToSendByVoice.clear();

        EntityID targetId = context.getTarget();
        if(targetId == null){
            return;
        }
        UnavailableMessage voiceMessage0 = new UnavailableMessage(false, targetId, UnavailableType.noBuried);
        messagesToSendByVoice.add(voiceMessage0);

        EntityID meId = agentInfo.getID();
        Area positionArea = agentInfo.getPositionArea();
        EntityID mePosId = positionArea.getID();
        TargetBlockMessage voiceMessage1 = new TargetBlockMessage(false, meId, mePosId, targetId);
        messagesToSendByVoice.add(voiceMessage1);

        AgentConditionMessage voiceMessage2 = new AgentConditionMessage(
                meId,
                false,
                Objects.requireNonNull(worldInfo.getEntity(mePosId)),
                100,
                0,
                StuckType.NOT_STUCK,
                targetId);
        messagesToSendByVoice.add(voiceMessage2);

        PackedVoiceMessage packedVoiceMessage = new PackedVoiceMessage(
                false, scenarioInfo.getVoiceMessagesSize(), messagesToSendByVoice);
        messageManageTool.addMessage(packedVoiceMessage);
    }

}
