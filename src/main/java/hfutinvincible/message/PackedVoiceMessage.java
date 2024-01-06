package hfutinvincible.message;

import adf.core.component.communication.util.BitStreamReader;
import hfutinvincible.anno.HfutMessage;
import hfutinvincible.anno.MessageField;
import hfutinvincible.anno.MessageFieldType;
import hfutinvincible.common.BitArray;
import hfutinvincible.debug.TestLogger;
import hfutinvincible.tool.comm.PropertyManageTool;
import hfutinvincible.tool.comm.DecodingException;
import hfutinvincible.tool.comm.EncodingException;
import hfutinvincible.message.property.MessageClassProperty;
import org.apache.log4j.Logger;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

@HfutMessage
public class PackedVoiceMessage extends BaseMessage {

    @MessageField(type = MessageFieldType.MESSAGE_CLASS_PROPERTY)
    private MessageClassProperty messageClassProperty;

    private Logger logger = TestLogger.getLogger("PKM");

    public PackedVoiceMessage(boolean isRadio, int maxSize, @Nonnull List<BaseMessage> messages) {
        super(isRadio);
        this.messageClassProperty = new MessageClassProperty(maxSize, messages);
    }

    public PackedVoiceMessage(boolean isRadio, int rawSenderID, int ttl, BitStreamReader bsr) {
        super(isRadio, rawSenderID, ttl, bsr);
    }

    public List<BaseMessage> getMessages() {
        return this.messageClassProperty.getMessages();
    }

    @Nonnull
    @Override
    public String getCheckKey() {
        return "PKM" + getMessages();
    }
}
