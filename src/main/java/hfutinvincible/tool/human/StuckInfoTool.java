package hfutinvincible.tool.human;

import adf.core.agent.action.Action;
import adf.core.agent.action.common.ActionMove;
import adf.core.agent.action.common.ActionRest;
import adf.core.agent.communication.MessageManager;
import adf.core.agent.develop.DevelopData;
import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.ScenarioInfo;
import adf.core.agent.info.WorldInfo;
import adf.core.agent.module.ModuleManager;
import hfutinvincible.anno.AgentType;
import hfutinvincible.anno.Autowired;
import hfutinvincible.anno.HfutTool;
import hfutinvincible.debug.Debugger;
import hfutinvincible.debug.Painter;
import hfutinvincible.debug.paintable.Paintable;
import hfutinvincible.message.AgentConditionMessage;
import hfutinvincible.message.CvConditionMessage;
import hfutinvincible.tool.LifeCycle;
import hfutinvincible.tool.area.ToJts;
import hfutinvincible.tool.comm.MessageManageTool;
import hfutinvincible.tool.edge.AreaPassableTool;
import hfutinvincible.tool.edge.HFUTEdge;
import hfutinvincible.tool.misc.HFUTGeometryTool;
import hfutinvincible.tool.misc.MeasureLengthTool;
import hfutinvincible.tool.misc.OptionsTool;
import hfutinvincible.tool.path.Path;
import hfutinvincible.tool.path.PathUtilTool;
import hfutinvincible.tool.path.SearchPathTool;
import org.apache.log4j.Logger;
import org.locationtech.jts.geom.*;
import rescuecore2.misc.geometry.Point2D;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.stream.Stream;

@HfutTool
@AgentType({AmbulanceTeam.class, FireBrigade.class, PoliceForce.class})
public class StuckInfoTool implements LifeCycle {
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
    public final Map<EntityID, StuckTypeRecord> recordMap;

    /**
     * 视野范围内卡住情况改变了的 Cv 的 EntityIDs
     */
    private final Set<EntityID> stuckTypeChangedCvIdsInSight;

    @Autowired
    MessageManageTool messageTool;
    @Autowired
    MeasureLengthTool measureLengthTool;
    @Autowired
    PathUtilTool pathUtilTool;
    @Autowired
    HumanPositionTool humanPositionTool;
    @Autowired
    AreaPassableTool areaPassableTool;
    @Autowired
    SearchPathTool searchPathTool;



    private static final GeometryFactory GF = new GeometryFactory();

    public StuckInfoTool(){
        this.recordMap = new HashMap<>();
        this.stuckTypeChangedCvIdsInSight =new HashSet<>();
    }

