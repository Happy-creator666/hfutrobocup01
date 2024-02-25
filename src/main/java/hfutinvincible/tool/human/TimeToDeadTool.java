//package hfutinvincible.tool.human;
//
//import adf.core.agent.communication.MessageManager;
//import adf.core.agent.develop.DevelopData;
//import adf.core.agent.info.AgentInfo;
//import adf.core.agent.info.ScenarioInfo;
//import adf.core.agent.info.WorldInfo;
//import adf.core.agent.module.ModuleManager;
//import hfutinvincible.anno.AgentType;
//import hfutinvincible.anno.Autowired;
//import hfutinvincible.anno.HfutTool;
//import hfutinvincible.message.AgentConditionMessage;
//import hfutinvincible.message.CvConditionMessage;
//import hfutinvincible.tool.LifeCycle;
//import hfutinvincible.tool.UnavailableTargetTool;
//import hfutinvincible.tool.comm.MessageManageTool;
//import hfutinvincible.tool.misc.OptionsTool;
//import org.apache.log4j.Logger;
//import rescuecore2.standard.entities.*;
//import rescuecore2.worldmodel.EntityID;
//
//import javax.annotation.Nonnull;
//import javax.annotation.Nullable;
//import java.util.HashMap;
//import java.util.HashSet;
//import java.util.Map;
//import java.util.Set;
//import java.util.function.Predicate;
//import java.util.stream.Stream;
//
//@HfutTool
//@AgentType({AmbulanceTeam.class, PoliceForce.class, FireBrigade.class})
//public class TimeToDeadTool implements LifeCycle {
//    public Map<EntityID, TimeToDeadRecord> recordMap;
//    private Set<EntityID> timeToDeadChangedCvIdsInSight;
//
//    @Autowired
//    AgentInfo agentInfo;
//    @Autowired
//    WorldInfo worldInfo;
//    @Autowired
//    ScenarioInfo scenarioInfo;
//    @Autowired
//    ModuleManager moduleManager;
//    @Autowired
//    DevelopData developData;
//    @Autowired
//    Logger logger;
//    //Tools
//    @Autowired
//    MessageManageTool messageManageTool;
//    @Autowired
//    HumanPositionTool humanPositionTool;
//    @Autowired
//    StuckInfoTool stuckInfoTool;
//    @Autowired
//    UnavailableTargetTool unavailableTargetTool;
//
//    public TimeToDeadTool(){
//        this.recordMap = new HashMap<>();
//        this.timeToDeadChangedCvIdsInSight = new HashSet<>();
//    }
//
//    @Override
//    public void updateInfo(MessageManager messageManager) {
//        this.humanPositionTool.updateInfo(messageManager);
//        this.messageManageTool.updateInfo(messageManager);
//        this.stuckInfoTool.updateInfo(messageManager);
//        this.unavailableTargetTool.updateInfo(messageManager);
//
//        int time = agentInfo.getTime();
//
//        // 先从视野更新
//        Set<EntityID> changedEntities = worldInfo.getChanged().getChangedEntities();
//        timeToDeadChangedCvIdsInSight.clear();
//        changedEntities.stream()
//                .map(worldInfo::getEntity)
//                .filter(Human.class::isInstance)
//                .map(Human.class::cast)
//                .forEach(human -> {
//                    int hp = human.isHPDefined() ? human.getHP() : 0;
//                    int damage = human.isDamageDefined() ? human.getDamage() : 0;
//                    // 由于从视野看到的 hp 的 damage 都有误差，因此可能一个人受伤了，但看上去没受伤。
//                    // 因此如果一个 damage 为 0 Civilian 没被卡也没被埋，但是一直呆在原地不动（stayCount 超过阈值），
//                    // 则将其 damage 设为误差限
//                    // TODO 虽然 damage 可能看不到，但是 damage 造成的 hp 下降累计到一定程度是可观测到的
////                        if (damage == 0
////                                && human instanceof Civilian
////                                && !stuckInfoTool.isStuck(human.getID()) // 没被卡
////                                && (!(human.isBuriednessDefined()) || human.getBuriedness() == 0) // 没被埋
////                                && humanPositionTool.getStayCount(human.getID()) > params.getInt("hurtStayCountThreshold")) {
////                            EntityID position = humanPositionTool.getPosition(human.getID());
////                            unavailableTargetTool.setUnavailableAndWait(
////                                    position == null ? human.getPosition() : position,
////                                    UnavailableType.noHurtNotSure,
////                                    scenarioInfo.getPerceptionLosPrecisionHp() / scenarioInfo.getPerceptionLosPrecisionDamage(),
////                                    true
////                            );
////                            logger.info("damage of civilian: " + human.getID() + "is " + damage + "; hp is: " + hp);
////                        }
//                    if (damage == 0
//                            && human instanceof Civilian
//                            && !stuckInfoTool.isStuck(human.getID()) // 没被卡
//                            && (!(human.isBuriednessDefined()) || human.getBuriedness() == 0) // 没被埋
//                            && humanPositionTool.getStayCount(human.getID()) > params.getInt("hurtStayCountThreshold")) {
//                        damage = scenarioInfo.getPerceptionLosPrecisionDamage();
//                    }
////                        int timeToDead = hp == 0
////                                ? 0
////                                : (damage == 0
////                                ? Integer.MAX_VALUE
////                                : hp / damage + ((hp % damage) != 0 ? 1 : 0));
//////                        timeToDead = Math.min(timeToDead, OptionsTool.KERNEL_TIMESTEPS);
////                        logger.info("civilian(" + human.getID() + ")'s timeToDead is " + timeToDead);
////                        updateTimeToDead(human instanceof Civilian, human.getID(), timeToDead, time, InfoSource.WITNESS);
//                    int timeToDead = hp == 0
//                            ? 0
//                            : (damage == 0
//                            ? OptionsTool.KERNEL_TIMESTEPS
//                            : hp / damage + ((hp % damage) != 0 ? 1 : 0));
//                    timeToDead = Math.min(timeToDead, OptionsTool.KERNEL_TIMESTEPS);
//                    updateTimeToDead(human instanceof Civilian, human.getID(), timeToDead, time, InfoSource.WITNESS);
//                });
//
//        // 然后从消息更新
//        messageManageTool.getReceivedMessages(AgentConditionMessage.class)
//                .forEach(m -> updateTimeToDead(
//                        false,
//                        m.getAgentId(),
//                        m.getTimeToDead(),
//                        time - 1,
//                        InfoSource.FROM_RADIO
//                ));
//
//        messageManageTool.getReceivedMessages(CvConditionMessage.class)
//                .forEach(m -> updateTimeToDead(
//                        true,
//                        m.getCvId(),
//                        m.getTimeToDead(),
//                        time - 1,
//                        InfoSource.FROM_RADIO
//                ));
//    }
//    /**
//     * 查询一个人还可存活时间的最新记录（不是本周期的）
//     * @param humanId 要查询的人的 EntityId
//     * @return null，不知道；否则为此人还能活的周期数最新记录（不是本周期的）
//     */
//    @Nullable
//    public Integer getLatestTimeToDead(@Nonnull EntityID humanId) {
//        TimeToDeadRecord record = this.recordMap.get(humanId);
//        return record == null
//                ? null
//                : record.getTimeToDead();
//    }
//
//    /**
//     * 查询一个人还可存活时间的最新记录，并估计从本周期起还可存活的时间
//     * @param humanId 要查询的人的 EntityId
//     * @return null，不知道；否则为从本周期起还可存活的时间
//     */
//    @Nullable
//    public Integer getTimeToDeadNowEstimate(@Nonnull EntityID humanId) {
//        TimeToDeadRecord record = this.recordMap.get(humanId);
//        if (record == null)
//            return null;
//        int timeToDead = record.getTimeToDead();
//        return timeToDead == OptionsTool.KERNEL_TIMESTEPS
//                ? OptionsTool.KERNEL_TIMESTEPS
//                : timeToDead - (agentInfo.getTime() - record.getLastUpdateTime());
//    }
//
//    /**
//     * 返回最新的关于一个人被卡住的记录的时间
//     * <br>
//     * 如果不知道这个人的信息，返回 -1
//     */
//    public int getUpdateTime(@Nonnull EntityID humanId) {
//        TimeToDeadRecord record = this.recordMap.get(humanId);
//        return record == null
//                ? -1
//                : record.getLastUpdateTime();
//    }
//
//    /**
//     * 返回最新的关于一个人被卡住的记录的时间
//     * <br>
//     * 如果不知道这个人的信息，返回 -1
//     */
//    @Nullable
//    public InfoSource getInfoSource(@Nonnull EntityID humanId) {
//        TimeToDeadRecord record = this.recordMap.get(humanId);
//        return record == null
//                ? null
//                : record.getInfoSource();
//    }
//
//    /**
//     * 获得所有确定受伤了而且没死的人的 EntityID
//     */
//    @Nonnull
//    public Stream<EntityID> getHurtHumanIds() {
//        return getHumanIdsIf(timeToDead ->
//                timeToDead != null
//                        && timeToDead < OptionsTool.KERNEL_TIMESTEPS
//                        && timeToDead > 0
//        );
//    }
//
//    /**
//     * 返回 TimeToDead 属性满足给定谓词的 EntityIDs
//     * @param timeToDeadPredicate 关于 TimeToDead 的谓词
//     */
//    @Nonnull
//    public Stream<EntityID> getHumanIdsIf(Predicate<Integer> timeToDeadPredicate) {
//        return this.recordMap.entrySet()
//                .stream()
//                .filter(e -> {
//                    Integer timeToDead = e.getValue().getTimeToDead();
//                    return timeToDeadPredicate.test(timeToDead);
//                })
//                .map(Map.Entry::getKey);
//    }
//
//    /**
//     * 返回本周期视野范围内存活时间改变的所有 Civilian 的 EntityID
//     */
//    @Nonnull
//    public Set<EntityID> getTimeToDeadChangedCvIdsInSight() {
//        return timeToDeadChangedCvIdsInSight;
//    }
//
//    private void updateTimeToDead(
//            boolean isCivilian,
//            @Nonnull EntityID humanId,
//            int timeToDead,
//            int time,
//            @Nonnull InfoSource infoSource
//    ) {
//        // 世界模型里没有这个人，不更新
//        Human human = (Human) worldInfo.getEntity(humanId);
//        if (human == null)
//            return;
//        TimeToDeadRecord record = this.recordMap.get(humanId);
//        // 还没有记录
//        if (record == null) {
//            record = new TimeToDeadRecord();
//            record.setLastUpdateTime(time);
//            record.setTimeToDead(timeToDead);
//            record.setInfoSource(infoSource);
//            this.recordMap.put(humanId, record);
//            logger.info("update: humanId=" + human + ", from=" + null + ", to=" + record);
//        } else {
//            // TODO 使用更详细的更新策略
//            // 已经有记录了
//            boolean update = false;
//            if (isCivilian) { // 是 CV，所有信息其实都是自己或别人看到的
//                // 只有新信息更新，才更新
//                if (time > record.getLastUpdateTime())
//                    update = true;
//            } else { // 是 AT、FB、PF 这些智能体
//                // 已有记录是通过消息收到的
//                if (record.getInfoSource() != InfoSource.WITNESS) {
//                    // 新信息也是通过消息收到的，而且更新，则更新
//                    if (infoSource != InfoSource.WITNESS && time > record.getLastUpdateTime())
//                        update = true;
//                        // 否则新信息是看到的，看到的消息需要比消息收到的消息新 5 个周期以上才更新
//                    else if (time - record.getLastUpdateTime() > 5)
//                        update = true;
//                } else { // 已有记录是看到的
//                    // 新信息也是看到的，而且更新，则更新
//                    if (infoSource == InfoSource.WITNESS && time > record.getLastUpdateTime())
//                        update = true;
//                        // 否则是通过消息收到的
//                        // 消息收到的信息只要不滞后于已有记录 5 个周期以上，都更新
//                    else if (time >= record.getLastUpdateTime() || record.getLastUpdateTime() - time <= 5)
//                        update = true;
//                }
//            }
//            if (update) {
//                StringBuilder sb = new StringBuilder();
//                sb.append("update: humanId=" + human + ", from=" + record);
//                if (isCivilian && record.getTimeToDead() != timeToDead && infoSource == InfoSource.WITNESS)
//                    this.timeToDeadChangedCvIdsInSight.add(humanId);
//                record.setTimeToDead(timeToDead);
//                record.setLastUpdateTime(time);
//                record.setInfoSource(infoSource);
//                sb.append(", to=" + record);
//                logger.info(sb);
//            }
//        }
//    }
//}
//
