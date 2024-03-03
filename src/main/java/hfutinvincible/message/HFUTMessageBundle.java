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
            AreaEdgePairPassableMessage.class,
            AgentConditionMessage.class,
            CvConditionMessage.class,
            BuildingBrokenMessage.class,
            RefugeConditionMessage.class,
            PackedVoiceMessage.class,
            MessageDummy.class,
            TargetPathMessage.class,
            TargetBlockMessage.class,
            UnavailableMessage.class

    ));

    @Nonnull
    public List<Class<? extends CommunicationMessage>> getMessageClassList() {
        return new ArrayList<>(messageClasses);
    }
}
