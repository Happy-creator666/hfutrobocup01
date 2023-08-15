package hfutinvincible.tool.comm;

import adf.core.agent.communication.MessageManager;
import adf.core.agent.info.AgentInfo;
import adf.core.agent.precompute.PrecomputeData;
import adf.core.component.communication.CommunicationMessage;
import adf.core.component.communication.MessageBundle;
import hfutinvincible.anno.AgentType;
import hfutinvincible.anno.Autowired;
import hfutinvincible.anno.HfutMessage;
import hfutinvincible.anno.HfutTool;
import hfutinvincible.common.ReflectUtil;
import hfutinvincible.message.AgentPosAreaMessage;
import hfutinvincible.message.BaseMessage;
import hfutinvincible.tool.CalcContext;
import hfutinvincible.tool.LifeCycle;
import org.apache.log4j.Logger;
import rescuecore2.standard.entities.AmbulanceTeam;
import rescuecore2.standard.entities.FireBrigade;
import rescuecore2.standard.entities.PoliceForce;
import rescuecore2.worldmodel.EntityID;

import java.lang.reflect.Field;
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

    @Autowired Logger logger;

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
        EntityID agentId = ai.getID();
        EntityID agentPosAreaId = ai.getPositionArea().getID();
        BaseMessage m1 = new AgentPosAreaMessage(true, agentId, agentPosAreaId);
        addMessage(m1);
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
}
