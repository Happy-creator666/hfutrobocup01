package hfutinvincible.tool.human;

import adf.core.agent.action.Action;
import adf.core.agent.action.common.ActionMove;
import adf.core.agent.communication.MessageManager;
import adf.core.agent.develop.DevelopData;
import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.ScenarioInfo;
import adf.core.agent.info.WorldInfo;
import adf.core.agent.module.ModuleManager;
import hfutinvincible.anno.After;
import hfutinvincible.anno.AgentType;
import hfutinvincible.anno.Autowired;
import hfutinvincible.anno.HfutTool;
import hfutinvincible.message.AgentConditionMessage;
import hfutinvincible.message.CvConditionMessage;
import hfutinvincible.tool.LifeCycle;
import hfutinvincible.tool.comm.MessageManageTool;
import hfutinvincible.tool.path.SearchPathTool;
import org.apache.log4j.Logger;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

@HfutTool
@AgentType({AmbulanceTeam.class, PoliceForce.class, FireBrigade.class})
public class HumanPositionTool implements LifeCycle {

    @Autowired
    Logger logger;

    @Autowired
    AgentInfo agentInfo;
    @Autowired
    WorldInfo worldInfo;
    @Autowired
    ScenarioInfo scenarioInfo;
    @Autowired
    ModuleManager moduleManager;
    @Autowired
    DevelopData developData;

    public final Map<EntityID, HumanPositionRecord> recordMap;
    private final Map<EntityID, Set<EntityID>> areaId2humanOnIt;
    private final Set<EntityID> posChangedCvIdsInSight;
    // 记录我访问一个 area 的次数
    private final Map<EntityID, Integer> lastVisitTimeMap;

    private final Map<EntityID, Integer> stayCountMap;

//    // Tools
//    private MessageManageTool messageTool;
//    private SearchPathTool searchPathTool;
//    private StuckInfoTool stuckInfoTool;

    @Autowired MessageManageTool messageTool;
    @Autowired
    SearchPathTool searchPathTool;
    @Autowired StuckInfoTool stuckInfoTool;
    public HumanPositionTool(){
        this.recordMap = new HashMap<>();
        this.posChangedCvIdsInSight = new HashSet<>();
        this.areaId2humanOnIt = new HashMap<>();

        this.stayCountMap = new HashMap<>();
        this.lastVisitTimeMap = new HashMap<>();
    }

    @Override
    @After({"MessageManageTool"})
    public void updateInfo(MessageManager messageManager) {
        int time = agentInfo.getTime();
        Set<EntityID> changedEntities = worldInfo.getChanged().getChangedEntities();

//            Point2D p = new Point2D(agentInfo.getX(), agentInfo.getY());
//            Collection<StandardEntity> nearbyEntities =
//                    worldInfo.getObjectsInRange((int) agentInfo.getX(), (int) agentInfo.getY(), OptionsTool.VIEW_DISTANCE);
//            logger.debug(lineOfSightPerception.findVisibleAreas(null, p, nearbyEntities));

        if (agentInfo.getTime() == 1) {
            // 我知道每个智能体的初始位置
            worldInfo.getEntitiesOfType(
                            StandardEntityURN.AMBULANCE_TEAM,
                            StandardEntityURN.POLICE_FORCE,
                            StandardEntityURN.FIRE_BRIGADE)
                    .stream()
                    .map(Human.class::cast)
                    .forEach(human -> updatePosition(
                            false,
                            human.getID(),
                            human.getPosition(),
                            time,
                            InfoSource.WITNESS // 就认为是看到的
                    ));
        }

        // 先从视野更新
        this.posChangedCvIdsInSight.clear();
        changedEntities.stream() // 先更新 at
                .map(worldInfo::getEntity)
                .filter(AmbulanceTeam.class::isInstance)
                .map(AmbulanceTeam.class::cast)
                .forEach(at -> updatePosition(
                        false,
                        at.getID(),
                        at.getPosition(), // 只可能是 Area
                        time,
                        InfoSource.WITNESS
                ));

        changedEntities.stream() // 再更新其他 human
                .map(worldInfo::getEntity)
                .filter(e -> (e instanceof Human) && !(e instanceof AmbulanceTeam))
                .map(Human.class::cast)
                .forEach(human -> updatePosition(
                        human instanceof Civilian,
                        human.getID(),
                        human.getPosition(),
                        time,
                        InfoSource.WITNESS
                ));

        Area myPosArea = getMyPositionArea();
        for (Map.Entry<EntityID, HumanPositionRecord> e : recordMap.entrySet()) {
            EntityID humanId = e.getKey();
            // 忽略我自己
            if (humanId.equals(agentInfo.getID()))
                continue;
            HumanPositionRecord record = e.getValue();
            Area posArea = getPositionArea(humanId);
            // 如果根据记录, 这个人在我当前所在的 area, 但是我看不到他, 说明这个人的位置信息有误, 应删除
            if (Objects.equals(myPosArea, posArea) && !changedEntities.contains(humanId)) {
                record.setHumanPosId(null); // 清除原有错误记录
                record.setLastUpdateTime(time);
                record.setInfoSource(InfoSource.WITNESS);
            }
        }

        // 再从消息更新
        messageTool.getReceivedMessages(AgentConditionMessage.class)
                .stream() // 忽略视野范围内的人
                .filter(m -> !changedEntities.contains(m.getAgentId()))
                // 不考虑消息转发，消息携带的是上一周期的信息
                .forEach(m -> updatePosition(
                        false,
                        m.getAgentId(),
                        m.getPosId(),
                        time - 1,
                        InfoSource.FROM_RADIO
                ));

        messageTool.getReceivedMessages(CvConditionMessage.class)
                .stream() // 忽略视野范围内的人
                .filter(m -> !changedEntities.contains(m.getCvId()))
                // 不考虑消息转发，消息携带的是上一周期的信息
                .forEach(m -> updatePosition(
                        true,
                        m.getCvId(),
                        m.getPosId(),
                        time - 1,
                        InfoSource.FROM_RADIO
                ));

        // 更新 visitCount
        Action prevAction = agentInfo.getExecutedAction(-1);
        if (prevAction instanceof ActionMove am) {
            java.util.List<EntityID> path = am.getPath();
            if (path.isEmpty())
                return;
            Area curPosArea = getMyPositionArea();
            int curIndex = path.indexOf(curPosArea.getID());
            // != -1 走出去了
            if (curIndex != -1) {
                List<EntityID> visitedAreaIds = path.subList(0, curIndex + 1);
                logger.info("Update last visit time: " + visitedAreaIds);
                visitedAreaIds.forEach(id ->
                        this.lastVisitTimeMap.put(id, time));
            }
        }

        // 更新 areaId2humanOnIt
        areaId2humanOnIt.values().forEach(Set::clear);
        for (StandardEntity e : worldInfo.getAllEntities()) {
            if (!(e instanceof Human))
                continue;
            EntityID humanId = e.getID();
            EntityID posAreaId = getPositionAreaId(humanId);
            if (posAreaId != null) {
                if (!areaId2humanOnIt.containsKey(posAreaId))
                    areaId2humanOnIt.put(posAreaId, new HashSet<>());
                areaId2humanOnIt.get(posAreaId).add(humanId);
            }
        }

        logger.info("recordMap=" + this.recordMap);
        logger.info("posChangedCvIdsInSight=" + this.posChangedCvIdsInSight);

    }