    @Override
    public void updateInfo(MessageManager messageManager) {
        this.humanPositionTool.updateInfo(messageManager);
        this.messageTool.updateInfo(messageManager);

        int time = agentInfo.getTime();
        final Set<EntityID> changedEntities = worldInfo.getChanged().getChangedEntities();
        final Area mePosArea = humanPositionTool.getMyPositionArea();

        // 处理我自己
        Human me = (Human) agentInfo.me();
        if (isStuckInBlockadeOrInBuildingInSight(me, mePosArea))
            updateStuckType(false, me.getID(), StuckType.STUCK_IN_BLOCKADE_OR_BUILDING, time, InfoSource.MY_SELF);
        else if (amIStuckWhenMoving())
            updateStuckType(false, me.getID(), StuckType.STUCK_WHEN_MOVING, time, InfoSource.MY_SELF);
        else updateStuckType(false, me.getID(), StuckType.NOT_STUCK, time, InfoSource.MY_SELF);

        // 根据看到的更新，不处理我自己
        this.stuckTypeChangedCvIdsInSight.clear();
        changedEntities.stream()
                .filter(id -> !agentInfo.getID().equals(id)) // 不考虑我自己
                .map(worldInfo::getEntity)
                .filter(Human.class::isInstance)
                .map(Human.class::cast)
                .forEach(human -> {
                    if (human instanceof Civilian) { // 看到的是村民，直接更新
                        if (isStuckInBlockadeOrInBuildingInSight(human, mePosArea))
                            updateStuckType(true, human.getID(), StuckType.STUCK_IN_BLOCKADE_OR_BUILDING, time, InfoSource.WITNESS);
                        else if (isStuckWhenMovingInSight(human))
                            updateStuckType(true, human.getID(), StuckType.STUCK_WHEN_MOVING, time, InfoSource.WITNESS);
                        else updateStuckType(true, human.getID(), StuckType.NOT_STUCK, time, InfoSource.WITNESS);
                    } else if (!(human instanceof PoliceForce)) { // PF 永远不会被卡 / 被卡了自己会出来，所以忽略所有 PF
                        if (isStuckInBlockadeOrInBuildingInSight(human, mePosArea))
                            updateStuckType(false, human.getID(), StuckType.STUCK_IN_BLOCKADE_OR_BUILDING, time, InfoSource.WITNESS);
                            // 移动中被卡住
                        else if (isStuckWhenMovingInSight(human))
                            updateStuckType(false, human.getID(), StuckType.STUCK_WHEN_MOVING, time, InfoSource.WITNESS);
                            // 否则 "在我看来" 这个智能体应该没被卡住
                        else updateStuckType(false, human.getID(), StuckType.NOT_STUCK, time, InfoSource.WITNESS);
                    }
                });

        // 然后从消息更新
        messageTool.getReceivedMessages(AgentConditionMessage.class)
                .forEach(m -> updateStuckType(
                        false,
                        m.getAgentId(),
                        m.getStuckType(),
                        time - 1,
                        m.isRadio() ? InfoSource.FROM_RADIO : InfoSource.FROM_VOICE));

        messageTool.getReceivedMessages(CvConditionMessage.class)
                .forEach(m -> updateStuckType(
                        true,
                        m.getCvId(),
                        m.getStuckType(),
                        time - 1,
                        m.isRadio() ? InfoSource.FROM_RADIO : InfoSource.FROM_VOICE));

        logger.info("stuck humans=" + getStuckHumanIds(OptionsTool.IGNORE_STUCK_HUMAN_AFTER).toList());

//            logger.debug("recordMap=" + recordMap);

//            // 调试器上显示被卡住的人
//            var stuckHumanLayer = HFUTDebugger.getMiscLayerOf(agentInfo, StuckHumanLayer.class);
//            if (stuckHumanLayer != null)
//                getStuckHumanIds(OptionsTool.IGNORE_STUCK_HUMAN_AFTER).forEach(stuckHumanId -> {
//                    StuckType stuckType = getStuckType(stuckHumanId);
//                    if (stuckType == null)
//                        return;
//                    Human stuckHuman = (Human) worldInfo.getEntity(stuckHumanId);
//                    if (stuckHuman == null || !stuckHuman.isXDefined() || !stuckHuman.isYDefined())
//                        return;
//                    double x = stuckHuman.getX();
//                    double y = stuckHuman.getY();
//                    stuckHumanLayer
//                            .addText(x, y + 100, stuckType.toString(), Color.MAGENTA);
//                });

    }

    /**
     * 查询一个人被卡住的情况
     * @param humanId 要查询的人的 EntityId
     * @return null，不知道
     */
    @Nullable
    public StuckType getStuckType(@Nonnull EntityID humanId) {
        StuckTypeRecord record = this.recordMap.get(humanId);
        return record == null
                ? null
                : record.getStuckType();
    }

    /**
     * 判断一个人是否被卡住
     */
    public boolean isStuck(@Nonnull EntityID humanId) {
        StuckType stuckType = getStuckType(humanId);
        return stuckType != StuckType.NOT_STUCK;
    }

    /**
     * 返回最新的关于一个人被卡住的记录的时间
     * <br>
     * 如果不知道这个人的信息，返回 -1
     */
    public int getUpdateTime(@Nonnull EntityID humanId) {
        StuckTypeRecord record = this.recordMap.get(humanId);
        return record == null
                ? -1
                : record.getLastUpdateTime();
    }

    /**
     * 返回最新的关于一个人被卡住的记录来自哪里
     */
    @Nullable
    public InfoSource getInfoSource(@Nonnull EntityID humanId) {
        StuckTypeRecord record = this.recordMap.get(humanId);
        return record == null
                ? null
                : record.getInfoSource();
    }

