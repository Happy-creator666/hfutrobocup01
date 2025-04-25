package hfutinvincible.message;

import adf.core.agent.communication.standard.bundle.MessageDummy;
import adf.core.component.communication.CommunicationMessage;
import adf.core.component.communication.MessageBundle;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

public class HFUTMessageBundle extends MessageBundle {

    public HFUTMessageBundle() {}

    public static final List<Class<? extends CommunicationMessage>> messageClasses = new ArrayList<>(List.of(
            AgentConditionMessage.class,
            AgentPosAreaMessage.class,
            AreaEdgePairPassableMessage.class,
            BaseMessage.class,
            BuildingBrokenMessage.class,
            CvConditionMessage.class,
            PackedVoiceMessage.class,
            RefugeConditionMessage.class,
            TargetBlockMessage.class,
            TargetPathMessage.class,
            MessageDummy.class,
            UnavailableMessage.class

    ));

    @Nonnull
    public List<Class<? extends CommunicationMessage>> getMessageClassList() {
        return new ArrayList<>(messageClasses);
    }
}