    /**
     * 获得访问一个 area 的最新时间
     * @param areaId
     * @return
     */
    public int getLastVisitTime(@Nonnull EntityID areaId) {
        return lastVisitTimeMap.getOrDefault(areaId, 0);
    }

    /**
     * 获得一个人有多少周期呆在原地不动
     */
    public int getStayCount(@Nonnull EntityID humanId) {
        return stayCountMap.getOrDefault(humanId, 0);
    }

    /**
     * 获得一个 Human 的 Position
     * @param humanId Human 的 EntityID
     * @return 如果 Human 被装在 AT 上，则返回 AT，否则返回 Human 脚下的 EntityID
     * ，如果不知道这个人的位置，返回 null
     */
    @Nullable
    public EntityID getPosition(@Nonnull EntityID humanId) {
        HumanPositionRecord record = this.recordMap.get(humanId);
        return record == null
                ? null
                : record.getHumanPosId();
    }

    /**
     * 获得一个 area 上的所有人
     */
    @Nonnull
    public Set<EntityID> getHumanIdsOnArea(@Nonnull EntityID areaId) {
        return areaId2humanOnIt.getOrDefault(areaId, new HashSet<>());
    }

    /**
     * 获得一个 Human 脚下的 Area
     * @param humanId Human 的 EntityID
     * @return 如果 Human 被装在 AT 上，则返回 AT 脚下的 EntityID，否则返回 Human 脚下的 EntityID
     */
    @Nullable
    public EntityID getPositionAreaId(@Nonnull EntityID humanId) {
        HumanPositionRecord record = this.recordMap.get(humanId);
        if (record == null)
            return null;
        EntityID posId = record.getHumanPosId();
        if (posId == null)
            return null;
        StandardEntity posEntity = worldInfo.getEntity(posId);
        if (posEntity instanceof AmbulanceTeam)
            return getPosition(posId);
        return posId;
    }

    /**
     * 获得一个 Human 脚下的 Area
     * @param humanId Human 的 EntityID
     * @return 如果 Human 被装在 AT 上，则返回 AT 脚下的 EntityID，否则返回 Human 脚下的 EntityID
     */
    @Nullable
    public Area getPositionArea(@Nonnull EntityID humanId) {
        HumanPositionRecord record = this.recordMap.get(humanId);
        if (record == null)
            return null;
        EntityID posId = record.getHumanPosId();
        if (posId == null)
            return null;
        StandardEntity posEntity = worldInfo.getEntity(posId);
        if (posEntity instanceof AmbulanceTeam) {
            EntityID atPosId = getPosition(posId);
            return atPosId == null
                    ? null
                    : (Area) worldInfo.getEntity(atPosId);
        } else return (Area) worldInfo.getEntity(posId);
    }