    /**
     * 获得所有被卡住的人的 EntityID（会筛去位置 undefined 的）
     * @param timeLimit 忽略超过指定时间的
     */
    @Nonnull
    public Stream<EntityID> getStuckHumanIds(int timeLimit) {
        int time = agentInfo.getTime();
        return this.recordMap.entrySet()
                .stream()
                .filter(e -> {
                    StuckType stuckType = e.getValue().getStuckType();
                    int lastUpdateTime = e.getValue().getLastUpdateTime();
                    boolean test = (stuckType == StuckType.STUCK_IN_BLOCKADE_OR_BUILDING ||
                            stuckType == StuckType.STUCK_WHEN_MOVING)
                            && time - lastUpdateTime < timeLimit;
                    return test;
                })
                .map(Map.Entry::getKey)
                .filter(humanId -> {
                    boolean test = humanPositionTool.getPosition(humanId) != null;
                    return test;
                });
    }

    /**
     * 判断一个视野内的 Civilian 是否被卡在路上
     */
    public boolean isStuckWhenMovingInSight(Human human) {

        // PF 永远不会移动时卡住
        if (human instanceof PoliceForce)
            return false;

        // 下面的计算要用到 human 的坐标，防止意外检查一下
        if (!human.isXDefined() || !human.isYDefined())
            return false;

        EntityID humanPosAreaId = humanPositionTool.getPositionAreaId(human.getID());
        if (humanPosAreaId == null)
            return false;
        Area humanPosArea = (Area) worldInfo.getEntity(humanPosAreaId);
        if (humanPosArea == null)
            return false;
        // 计算 human 所在 area 和周围所有 area 上的 blockades
        List<EntityID> checkBlockades = new ArrayList<>();
        if (humanPosArea.isBlockadesDefined())
            checkBlockades.addAll(humanPosArea.getBlockades());
        for (EntityID neighborId : humanPosArea.getNeighbours()) {
            StandardEntity neighbor = worldInfo.getEntity(neighborId);
            if (!(neighbor instanceof Area neighborArea))
                continue;
            if (neighborArea.isBlockadesDefined())
                checkBlockades.addAll(neighborArea.getBlockades());
        }
        // 没有 blockade，不可能卡住
        if (checkBlockades.isEmpty())
            return false;
        Coordinate humanPosCoord = new CoordinateXY(human.getX(), human.getY());

//        StuckHumanLayer stuckHumanLayer = HFUTDebugger.getMiscLayerOf(agentInfo, StuckHumanLayer.class);
        // 对每个 blockade
        boolean isTooCloseToBlockade = false;
        for (EntityID blockadeId : checkBlockades) {
            // 如果 human 距离某个 blockade 某条边的距离小于阈值，则认为卡住
            Blockade blockade = (Blockade) worldInfo.getEntity(blockadeId);
            if (blockade == null)
                continue;
            int[] apexes = blockade.getApexes();
            for (int i = 0; i < apexes.length - 3; i += 2) {
                int apexX1 = apexes[i];
                int apexY1 = apexes[i + 1];
                int apexX2 = apexes[i + 2];
                int apexY2 = apexes[i + 3];
                LineSegment edge = new LineSegment(apexX1, apexY1, apexX2, apexY2);
                int distance = (int) edge.distance(humanPosCoord);
                if (distance < OptionsTool.CV_STUCK_DISTANCE) {
                    isTooCloseToBlockade = true;
//                    if (stuckHumanLayer != null) {
//                        stuckHumanLayer.addText(human.getX(), human.getY() - 100, String.valueOf(distance), Color.MAGENTA);
//                    }
                    break;
                }
            }
        }

        // 如果 cv 和障碍太接近，认为被卡住
        if (human instanceof Civilian)
            return isTooCloseToBlockade;

        // AT 和 FB 有我们写的绕路策略，因此只有在距离障碍非常接近，而且 stayCount 过大时才认为被卡住
        if (!isTooCloseToBlockade)
            return false;
        int stayCount = humanPositionTool.getStayCount(human.getID());
        int maxTravelTime = getMaxTravelTime(humanPosAreaId);
        if (stayCount > maxTravelTime + OptionsTool.FB_AT_MAX_TRAVEL_TIME_DELTA)
            return true;
        return false;
    }

    /**
     * 计算一个点到一个 blockade 的最近距离
     * @implNote 计算给定点到 blockade 所有边的距离，然后取最小值
     */
    public double distanceToBlockade(Coordinate fromCoord, Blockade blockade) {
        Geometry blockadePolygon = ToJts.fromRcrsBlockade(blockade);
        return GF.createPoint(fromCoord).distance(blockadePolygon);
    }

