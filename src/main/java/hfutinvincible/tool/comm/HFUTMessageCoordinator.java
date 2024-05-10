package hfutinvincible.tool.comm;

import adf.core.agent.communication.MessageManager;
import adf.core.agent.communication.standard.bundle.StandardMessage;
import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.ScenarioInfo;
import adf.core.agent.info.WorldInfo;
import adf.core.component.communication.CommunicationMessage;
import adf.core.component.communication.MessageCoordinator;
import hfutinvincible.debug.TestLogger;
import hfutinvincible.message.*;
import org.apache.log4j.Logger;
import rescuecore2.standard.entities.Civilian;
import rescuecore2.standard.entities.Human;

import javax.annotation.Nonnull;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 使用 {@link HFUTChannelSubscriber}，slot[] 中每个 Slot 都 Subscribe 了一个 Channel，
 * {@code HFUTMessageCoordinator} 要做的就是将待发送的消息分配到 slot[] 里的各个信道里. <br>
 * <p>
 * 好的 Message Coordinate 方案方案需要满足许多约束，如应能保证更重要的信息放到质量更高的信道，
 * 各信道塞入的消息数应尽可能是根据信道容量加权平均的. 我们使用 OptaPlanner 来完成这一规划问题. <br>
 * <p>
 * 同时，{@code MessageCoordinator} 还需要筛除所有 {@code StandardMessage}，只保留我们需要的
 * {@code HFUTMessage}.
 *
 * @author stardust
 */
public class HFUTMessageCoordinator extends MessageCoordinator {

    private Logger logger;

    public HFUTMessageCoordinator() {
    }

    @Override
    public void coordinate(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo,
                           MessageManager messageManager, ArrayList<CommunicationMessage> sendMessageList,
                           List<List<CommunicationMessage>> channelSendMessageList) {
        this.logger = TestLogger.getLogger("MessageCoordinator/HFUTMessageCoordinator/" + agentInfo.me());

        logger.info("Channel number: " + scenarioInfo.getCommsChannelsCount());

        // 筛选出 BaseMessage，去掉 StandardMessage
        List<BaseMessage> hfutMessageList = sendMessageList.stream()
                .filter(m -> m instanceof BaseMessage)
                .map(m -> (BaseMessage) m)
                .sorted(Comparator.comparingInt(MessagePriority::getPriority))
                .toList();

        logger.info(hfutMessageList);


        int numSlots = channelSendMessageList.size();
        int toSlot = 1;
        int index = 0;
        int[] myCapacityRemain = new int[numSlots];
        for (int i = 0; i < numSlots; i++) {
            myCapacityRemain[i] = estimateTheCapacityICanUse(i, scenarioInfo, worldInfo);
        }
        // TODO 方便调试，这里使用最简单的分类策略
        // 声音信道, 将声音发送到声音信道上
        hfutMessageList.stream()
                .filter(m -> !m.isRadio())
                .peek(m -> logger.info(m + " /// from voice"))
                .forEach(m -> {
                    channelSendMessageList.get(0).add(m);
                    myCapacityRemain[0] -= m.getByteArraySizeCopy();
                });

        // 无线电信道
        while (index != hfutMessageList.size()) {
            if (toSlot >= numSlots) toSlot = 1;
            BaseMessage message = hfutMessageList.get(index);
            if (message.isRadio()) {
                if (numSlots == 1) {
                    ensureIsRadioFalse(message);
                    channelSendMessageList.get(0).add(message);
                } else {
                    myCapacityRemain[toSlot] -= message.getByteArraySizeCopy();
                    if (myCapacityRemain[toSlot] < 0) {
                        if (myCapacityRemain[0] < 0)
                            channelSendMessageList.get(toSlot).add(message);
                        else {
                            ensureIsRadioFalse(message);
                            channelSendMessageList.get(0).add(message);
                        }
                    } else {
                        channelSendMessageList.get(toSlot).add(message);
                    }
                    channelSendMessageList.get(toSlot).add(message);
                }
                toSlot++;
            }
            index++;
        }
        logger.info("channelSendMessageList:" + channelSendMessageList);
    }

    private static void ensureIsRadioFalse(CommunicationMessage message) {
        try {
            Field isRadioField = CommunicationMessage.class.getDeclaredField("isRadio");
            isRadioField.setAccessible(true);
            isRadioField.set(message, false);
        } catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private int getChannelBandwidth(int channel, @Nonnull ScenarioInfo si) {
        return si.getCommsChannelBandwidth(channel);
    }

    private int estimateTheCapacityICanUse(int channel, @Nonnull ScenarioInfo si, @Nonnull WorldInfo wi) {
        if (channel == 0)
            return si.getVoiceMessagesSize();
        // get agent count
        long agentCount = wi.getAllEntities().stream()
                .filter(Human.class::isInstance)
                .filter(h -> !(h instanceof Civilian)).count() / 2;
        int channelBandwidth = getChannelBandwidth(channel, si);
        return (int) (channelBandwidth / agentCount);
    }
}