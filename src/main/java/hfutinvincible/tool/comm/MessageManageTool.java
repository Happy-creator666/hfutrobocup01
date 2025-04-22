package hfutinvincible.tool.comm;

import adf.core.agent.action.Action;
import adf.core.agent.communication.MessageManager;
import adf.core.agent.communication.standard.bundle.StandardMessage;
import adf.core.agent.develop.DevelopData;
import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.ScenarioInfo;
import adf.core.agent.info.WorldInfo;
import adf.core.agent.module.ModuleManager;
import adf.core.component.communication.CommunicationMessage;
import com.google.common.collect.Streams;
import hfutinvincible.Tuple2;
import hfutinvincible.anno.AgentType;
import hfutinvincible.anno.Autowired;
import hfutinvincible.anno.HfutMessage;
import hfutinvincible.anno.HfutTool;
import hfutinvincible.common.ReflectUtil;
import hfutinvincible.message.*;
import hfutinvincible.tool.*;
import hfutinvincible.tool.building.BuildingBrokennessTool;
import hfutinvincible.tool.building.RefugeConditionTool;
import hfutinvincible.tool.edge.AreaPassableTool;
import hfutinvincible.tool.human.*;
import hfutinvincible.tool.partition.Partition;
import hfutinvincible.tool.partition.StaticPartitionTool;
import org.apache.log4j.Logger;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@HfutTool
@AgentType({PoliceForce.class, AmbulanceTeam.class, FireBrigade.class})
public class MessageManageTool implements LifeCycle {

    @Autowired private AgentInfo agentInfo;
    @Autowired private WorldInfo worldInfo;
    @Autowired private Logger logger;

    /**
     * 所有消息都放到这个包里，第一次 {@code updateInfo} 时会扫描这个包，
     * 然后将所有消息类注册到官方的消息管理器。注意每个消息类应该继承
     * {@link BaseMessage} 并且打上 {@link HfutMessage} 注解。
     */
    private static final String MESSAGE_PACKAGE = "hfutinvincible.message";

    /** 本周期收到的所有消息 */
    private final List<CommunicationMessage> receivedMessages;

    /**
     * 消息分类缓存，注意 value 类型是 {@literal List<HFUTMessage>}，因此取用时需先根据 key 做转换
     */
    private final Map<Class<? extends CommunicationMessage>, List<CommunicationMessage>> receivedMessageCache;

    /**
     * 官方提供的消息管理器，在第一次 {@code updateInfo} 时获得
     */
    private MessageManager messageManager;

    private Boolean isDominant;
    private final List<BaseMessage> messagesToSendByRadio;
    private final List<BaseMessage> messagesToSendByVoice;

    private final Params params = new Params(getClass().getSimpleName());

    // Tools
    @Autowired
    private HumanPositionTool humanPositionTool;
    @Autowired
    private BuriednessTool buriednessTool;
    @Autowired
    private StuckInfoTool stuckInfoTool;
    @Autowired
    private TimeToDeadTool timeToDeadTool;
    @Autowired
    private BuildingBrokennessTool buildingBrokennessTool;
    @Autowired
    private RefugeConditionTool refugeConditionTool;
    @Autowired
    private AreaPassableTool areaPassableTool;
    @Autowired
    private UnavailableTargetTool unavailableTargetTool;
    @Autowired
    private StaticPartitionTool staticPartitionTool;
    @Autowired
    private EncoderDecoderTool edt;
    @Autowired
    private PropertyManageTool propertyManageTool;


    public MessageManageTool(){
        this.receivedMessages = new ArrayList<>();
        this.messagesToSendByRadio = new ArrayList<>();
        this.messagesToSendByVoice = new ArrayList<>();

        // 先为每个消息类都初始化一个空消息列表
        this.receivedMessageCache = new HashMap<>();
        HFUTMessageBundle.messageClasses
                .forEach(m -> this.receivedMessageCache.put(m , new ArrayList<>()));

    }