    /**
     * 计算一个点到一个 blockade 的最近距离
     * @implNote 计算给定点到 blockade 所有边的距离，然后取最小值
     */
    public double distanceToBlockade(double fromX, double fromY, Blockade blockade) {
        Coordinate fromCoord = new CoordinateXY(fromX, fromY);
        return distanceToBlockade(fromCoord, blockade);
    }

    /**
     * 判断我自己是否移动时被卡住了
     */
    public boolean amIStuckWhenMoving() {
        logger.info("Determine whether I am stuck...");
        if (agentInfo.getTime() < scenarioInfo.getKernelAgentsIgnoreuntil()) {
            logger.info("agentTime < " + scenarioInfo.getKernelAgentsIgnoreuntil() + " can't be stuck");
            return false;
        }
        // PF 永远不会移动时卡住
        if (agentInfo.me() instanceof PoliceForce)
            return false;
        Area mePosArea = humanPositionTool.getMyPositionArea();
        // 执行的前一个动作不是移动，不会被卡住
        Action prevAction = agentInfo.getExecutedAction(-1);
        if (!(prevAction instanceof ActionMove || prevAction instanceof ActionRest)) {
            logger.info("Previous action is not action move, can't be stuck");
            return false;
        }
        // 如果停留在一个 area 的时间超过 maxTravelTime，则认为我卡住了



        int maxTravelTime = getMaxTravelTime(mePosArea.getID());
        int myStayCount = humanPositionTool.getStayCount(agentInfo.getID());
        logger.info("stayCount=" + myStayCount + ", maxTravelTime=" + maxTravelTime);
        if (myStayCount > maxTravelTime)
            return true;
        else return false;
    }

    /**
     * 判断一个视野内的人是否被障碍覆盖或被卡在建筑里
     * @return 覆盖此人的 Blockade
     */
    public boolean isStuckInBlockadeOrInBuildingInSight(
            @Nonnull Human human,
            @Nonnull Area mePosArea
    ) {
        Area humanPosArea = humanPositionTool.getPositionArea(human.getID());
        assert humanPosArea != null;
        // PF 永远不会卡住
        if (human instanceof PoliceForce)
            return false;
        // human 在建筑里面，而且不是我自己
        if (humanPosArea instanceof Building building) {
            if (human.equals(agentInfo.me())) { // 是我自己
                boolean allEntranceBlock = true;
                for (EntityID neighborId : building.getNeighbours()) {
                    StandardEntity neighbor = worldInfo.getEntity(neighborId);
                    if (neighbor instanceof Road && areaPassableTool.getImpassableRatio(neighborId) == 0)
                        allEntranceBlock = false;
                }
                if (allEntranceBlock)
                    return true;
            } else { // 不是我自己
                // 忽略所有不可通行边对，如果找不到这个 human 到我的一条路，则认为这个 human 卡住了
                searchPathTool.setFromAndTo(building, mePosArea) // 反向搜索，fail fast
                        .ignoreImpassableAdjacentEdges()
                        .calc();
                if (searchPathTool.getEdgePath() == null) {
                    logger.debug("cannot find a path from my pos to " + building);
                    return true;
                }
            }
        }
        // 没有 blockades，肯定不可能被卡住
//        // 注释的这段方法，在人在障碍边缘时很容易误判
//        if (!humanPosArea.isBlockadesDefined() || humanPosArea.getBlockades().isEmpty())
//            return false;
//        for (EntityID blockadeId : humanPosArea.getBlockades()) {
//            Blockade blockade = (Blockade) worldInfo.getEntity(blockadeId);
//            assert blockade != null;
//            if (blockade.getShape().contains(human.getX(), human.getY())) {
//                return true;
//            }
//        }
        // TODO 是否存在性能问题？
        if (human.isXDefined() && human.isYDefined()) {
            Geometry blockPolygon = areaPassableTool.getBlockadePolygonOf(humanPosArea.getID());
            if (blockPolygon == null || blockPolygon.isEmpty())
                return false;
            else {
                // TODO
//                Geometry shrunkenBlockadePolygon = blockPolygon.buffer(-20, 2);
                Geometry shrunkenBlockadePolygon = blockPolygon.buffer(2);
                return shrunkenBlockadePolygon.contains(GF.createPoint(new CoordinateXY(human.getX(), human.getY())));
            }
        } return false;
    }

