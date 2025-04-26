//package hfutinvincible.message;
//
//import adf.core.component.communication.util.BitStreamReader;
//import hfutinvincible.anno.HfutMessage;
//import hfutinvincible.anno.MessageField;
//import hfutinvincible.anno.MessageFieldType;
//import hfutinvincible.common.BitArray;
//import rescuecore2.worldmodel.EntityID;
//
//import javax.annotation.Nonnull;
//
///**
// * 携带有一个智能体所在 Area （位置）的信息
// */
//@HfutMessage
//public class AgentPosAreaMessage extends BaseMessage {
//
//    @MessageField(type=MessageFieldType.AGENT_ID)
//    private EntityID agentId;
//
//    @MessageField(type=MessageFieldType.AREA_ID)
//    private EntityID agentPosAreaId;
//
//    /**
//     * <p><b>子类应该重写此方法！！！</b></p>
//     * <p>这个构造方法在发送消息时用来构造一条要发送的消息。</p>
//     * <p>根据官方的设计，消息编码逻辑应该被放在这里。也就是此函数的原本作用是
//     * 在构造消息的同时，完成消息的编码，将二进制表示的消息存入一个成员变量里面。
//     * 但是我们需要自己控制解码逻辑，因此我们选择不在此处完成编码，而是之后再找个时间完成编码。</p>
//     *
//     * @param isRadio 是否通过无线电（否则通过声音）发送此消息
//     */
//    public AgentPosAreaMessage(boolean isRadio, @Nonnull EntityID agentId, @Nonnull EntityID agentPosAreaId) {
//        super(isRadio);
//        this.agentId = agentId;
//        this.agentPosAreaId = agentPosAreaId;
//    }
//
//    /**
//     * <p><b>此方法将被反射调用，也仅供反射调用，你永远不应该调用此方法！！！</b></p>
//     * <p>根据官方的设计，消息解码逻辑应该被放在这里。也就是此函数的原本作用是
//     * 从 {@code BitStreamReader bsr} 中得到消息的所有 bits，然后完成解码。
//     * 但是，我们需要自己控制解码逻辑，因此我们选择不在此处完成解码，而是先把
//     * {@code BitStreamReader bsr} 中的数据存到 {@link BitArray} 里，之后
//     * 再找个时机完成解码。</p>
//     */
//    public AgentPosAreaMessage(boolean isRadio, int rawSenderID, int ttl, BitStreamReader bsr) {
//        super(isRadio, rawSenderID, ttl, bsr);
//    }
//}