    @Override
    public void updateInfo(MessageManager messageManager) {
        logger.info("---------------------Time" + agentInfo.getTime() + " updateInfo start--------------------");

        int time = agentInfo.getTime();
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
            } catch (DecodingException e) {
                logger.info("Failed to decode " + message + "\n" + e);
            }
        }
    }

    @Override
    public void afterCalc(CalcContext context) {

        Action resultAction = context.getResultAction();
        Area targetArea = context.getTarget();
        List<EntityID> targetPath = context.getTargetPath();

        this.messagesToSendByRadio.clear(); // 清空
        this.messagesToSendByVoice.clear();
        sendMeConditionMessageByRadioAndVoice(targetArea == null ? null : targetArea.getID());
        if (targetPath != null)
            sendMeTargetPathMessageByVoice(targetPath);
        else logger.info("targetPath=" + targetPath);
        if (isDominant()) {// 如果我是 dominant agent，则发送我所看到的发生变化的 cv 的信息
            logger.info("I am dominator.");
            sendCvConditionMessages();
        }
        sendAreaEdgePairsPassableMessages();
        sendBuildingBrokennessInSight();
        sendRefugeConditionsInSight();
        sendTargetBlockMessageByRadioAndVoice();
        sendNoBuriedOrHurtMessages();

        // 编码
        this.messagesToSendByRadio.stream()
                .filter(Objects::nonNull)
                .forEach(this::addMessage);

        StringBuilder sb = new StringBuilder();
        sb.append("Added radio messages=[\n");
        for (BaseMessage message : getMessagesToSendByRadio()) {
            sb.append(message);
            sb.append("\t\t raw bits: " + message.getData() + '\n');
        }
        sb.append("]\n");
        logger.info(sb);


        sb.setLength(0); // clear
        if (!this.messagesToSendByVoice.isEmpty()) {
            sb.append("Added voice messages=[\n");
            this.messagesToSendByVoice.stream()
                    .filter(Objects::nonNull)
                    .forEach(this::addMessage);

            for (BaseMessage message : this.messagesToSendByVoice) {
                sb.append(message);
                sb.append("\t\t raw bits: " + message.getData() + '\n');
            }
            sb.append("]\n");
            logger.info(sb);
            // 将 voice message 打包为一个大 message
//                this.messagesToSendByVoice.forEach(voiceMessage -> {
////                    sb.append(voiceMessage);
////                    sb.append('\n');
//                   try{
//                       voiceMessage.encode(propertyManageTool);
//                       sb.append("\t\t raw bits: " + voiceMessage.rawBitsArray + '\n');
//                       messageManager.addMessage(voiceMessage);
//                       sb.append("]\n");
//                       logger.info(sb);
//                   }catch (Exception e){
//                       logger.error("Cannot encode message " + voiceMessage + "\n " + e);
//                   }
//                });
//                var packedVoiceMessage = new PackedVoiceMessage(
//                        false, scenarioInfo.getVoiceMessagesSize(), this.messagesToSendByVoice.toArray(new HFUTMessage[0]));
//                try {
//                    packedVoiceMessage.encode(propertyManageTool);
//                    sb.append("\t\t raw bits: " + packedVoiceMessage.rawBitsArray + '\n');
//                    messageManager.addMessage(packedVoiceMessage);
//                    sb.append("]\n");
//                    logger.info(sb);
//                } catch (Exception e) {
//                    logger.error("Cannot encode message " + packedVoiceMessage + "\n " + e);
//                }
        }

        logger.info("---------------------Time" + agentInfo.getTime() + " afterCalc end--------------------\n");
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

    /**
     * 获得本周期收到的所有指定类型的 Messages
     */
    @SuppressWarnings("unchecked")
    public <T extends CommunicationMessage> List<T> getReceivedMessages(Class<T> type) {
/*// 如果逻辑正确，下面被注释的检查是不必要的
//        if (this.receivedMessages == null)
//            this.getAndDecodeMessages();
        List<CommunicationMessage> ret = this.messageManager.getReceivedMessageList(type);
        if (ret == null) {
            return this.getReceivedMessageList().stream()
                    .filter(m -> type.isAssignableFrom(m.getClass()))
                    .map(type::cast)
                    .toList();
        }
        return (List<T>) ret; // 转换为指定类型*/

        // 如果逻辑正确，下面被注释的检查是不必要的
//        if (this.receivedMessages == null)
//            this.getAndDecodeMessages();
        List<CommunicationMessage> ret = this.receivedMessageCache.get(type);
        if (ret == null) {
            return receivedMessages.stream()
                    .filter(m -> type.isAssignableFrom(m.getClass()))
                    .map(type::cast)
                    .toList();
        }
        return (List<T>) ret; // 转换为指定类型
    }


    //////////////
    /**
     * 不在 at 上认为处于支配地位
     */
    private boolean isDominant() {
        EntityID posId = agentInfo.getPosition();
        return !(worldInfo.getEntity(posId) instanceof AmbulanceTeam);
    }

    /**
     * 发送 unavailabletMessage
     * @see {@link #sendTargetBlockMessageByRadioAndVoice()}
     */
    private void sendNoBuriedOrHurtMessages() {
        // 消防只发送 noHurtDefinitely，救护只发送 noHurt
        UnavailableType myType = agentInfo.me() instanceof AmbulanceTeam
                ? UnavailableType.noHurtDefinitely
                : UnavailableType.noBuried;

        List<Tuple2<EntityID, UnavailableType>> unavailableTargetAndTypes = unavailableTargetTool
                .getAllUnavailableTargetAndTypesFromMyself(myType);

        Partition<EntityID> myAssignedPartition = staticPartitionTool.getAssignedPartition();
        for (Tuple2<EntityID, UnavailableType> t : unavailableTargetAndTypes) {
            EntityID targetId = t.e0;
            UnavailableType unavailableType = t.e1;
            // 如果是这个周期我看到的 unavailable target，发 radio message
            if (unavailableTargetTool.isUnavailableTargetISee(targetId)) {
                UnavailableMessage radioMessage = new UnavailableMessage(true, targetId, unavailableType);
                messagesToSendByRadio.add(radioMessage);
            }
            // 我所在分区内，我知道的所有 unavailable targets 通过声音发送
            if (myAssignedPartition.contains(targetId)) {
                UnavailableMessage voiceMessage = new UnavailableMessage(false, targetId, unavailableType);
                messagesToSendByVoice.add(voiceMessage);
            }
        }
    }

    /**
     * 发送因堵住而无法到达的目标
     */
    private void sendTargetBlockMessageByRadioAndVoice() {
        Human me = (Human) agentInfo.me();
        EntityID meId = agentInfo.getID();
        EntityID mePosId = this.humanPositionTool.getPosition(meId);
        if (mePosId == null) return;
        if (me instanceof PoliceForce) // 警察不发 TargetBlockMessage
            return;

        int radioCount = params.getInt("targetBlockMessageByRadioNumPerTimestep");
        int voiceCount = params.getInt("targetBlockMessageByVoiceNumPerTimestep");

        Partition<EntityID> myAssignedPartition = staticPartitionTool.getAssignedPartition();
        for (EntityID blockedTarget : unavailableTargetTool.getAllUnavailableTargets(UnavailableType.block).toList()) {
            // 如果是我这周期看到的 block target
            if (--radioCount > 0 && unavailableTargetTool.isUnavailableTargetISee(blockedTarget)) {
                TargetBlockMessage radioMessage = new TargetBlockMessage(true, meId, mePosId, blockedTarget);
                messagesToSendByRadio.add(radioMessage);
            }
            // 如果是我分区内的 block target
            if (--voiceCount > 0 && myAssignedPartition.contains(blockedTarget)) {
                TargetBlockMessage voiceMessage = new TargetBlockMessage(false, meId, mePosId, blockedTarget);
                messagesToSendByVoice.add(voiceMessage);
            }
        }
    }

    /**
     * 发送自己的情况
     * @param targetAreaId 目标 Area 的 EntityID
     */
    private void sendMeConditionMessageByRadioAndVoice(@Nullable EntityID targetAreaId) {
        EntityID meId = agentInfo.getID();
        EntityID mePosId = this.humanPositionTool.getPosition(meId);
        if (mePosId == null) return;
        StandardEntity mePos = worldInfo.getEntity(mePosId);
        // 我被装在 AT 上，我就不发消息了
        if (mePos instanceof AmbulanceTeam)
            return;
        if (mePos == null) return;
        Integer timeToDead = this.timeToDeadTool.getLatestTimeToDead(meId);
        if (timeToDead == null) return;
        Integer buriedness = this.buriednessTool.getBuriedness(meId);
        if (buriedness == null) return;
        StuckType stuckType = this.stuckInfoTool.getStuckType(meId);
        if (stuckType == null) return;

        AgentConditionMessage radioMessage = new AgentConditionMessage(
                meId,
                true,
                mePos,
                timeToDead,
                buriedness,
                stuckType,
                targetAreaId);
        messagesToSendByRadio.add(radioMessage);
        AgentConditionMessage voiceMessage = new AgentConditionMessage(
                meId,
                false,
                mePos,
                timeToDead,
                buriedness,
                stuckType,
                targetAreaId);
        messagesToSendByVoice.add(voiceMessage);
    }

    /**
     * 通过声音发送我的目标 path
     */
    private void sendMeTargetPathMessageByVoice(@Nonnull List<EntityID> targetPath) {
        EntityID meId = agentInfo.getID();
        TargetPathMessage voiceMessage = new TargetPathMessage(meId, targetPath);
        messagesToSendByVoice.add(voiceMessage);
    }

    /**
     * 发送视野内改变的 cv 的信息
     */
    private void sendCvConditionMessages() {

        Streams.concat(this.humanPositionTool.getPosChangedCvIdsInSight().stream(),
                        this.buriednessTool.getBuriednessChangedCvIdsInSight().stream(),
                        this.buriednessTool.getBuriedTargetCvs().stream(),
                        this.stuckInfoTool.getStuckTypeChangedCvIdsInSight().stream(),
                        this.timeToDeadTool.getTimeToDeadChangedCvIdsInSight().stream())
                .distinct()
                .filter(id -> worldInfo.getEntity(id) instanceof Civilian)
                .forEach(cvId -> {
                    EntityID cvPosId = this.humanPositionTool.getPosition(cvId);
                    if (cvPosId == null) return;
                    StandardEntity cvPos = worldInfo.getEntity(cvPosId);
                    if (cvPos == null) return;
                    Integer timeToDead = this.timeToDeadTool.getLatestTimeToDead(cvId);
                    if (timeToDead == null) return;
                    Integer buriedness = this.buriednessTool.getBuriedness(cvId);
                    if (buriedness == null) return;
                    StuckType stuckType = this.stuckInfoTool.getStuckType(cvId);
                    if (stuckType == null) return;
                    CvConditionMessage radioMessage = new CvConditionMessage(true, cvId, cvPos, timeToDead, buriedness, stuckType);
                    messagesToSendByRadio.add(radioMessage);
                    CvConditionMessage voiceMessage = new CvConditionMessage(false, cvId, cvPos, timeToDead, buriedness, stuckType);
                    messagesToSendByVoice.add(voiceMessage);
                });
    }

    /**
     * 发送视野内通行情况改变的 area 的信息
     */
    private void sendAreaEdgePairsPassableMessages() {
        this.areaPassableTool.getEdgePairInfosToSend()
                .stream()
                .map(p -> new AreaEdgePairPassableMessage(true, p.first(), p.second()))
                .forEach(messagesToSendByRadio::add);

    }

    /**
     * 发送视野内损坏程度改变的建筑的信息
     */
    private void sendBuildingBrokennessInSight() {
        this.buildingBrokennessTool.getBrokennessChangedBuildingIdsInSight()
                .stream()
                .map(buildingId -> {
                    Integer brokenness = this.buildingBrokennessTool.getBuildingBrokenness(buildingId);
                    if (brokenness == null) return null;
                    return new BuildingBrokenMessage(true, buildingId, brokenness);
                })
                .filter(Objects::nonNull)
                .forEach(messagesToSendByRadio::add);
    }

    /**
     * 发送视野内改变的 refuge 情况的信息
     */
    private void sendRefugeConditionsInSight() {
        for (EntityID refugeId : this.refugeConditionTool.getChangedRefugeIdsInSight()) {
            // 此 refuge 一定在我视野范围内，因此可以之间获取
            Refuge refuge = (Refuge) worldInfo.getEntity(refugeId);
            if (refuge == null) continue;
            var radioMessage = new RefugeConditionMessage(true, refuge);
            this.messagesToSendByRadio.add(radioMessage);
            var voiceMessage = new RefugeConditionMessage(false, refuge);
            this.messagesToSendByVoice.add(voiceMessage);
        }
    }

    /**
     * 获得本周期收到的所有 Messages
     */
    public List<CommunicationMessage> getReceivedMessages() {
// 如果逻辑正确，下面被注释的检查是不必要的
//        if (this.receivedMessages == null)
//            this.getAndDecodeMessages();
        return new ArrayList<>(this.receivedMessages);
    }

    /**
     * 获得本周期要发送的所有 Messages
     */
    @SuppressWarnings("unchecked")
    public List<BaseMessage> getMessagesToSendByRadio() {
        if (this.messageManager == null)
            return null;
        // 使用反射获得 MessageManager 的私有成员 sendMessageList
        Field sendMessageListField;
        try {
            sendMessageListField = MessageManager.class.getDeclaredField("sendMessageList");
            sendMessageListField.setAccessible(true);
            ArrayList<CommunicationMessage> sendMessageList =
                    (ArrayList<CommunicationMessage>) sendMessageListField.get(this.messageManager);
            return sendMessageList.stream()
                    .filter(msg -> msg instanceof BaseMessage)
                    .map(BaseMessage.class::cast)
                    .collect(Collectors.toList());
        } catch (NoSuchFieldException | IllegalAccessException e) {
            logger.error("IMPOSSIBLE!" + e);
            return null;
        }
    }

    /**
     * MessageTool 是否注册了 MessageManager
     */
    public boolean isMessageManagerRegistered() {
        return this.messageManager != null;
    }

    /**
     * generate [lo, hi)
     * @param lo inclusive
     * @param hi exclusive
     */
    private static List<Integer> range(int lo, int hi) {
        return IntStream.range(lo, hi).boxed().toList();
    }

}
