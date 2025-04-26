package hfutinvincible.message;

import adf.core.agent.communication.standard.bundle.MessageDummy;
import adf.core.component.communication.CommunicationMessage;

import java.util.Map;

/**
 * 管理消息优先级
 */
public class MessagePriority {

    public static final Map<Class<?>, Integer> priority = Map.ofEntries(
            Map.entry(AreaEdgePairPassableMessage.class, 10),
            Map.entry(AgentConditionMessage.class, 1),
            Map.entry(CvConditionMessage.class, 0),
            Map.entry(TargetBlockMessage.class, 3),
            Map.entry(BuildingBrokenMessage.class, 5),
            Map.entry(RefugeConditionMessage.class, 6),
            Map.entry(PackedVoiceMessage.class, 0),
            Map.entry(MessageDummy.class, 11),
            Map.entry(TargetPathMessage.class, 4),
            Map.entry(UnavailableMessage.class, 2),
            Map.entry(AgentPosAreaMessage.class, 2)
    );

    public static int getPriority(CommunicationMessage message) {
        // TODO SpotMessage 根据 Spot 重要性进一步细分优先级
        return priority.getOrDefault(message.getClass(), 4);
    }
}