    /**
     * 返回本周期视野范围内所有被卡住情况改变的所有 Civilian 的 EntityID
     */
    @Nonnull
    public Set<EntityID> getStuckTypeChangedCvIdsInSight() {
        return stuckTypeChangedCvIdsInSight;
    }

    /**
     * 计算走出一个 area 需要的最长时间
     */
    private int getMaxTravelTime(@Nonnull EntityID areaId) {
        List<HFUTEdge> hfutEdges = areaPassableTool.getHfutEdgesOf(areaId);
        if (hfutEdges == null || hfutEdges.isEmpty())
            return Integer.MAX_VALUE;
        double maxDist = 0.0;
        for (int i = 0; i < hfutEdges.size(); i ++) {
            HFUTEdge ithEdge = hfutEdges.get(i);
            for (int j = i; j < hfutEdges.size(); j ++) {
                HFUTEdge jthEdge = hfutEdges.get(j);
                double dist = HFUTGeometryTool.getDistance(ithEdge, jthEdge);
                if (dist > maxDist)
                    maxDist = dist;
            }
        }
        if (maxDist > 0)
            return 1 + (int) Math.ceil(maxDist / OptionsTool.MOVING_DISTANCE);
        return Integer.MAX_VALUE;
    }

    private void updateStuckType(
            boolean isCivilian,
            @Nonnull EntityID humanId,
            @Nonnull StuckType stuckType,
            int time,
            @Nonnull InfoSource infoSource
    ) {
        // 世界模型里没有这个人，不更新
        Human human = (Human) worldInfo.getEntity(humanId);
        if (human == null)
            return;
        StuckTypeRecord record = this.recordMap.get(humanId);
        // 还没有记录
        if (record == null) {
            record = new StuckTypeRecord();
            record.setLastUpdateTime(time);
            record.setStuckType(stuckType);
            record.setInfoSource(infoSource);
            logger.info("update: humanId=" + human + ", from=" + null + ", to=" + record);
            this.recordMap.put(humanId, record);
        } else {
            // 已经有记录了
            // 1. 我无法判断别的智能体是否移动时被卡，也可能误判被卡住的智能体没被卡。因此如果原有记录是 NOT_STUCK_FROM_OTHERS
            //    但新记录不是，则只要新记录不晚于旧记录三个周期，就更新
            // 2. 新的信息更新
            boolean update = false;
            if (isCivilian) { // 是 CV，所有信息其实都是自己或别人看到的
                // 只有新信息更新，才更新
                if (time > record.getLastUpdateTime())
                    update = true;
            } else { // 是 AT、FB、PF 这些智能体
                // 已有记录是通过消息收到的，或者是自己获得的
                if (record.getInfoSource() != InfoSource.WITNESS) {
                    // 不管新信息是看到的还是收到的，只要比原有记录更新，则更新
                    if (time > record.getLastUpdateTime())
                        update = true;
                } else { // 已有记录是看到的
                    // 新信息也是看到的，而且更新，则更新
                    if (infoSource == InfoSource.WITNESS && time > record.getLastUpdateTime())
                        update = true;
                        // 否则是通过消息收到的
                        // 已有记录不确定
                    else if ((record.getStuckType() == StuckType.NOT_STUCK && record.getInfoSource() == InfoSource.WITNESS)
                            && (stuckType == StuckType.STUCK_WHEN_MOVING || stuckType == StuckType.NOT_STUCK)) {
                        // 消息收到的信息只要不滞后于已有记录 2 个周期以上，都更新
                        if (time >= record.getLastUpdateTime() || record.getLastUpdateTime() - time <= 2)
                            update = true;
                    } else { // 已有记录确定
                        // 则只有消息收到的信息更新才更新
                        if (time > record.getLastUpdateTime())
                            update = true;
                    }
                }
            }
            if (update) {
                StringBuilder sb = new StringBuilder();
                sb.append("update: humanId=" + human + ", from=" + record);
                if (isCivilian && !record.getStuckType().equals(stuckType) && infoSource == InfoSource.WITNESS)
                    this.stuckTypeChangedCvIdsInSight.add(humanId);
                record.setStuckType(stuckType);
                record.setLastUpdateTime(time);
                record.setInfoSource(infoSource);
                EntityID positionAreaId = humanPositionTool.getPositionAreaId(humanId);
                sb.append(", to=" + record);
                sb.append(", stuckedArea" + positionAreaId);
                logger.info(sb);
            }
        }
    }
}
