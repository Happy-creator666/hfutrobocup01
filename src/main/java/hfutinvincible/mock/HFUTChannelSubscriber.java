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

/**
 * 将智能体订阅信道看作是将信道（channel[]）插入（Subscribe）槽（slot[]）中，
 * 每个智能体 slot 的数量是有限的，且与是排智能体还是中心智能体有关. <br>
 *
 * 使用 {@code scenarioInfo.getCommsChannelsMaxPlatoon()} 和
 * {@code scenarioInfo.getCommsChannelsMaxOffice()}
 * 可以分别获得排智能体和中心智能体各自 slot 的个数 {@code numSlots}（最多所能订阅的信道数）.
 * 使用 {@code scenarioInfo.getCommsChannelsCount()} 可以获得可供插入的信道数量 {@code numChannels}. <br>
 *
 * 每个信道由其下标唯一确定，{@code channel[0]} 是声音信道，大家都有，无需 Subscribe; <br>
 * {@code channel[i], i > 0} 都是无线电信道，需要 Subscribe. <br>
 *
 * {@code HFUTChannelSubscriber} 要做的就是确定每个槽 Subscribe 哪个信道
 * （确定 slot[i] = ?, ? 是 Subscribe 的信道在 channel[] 中的下标）. <br>
 *
 * 好的 Channel Subscribe 方案需要满足许多约束，如应能保证不同类型智能体所 Subscribe 的信道有一部分重叠，
 * 这样才能让不同类型智能体通过通讯完成合作; 各信道被订阅次数应尽可能是根据信道质量加权平均的，
 * 这样才能最大化信息传输量. TODO 我们使用 OptaPlanner 来完成这一规划问题.
 *
 * @see adf.core.agent.communication.MessageManager#coordinateMessages
 *
 * @author stardust
 */
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
        logger.info("This is in the mock --------------------------------");

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