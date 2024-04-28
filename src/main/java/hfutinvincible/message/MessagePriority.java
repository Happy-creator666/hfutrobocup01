package hfutinvincible.message;

import adf.core.agent.communication.standard.bundle.MessageDummy;
import adf.core.component.communication.CommunicationMessage;

import java.util.Map;

/**
 * 管理消息优先级
 */
public class MessagePriority {

    public static final Map<Class<?>, Integer> priority = Map.of(
            AreaEdgePairPassableMessage.class, 10,
            AgentConditionMessage.class, 1,
            CvConditionMessage.class, 0,
            TargetBlockMessage.class, 3,
            BuildingBrokenMessage.class, 5,
            RefugeConditionMessage.class, 6,
            PackedVoiceMessage.class, 0,
            MessageDummy.class, 11,
            TargetPathMessage.class, 4,
            UnavailableMessage.class, 2
    );

    public static int getPriority(CommunicationMessage message) {
        // TODO SpotMessage 根据 Spot 重要性进一步细分优先级
        return priority.getOrDefault(message.getClass(), 4);
    }
}