    /**
     * 获得一个 Human 脚下的 Area
     * @return 如果 Human 被装在 AT 上，则返回 AT 脚下的 EntityID，否则返回 Human 脚下的 EntityID
     */
    @Nonnull
    public Area getMyPositionArea() {
        Area area = getPositionArea(agentInfo.getID());
        assert area != null;
        return area;
    }

    /**
     * 获得一个 Human 脚下的 Area
     * @return 如果 Human 被装在 AT 上，则返回 AT 脚下的 EntityID，否则返回 Human 脚下的 EntityID
     */
    @Nonnull
    public EntityID getMyPositionAreaId() {
        return getMyPositionArea().getID();
    }

    /**
     * 返回最新的关于一个人位置的记录的时间
     * <br>
     * 如果不知道这个人的信息，返回 -1
     */
    public int getUpdateTime(@Nonnull EntityID humanId) {
        HumanPositionRecord record = this.recordMap.get(humanId);
        return record == null
                ? -1
                : record.getLastUpdateTime();
    }

    /**
     * 返回最新的关于一个人位置的记录来自哪里
     */
    @Nullable
    public InfoSource getInfoSource(@Nonnull EntityID humanId) {
        HumanPositionRecord record = this.recordMap.get(humanId);
        return record == null
                ? null
                : record.getInfoSource();
    }

    /**
     * 返回本周期视野范围内位置改变的所有 Civilian 的 EntityID
     */
    public Set<EntityID> getPosChangedCvIdsInSight() {
        return posChangedCvIdsInSight;
    }

    private void updatePosition(
            boolean isCivilian,
            @Nonnull EntityID humanId,
            @Nonnull EntityID humanPosId,
            int time,
            @Nonnull InfoSource infoSource
    ) {
        Human human = (Human) worldInfo.getEntity(humanId);
        // Agent 应该我都知道，如果 human 为 null，只可能是 cv
        // 创建这个 Civilian 并将其加入世界模型
        if (human == null) {
            human = new Civilian(humanId);
            worldInfo.addEntity(human);
        }
        HumanPositionRecord record = this.recordMap.get(humanId);
        StandardEntity posEntity = worldInfo.getEntity(humanPosId);
        // 还没有记录
        if (record == null) {
            record = new HumanPositionRecord();
            record.setLastUpdateTime(time);
            record.setHumanPosId(humanPosId);
            record.setInfoSource(infoSource);
            this.recordMap.put(humanId, record);
            human.setPosition(humanPosId); // 更新世界模型
            updateStayCount(humanId, null, humanPosId, agentInfo.getTime());
            logger.info("update: humanId=" + human + ", from=" + null + ", to=" + record);
        } else
            // 已经有记录了
            // 如果传入的记录更新，则更新之
            if (time > record.getLastUpdateTime()) {
                EntityID _fromPosId = record.getHumanPosId();
                int _prevUpdateTime = record.getLastUpdateTime();
                StringBuilder sb = new StringBuilder();
                sb.append("update: humanId=" + human + ", from=" + record);
                // 更新本周期视野内位置改变的 Cv 的集合
                if (isCivilian
                        && !humanPosId.equals(record.getHumanPosId())
                        && infoSource == InfoSource.WITNESS)
                    this.posChangedCvIdsInSight.add(humanId);
                record.setHumanPosId(humanPosId);
                record.setLastUpdateTime(time);
                record.setInfoSource(infoSource);
                updateStayCount(humanId, _fromPosId, humanPosId, _prevUpdateTime);
                // 更新世界模型
                human.setPosition(humanPosId);
                sb.append(", to=" + record);
                logger.info(sb);
            }
    }

    private void updateStayCount(
            @Nonnull EntityID humanId,
            @Nullable EntityID fromPos,
            @Nullable EntityID toPos,
            int lastUpdateTime
    ) {
        // 忽略所有非人
        StandardEntity entity = worldInfo.getEntity(humanId);
        if (!(entity instanceof Human human))
            return;
        // fromPos 还不知道，或者 invalidate toPos，stayCount 归零
        if (fromPos == null || toPos == null) {
            stayCountMap.put(humanId, 0);
            return;
        }
        StandardEntity posEntity = worldInfo.getEntity(toPos);
        if (posEntity instanceof AmbulanceTeam) { // TODO 直接拿 at 的 stayCount 替代是否合适？
            stayCountMap.put(humanId, stayCountMap.getOrDefault(toPos, 0));
            return;
        }
        if (fromPos.equals(toPos)) {
            int stayCount = agentInfo.getTime() - lastUpdateTime;
            stayCountMap.put(humanId, stayCountMap.getOrDefault(humanId, 0) + stayCount);
        } else stayCountMap.put(humanId, 0);
    }
}
