package hfutinvincible.mock;

import adf.core.agent.communication.MessageManager;
import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.ScenarioInfo;
import adf.core.agent.info.WorldInfo;
import adf.core.component.communication.ChannelSubscriber;
import hfutinvincible.debug.TestLogger;
import org.apache.log4j.Logger;
import rescuecore2.standard.entities.StandardEntityURN;

import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;

public class HFUTChannelSubscriber extends ChannelSubscriber {

    private Logger logger;

    public HFUTChannelSubscriber() {}

    @Override
    public void subscribe(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, MessageManager messageManager) {
        super.subscribe(agentInfo, worldInfo, scenarioInfo, messageManager);

        logger = TestLogger.getLogger("HFUTTool/HFUTChannelSubscriber/" + agentInfo.me());

        int numChannels = scenarioInfo.getCommsChannelsCount() - 1;
        int numSlots = isPlatoonAgent(agentInfo)
                ? scenarioInfo.getCommsChannelsMaxPlatoon()
                : scenarioInfo.getCommsChannelsMaxOffice();

        logger.info("numChannels=" + numChannels);
        logger.info("numSlots=" + numSlots);

        int[] slots = new int[numSlots];
        for (int i = 0; i < numSlots; i ++) {
            for (;;) {
                int newChannel = ThreadLocalRandom.current().nextInt(0, numChannels + 1);
                // 如果有足够多的信道可供订阅，则保证不订阅相同的信道
                if (numSlots <= numChannels) {
                    boolean duplicated = false;
                    for (int s : slots) {
                        if (s == newChannel) {
                            duplicated = true;
                            break;
                        }
                    }
                    if (!duplicated) {
                        slots[i] = newChannel;
                        break;
                    }
                } else {
                    slots[i] = newChannel;
                    break;
                }
            }
        }

        logger.debug(Arrays.toString(slots));

        messageManager.subscribeToChannels(slots);
    }

    protected boolean isPlatoonAgent(AgentInfo agentInfo) {
        StandardEntityURN agentType = agentInfo.me().getStandardURN();
        return agentType == StandardEntityURN.FIRE_BRIGADE
                || agentType == StandardEntityURN.POLICE_FORCE
                || agentType == StandardEntityURN.AMBULANCE_TEAM;
    }
}