package hfutinvincible.message;

import adf.core.agent.communication.standard.bundle.StandardMessage;
import adf.core.agent.communication.standard.bundle.StandardMessagePriority;
import adf.core.component.communication.util.BitOutputStream;
import adf.core.component.communication.util.BitStreamReader;
import hfutinvincible.anno.MessageField;
import hfutinvincible.anno.MessageFieldType;
import hfutinvincible.common.BitArray;
import hfutinvincible.message.property.AreaEdgePairPassableProperty;
import hfutinvincible.message.property.MessageClassProperty;
import hfutinvincible.message.property.PathIdsProperty;
import hfutinvincible.message.property.PositionIdProperty;
import hfutinvincible.tool.UnavailableType;
import hfutinvincible.tool.comm.DecodingException;
import hfutinvincible.tool.comm.EncoderDecoderTool;
import hfutinvincible.tool.comm.EncodingException;
import hfutinvincible.tool.human.StuckType;
import rescuecore2.worldmodel.EntityID;

import javax.annotation.Nonnull;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public abstract class BaseMessage extends StandardMessage {

    private BitArray data;

    // 如果算出了 bytes，就存下来，防止重复计算
    private byte[] bytesCache;

    /**
     * <p><b>子类应该重写此方法！！！</b></p>
     * <p>这个构造方法在发送消息时用来构造一条要发送的消息。</p>
     * <p>根据官方的设计，消息编码逻辑应该被放在这里。也就是此函数的原本作用是
     * 在构造消息的同时，完成消息的编码，将二进制表示的消息存入一个成员变量里面。
     * 但是我们需要自己控制解码逻辑，因此我们选择不在此处完成编码，而是之后再找个时间完成编码。</p>
     * @param isRadio 是否通过无线电（否则通过声音）发送此消息
     */
    protected BaseMessage(boolean isRadio) {
        super(isRadio, StandardMessagePriority.NORMAL);
    }

    /**
     * <p><b>此方法将被反射调用，也仅供反射调用，你永远不应该调用此方法！！！</b></p>
     * <p>根据官方的设计，消息解码逻辑应该被放在这里。也就是此函数的原本作用是
     * 从 {@code BitStreamReader bsr} 中得到消息的所有 bits，然后完成解码。
     * 但是，我们需要自己控制解码逻辑，因此我们选择不在此处完成解码，而是先把
     * {@code BitStreamReader bsr} 中的数据存到 {@link BitArray} 里，之后
     * 再找个时机完成解码。</p>
     */
    protected BaseMessage(boolean isRadio, int rawSenderID, int ttl, BitStreamReader bsr) {
        super(isRadio, rawSenderID, ttl, bsr);
        this.data = BitArray.from(bsr); // 将数据存入 data，之后再解码
    }

    /**
     * 获得此消息所有打上了 {@link MessageField} 的 fields
     */
    private List<Field> getMessageFields() {
        Class<? extends BaseMessage> messageClass = this.getClass();
        List<Field> messageFields = new ArrayList<>();
        for (Field field : messageClass.getDeclaredFields()) {
            if (!field.isAnnotationPresent(MessageField.class))
                continue;
            field.setAccessible(true);
            messageFields.add(field);
        }
        return messageFields;
    }

    public BitArray getData() {
        return data;
    }

    /**
     * 编码此消息到一个 {@link BitArray}，结果保存到此消息的 data 成员变量中
     */
    public final void encode(@Nonnull EncoderDecoderTool edt) {
        try {
            // 获得这个消息的所有 message fields
            List<Field> messageFields = getMessageFields();
            BitArray result = BitArray.create();
            // 对所有 field 逐一编码
            for (Field field : messageFields) {
                MessageField fieldAnno = field.getAnnotation(MessageField.class);
                // 根据 field 的类型编码
                if (fieldAnno.type() == MessageFieldType.INTEGER) {
                    int bitLength = 32;
                    // 如果指定了 bitLength，则使用指定的 bitLength
                    if (fieldAnno.bitLength() != -1)
                        bitLength = fieldAnno.bitLength();
                    int value = (int) field.get(this);
                    edt.encodeInt(value, bitLength, result);
                } else if (fieldAnno.type() == MessageFieldType.BOOLEAN) {
                    boolean is = (Boolean) field.get(this);
                    edt.encodeBOOLEAN(is, result);
                } else if (fieldAnno.type() == MessageFieldType.AGENT_ID) {
                    EntityID agentId = (EntityID) field.get(this);
                    edt.encodeAgentId(agentId, result);
                } else if (fieldAnno.type() == MessageFieldType.AREA_ID) {
                    EntityID areaId = (EntityID) field.get(this);
                    edt.encodeAreaId(areaId, result);
                } else if (fieldAnno.type() == MessageFieldType.AREA_EDGE_PAIR_PASSABLE_PROPERTY) {
                    AreaEdgePairPassableProperty areaEdgePairPassableProperty = (AreaEdgePairPassableProperty) field.get(this);
                    edt.encodeAreaEdgePairPassableProperty(areaEdgePairPassableProperty, result);
                } else if (fieldAnno.type() == MessageFieldType.POSITION_ID_PROPERTY) {
                    PositionIdProperty positionIdProperty = (PositionIdProperty) field.get(this);
                    edt.encodePositionIdProperty(positionIdProperty, result);
                } else if (fieldAnno.type() == MessageFieldType.BURIEDNESS) {
                    int buriedness = (int) field.get(this);
                    edt.encodeBuriedness(buriedness, result);
                } else if (fieldAnno.type() == MessageFieldType.STUCK_TYPE) {
                    StuckType stuckType = (StuckType) field.get(this);
                    edt.encodeStuckType(stuckType, result);
                } else if (fieldAnno.type() == MessageFieldType.CIVILIAN_ID) {
                    EntityID cvId = (EntityID) field.get(this);
                    edt.encodeCivilianId(cvId, result);
                } else if (fieldAnno.type() == MessageFieldType.BUILDING_ID) {
                    EntityID buildingId = (EntityID) field.get(this);
                    edt.encodeBuildingId(buildingId, result);
                } else if (fieldAnno.type() == MessageFieldType.BROKENNESS) {
                    int brokenness = (int) field.get(this);
                    edt.encodeBrokenness(brokenness, result);
                } else if (fieldAnno.type() == MessageFieldType.REFUGE_ID) {
                    EntityID refugeId = (EntityID) field.get(this);
                    edt.encodeRefugeId(refugeId, result);
                } else if (fieldAnno.type() == MessageFieldType.PATH_IDS_PROPERTY) {
                    PathIdsProperty pathIdsProperty = (PathIdsProperty) field.get(this);
                    edt.encodePathIdsProperty(pathIdsProperty, result);
                } else if (fieldAnno.type() == MessageFieldType.EXACT_TIME_TO_DEAD) {
                    int timeToDead = (Integer) field.get(this);
                    edt.encodeExactTimeToDead(timeToDead, result);
                } else if (fieldAnno.type() == MessageFieldType.UNAVAILABLE_TYPE) {
                    UnavailableType unavailableType = (UnavailableType) field.get(this);
                    edt.encodeUnavailableType(unavailableType, result);
                } else if (fieldAnno.type() == MessageFieldType.MESSAGE_CLASS_PROPERTY) {
                    MessageClassProperty messageClassProperty = (MessageClassProperty) field.get(this);
                    edt.encodeMessageClassProperty(messageClassProperty, result);
                } else {
                    // TODO 支持更多类型的编码
                    throw new UnsupportedOperationException();
                }
            }
            this.data = result;
        } catch (Exception e) {
            throw new EncodingException(e);
        }
    }

    /**
     * 从 data 成员变量中读消息的 bits，解码此消息，并赋值对应 {@link MessageField}
     */
    public final void decode(@Nonnull EncoderDecoderTool edt) {
        try {
            // 获得这个消息的所有 message fields
            List<Field> messageFields = getMessageFields();
            // 解码时反向遍历
            for (int i = messageFields.size() - 1; i >= 0; i --) {
                Field field = messageFields.get(i);
                MessageField fieldAnno = field.getAnnotation(MessageField.class);
                // 根据 field 的类型解码
                if (fieldAnno.type() == MessageFieldType.INTEGER) {
                    int bitLength = 32;
                    // 如果指定了 bitLength，则使用指定的 bitLength
                    if (fieldAnno.bitLength() != -1)
                        bitLength = fieldAnno.bitLength();
                    int value = edt.decodeInt(bitLength, this.data);
                    field.set(this, value);
                } else if (fieldAnno.type() == MessageFieldType.BOOLEAN) {
                    boolean is = edt.decodeBOOLEAN(this.data);
                    field.set(this, is);
                } else if (fieldAnno.type() == MessageFieldType.AGENT_ID) {
                    EntityID agentId = edt.decodeAgentId(this.data);
                    field.set(this, agentId);
                } else if (fieldAnno.type() == MessageFieldType.AREA_ID) {
                    EntityID areaId = edt.decodeAreaId(this.data);
                    field.set(this, areaId);
                } else if (fieldAnno.type() == MessageFieldType.AREA_EDGE_PAIR_PASSABLE_PROPERTY) {
                    AreaEdgePairPassableProperty areaEdgePairPassableProperty = edt.decodeAreaEdgePairPassableProperty(this.data);
                    field.set(this, areaEdgePairPassableProperty);
                } else if (fieldAnno.type() == MessageFieldType.POSITION_ID_PROPERTY) {
                    PositionIdProperty positionIdProperty = edt.decodePositionIdProperty(this.data);
                    field.set(this, positionIdProperty);
                } else if (fieldAnno.type() == MessageFieldType.BURIEDNESS) {
                    int buriedness = edt.decodeBuriedness(this.data);
                    field.set(this, buriedness);
                } else if (fieldAnno.type() == MessageFieldType.STUCK_TYPE) {
                    StuckType stuckType = edt.decodeStuckType(this.data);
                    field.set(this, stuckType);
                } else if (fieldAnno.type() == MessageFieldType.CIVILIAN_ID) {
                    EntityID cvId = edt.decodeCivilianId(this.data);
                    field.set(this, cvId);
                } else if (fieldAnno.type() == MessageFieldType.BUILDING_ID) {
                    EntityID buildingId = edt.decodeBuildingId(this.data);
                    field.set(this, buildingId);
                } else if (fieldAnno.type() == MessageFieldType.BROKENNESS) {
                    int brokenness = edt.decodeBrokenness(this.data);
                    field.set(this, brokenness);
                } else if (fieldAnno.type() == MessageFieldType.REFUGE_ID) {
                    EntityID refugeId = edt.decodeRefugeId(this.data);
                    field.set(this, refugeId);
                } else if (fieldAnno.type() == MessageFieldType.PATH_IDS_PROPERTY) {
                    PathIdsProperty pathIdsProperty = edt.decodePathIdsProperty(this.data);
                    field.set(this, pathIdsProperty);
                } else if (fieldAnno.type() == MessageFieldType.EXACT_TIME_TO_DEAD) {
                    int timeToDead = edt.decodeExactTimeToDead(this.data);
                    field.set(this, timeToDead);
                } else if (fieldAnno.type() == MessageFieldType.UNAVAILABLE_TYPE) {
                    UnavailableType unavailableType = edt.decodeUnavailableType(this.data);
                    field.set(this, unavailableType);
                } else if (fieldAnno.type() == MessageFieldType.MESSAGE_CLASS_PROPERTY) {
                    MessageClassProperty messageClassProperty = edt.decodeMessageClassProperty(this.data, this.getTTL());
                    field.set(this, messageClassProperty);
                } else {
                    throw new UnsupportedOperationException();
                }
            }
            this.data.clear(); // 解码完清除没用的 bits
        } catch (Exception e) {
            throw new DecodingException(e);
        }
    }

    @Override
    public final int getByteArraySize() {
        if (this.bytesCache == null) {
            assert this.data != null;
            this.bytesCache = this.data.toByteArray();
        }
        return this.bytesCache.length;
    }

    @Nonnull
    @Override
    public final byte[] toByteArray() {
        if (this.bytesCache == null) {
            assert this.data != null;
            this.bytesCache = this.data.toByteArray();
        }
        return this.bytesCache;
    }

    @Nonnull
    @Override
    public final BitOutputStream toBitOutputStream() {
        assert this.data != null;
        return this.data.toBitOutputStream();
    }

    @Nonnull
    @Override
    public String getCheckKey() {
        return toString(); // TODO
    }

    /**
     * 适用于所有消息的 {@code toString} 函数
     */
    @Override
    public final String toString() {
        try {
            Class<?> messageClass = getClass();
            StringBuilder sb = new StringBuilder("[")
                    .append(messageClass.getSimpleName())
                    .append(" ");
            for (Field field : messageClass.getDeclaredFields()) {
                if (!field.isAnnotationPresent(MessageField.class))
                    continue;
                field.setAccessible(true);
                String name = field.getName();
                Object value = field.get(this);
                sb.append(name).append("=").append(value).append(" ");
            }
            if (this.data != null) {
                sb.append("bits").append("=").append(this.data);
            }
            sb.append("]");
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
