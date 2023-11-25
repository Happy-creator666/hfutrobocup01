package hfutinvincible.tool.comm;

import adf.core.agent.communication.MessageManager;
import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.WorldInfo;
import adf.core.component.communication.CommunicationMessage;
import hfutinvincible.anno.AgentType;
import hfutinvincible.anno.Autowired;
import hfutinvincible.anno.HfutMessage;
import hfutinvincible.anno.HfutTool;
import hfutinvincible.common.ReflectUtil;
import hfutinvincible.message.AgentPosAreaMessage;
import hfutinvincible.message.AreaEdgePairPassableMessage;
import hfutinvincible.message.BaseMessage;
import hfutinvincible.tool.CalcContext;
import hfutinvincible.tool.LifeCycle;
import org.apache.log4j.Logger;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;

import java.util.List;

@HfutTool
@AgentType({PoliceForce.class, AmbulanceTeam.class, FireBrigade.class})
public class MessageManageTool implements LifeCycle {

    /**
     * 所有消息都放到这个包里，第一次 {@code updateInfo} 时会扫描这个包，
     * 然后将所有消息类注册到官方的消息管理器。注意每个消息类应该继承
     * {@link BaseMessage} 并且打上 {@link HfutMessage} 注解。
     */
    private static final String MESSAGE_PACKAGE = "hfutinvincible.message";

    @Autowired private AgentInfo ai;

    @Autowired private EncoderDecoderTool edt;

    @Autowired
    WorldInfo worldInfo;

    @Autowired Logger logger;

    @Autowired
    PropertyManageTool propertyManageTool;

    /**
     * 官方提供的消息管理器，在第一次 {@code updateInfo} 时获得
     */
    private MessageManager messageManager;

    @Override
    public void updateInfo(MessageManager messageManager) {
        int time = ai.getTime();
        if (time == 1) {
            int index = 12; // 官方已经注册了 12 种消息，我们的消息类型 index 从 12 开始
            // 扫描出所有消息类
            List<Class<BaseMessage>> messageClasses;
            try {
                messageClasses = ReflectUtil.scanAllClasses(MESSAGE_PACKAGE, c -> c.isAnnotationPresent(HfutMessage.class));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            // 向 messageManager 注册我们的所有消息类型
            for (Class<BaseMessage> messageClass : messageClasses) {
                messageManager.registerMessageClass(index ++, messageClass);
            }
            // 初始化 messageManager
            this.messageManager = messageManager;
        }
        // 对所有消息解码
        for (BaseMessage message : getReceivedMessageList()) {
            try {
                message.decode(edt);
                logger.info("Received " + message);
            } catch (EncodingException e) {
                logger.info("Failed to decode " + message + "\n" + e);
            }
        }
    }

    @Override
    public void afterCalc(CalcContext context) {
        // TEST 测试发送消息
        /*EntityID agentId = ai.getID();
        EntityID agentPosAreaId = ai.getPositionArea().getID();
        BaseMessage m1 = new AgentPosAreaMessage(true, agentId, agentPosAreaId);
        addMessage(m1);*/

        //MyTest
//        EntityID agentPosAreaId = ai.getPositionArea().getID();
//        Area area = (Area) worldInfo.getEntity(agentPosAreaId);
//        addMessage(new AreaEdgePairPassableMessage(true, area, List.of(true,false,true)));//随便加入的list
    }

    /**
     * 发送一条消息
     */
    public void addMessage(BaseMessage message) {
        try {
            message.encode(edt); // 添加消息前先编码
            this.messageManager.addMessage(message);
            logger.info("Send " + message);
        } catch (EncodingException e) {
            logger.info("Failed to encode " + message + "\n" + e);
        }
    }

    /**
     * 获得本周期受到的所有消息
     */
    @SuppressWarnings("unchecked")
    public List<BaseMessage> getReceivedMessageList() {
        return (List<BaseMessage>) (Object) this.messageManager.getReceivedMessageList();
    }

    //MyChanges Following

    /**
     * 获得本周期收到的所有指定类型的 Messages
     */
    @SuppressWarnings("unchecked")
    public <T extends CommunicationMessage> List<T> getReceivedMessages(Class<T> type) {
// 如果逻辑正确，下面被注释的检查是不必要的
//        if (this.receivedMessages == null)
//            this.getAndDecodeMessages();
        List<CommunicationMessage> ret = this.messageManager.getReceivedMessageList(type);
        if (ret == null) {
            return this.getReceivedMessageList().stream()
                    .filter(m -> type.isAssignableFrom(m.getClass()))
                    .map(type::cast)
                    .toList();
        }
        return (List<T>) ret; // 转换为指定类型
    }
}
