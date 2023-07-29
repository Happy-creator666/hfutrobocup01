package hfutinvincible.mock;

import adf.core.agent.communication.MessageManager;
import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.ScenarioInfo;
import adf.core.agent.info.WorldInfo;
import adf.core.component.communication.CommunicationMessage;
import adf.core.component.communication.MessageCoordinator;
import hfutinvincible.debug.TestLogger;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

public class HFUTMessageCoordinator extends MessageCoordinator {

    private Logger logger;

    public HFUTMessageCoordinator() {}

    @Override
    public void coordinate(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo,
                           MessageManager messageManager, ArrayList<CommunicationMessage> sendMessageList,
                           List<List<CommunicationMessage>> channelSendMessageList) {
        this.logger = TestLogger.getLogger("MessageCoordinator/HFUTMessageCoordinator/" + agentInfo.me());

        // TODO 方便调试，这里使用最简单的分类策略
        // 声音信道
        sendMessageList.stream()
                .filter(m -> !m.isRadio())
                .peek(m -> logger.info(m + " /// from voice"))
                .forEach(m -> channelSendMessageList.get(0).add(m));

        // 无线电信道直接平均分配？？
        int numSlots = channelSendMessageList.size();
        int toSlot = 1;
        int index = 0;
        while (index != sendMessageList.size()) {
            if (toSlot >= numSlots) toSlot = 1;
            CommunicationMessage message = sendMessageList.get(index);
            if (message.isRadio()) {
                channelSendMessageList.get(toSlot).add(message);
                toSlot++;
            }
            index++;
        }
        logger.info(channelSendMessageList);
    }
}