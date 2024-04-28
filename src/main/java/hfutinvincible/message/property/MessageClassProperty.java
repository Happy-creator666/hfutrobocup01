package hfutinvincible.message.property;

import adf.core.component.communication.CommunicationMessage;
import adf.core.component.communication.util.BitStreamReader;
import hfutinvincible.common.BitArray;
import hfutinvincible.tool.comm.PropertyManageTool;
import hfutinvincible.tool.comm.DecodingException;
import hfutinvincible.tool.comm.EncodingException;
import hfutinvincible.message.*;

import javax.annotation.Nonnull;
import java.util.*;

public class MessageClassProperty {

    private List<BaseMessage> messages;

    /**
     *  scenarioInfo.getVoiceMessagesSize()获取maxSize
     */
    private int maxSize;

    public List<BaseMessage> getMessages() {
        return messages;
    }

    public void setMessages(List<BaseMessage> messages) {
        this.messages = messages;
    }

    public int getMaxSize() {
        return maxSize;
    }

    public void setMaxSize(int maxSize) {
        this.maxSize = maxSize;
    }

    public void setMessageClass(Class<? extends BaseMessage> messageClass) {
        this.messageClass = messageClass;
    }

    private Class<? extends BaseMessage> messageClass;
    public static final int BIT_SIZE = 4;

    public static final Map<Class<? extends BaseMessage>, Integer> MESSAGE_CLASS_TO_INDEX;
    public static final Map<Integer, Class<? extends BaseMessage>> INDEX_TO_MESSAGE_CLASS;

    static {
        MESSAGE_CLASS_TO_INDEX = new HashMap<>();
        INDEX_TO_MESSAGE_CLASS = new HashMap<>();
        int i = 0;
        for (Class<? extends CommunicationMessage> messageClass : HFUTMessageBundle.messageClasses) {
            if (!BaseMessage.class.isAssignableFrom(messageClass))
                continue;
            MESSAGE_CLASS_TO_INDEX.put((Class<? extends BaseMessage>) messageClass, i);
            INDEX_TO_MESSAGE_CLASS.put(i, (Class<? extends BaseMessage>) messageClass);
            i ++;
        }
    }

    public MessageClassProperty( int maxSize, @Nonnull List<BaseMessage> messages) {
        this.maxSize = maxSize;
        this.messages = messages;
    }

    public MessageClassProperty(int messageClassIndex) {
        this.messageClass = INDEX_TO_MESSAGE_CLASS.get(messageClassIndex);
    }

    public Class<? extends BaseMessage> getMessageClass() {
        return messageClass;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MessageClassProperty that = (MessageClassProperty) o;
        return Objects.equals(messageClass, that.messageClass);
    }

    @Override
    public int hashCode() {
        return messageClass != null ? messageClass.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "[messages：" + getMessages().toString() + "]";
    }
}