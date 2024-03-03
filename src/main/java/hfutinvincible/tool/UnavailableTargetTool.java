package hfutinvincible.tool;

import adf.core.agent.communication.MessageManager;
import adf.core.agent.develop.DevelopData;
import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.ScenarioInfo;
import adf.core.agent.info.WorldInfo;
import adf.core.agent.module.ModuleManager;
import hfutinvincible.Tuple2;
import hfutinvincible.anno.AgentType;
import hfutinvincible.anno.Autowired;
import hfutinvincible.anno.HfutTool;
import hfutinvincible.debug.Debugger;
import hfutinvincible.debug.Painter;
import hfutinvincible.debug.paintable.Paintable;
import hfutinvincible.message.UnavailableMessage;
import hfutinvincible.tool.comm.MessageManageTool;
import org.apache.log4j.Logger;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;
import rescuecore2.misc.geometry.Point2D;

import javax.annotation.Nonnull;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.stream.Stream;

@HfutTool
@AgentType({AmbulanceTeam.class, FireBrigade.class, PoliceForce.class})
public class UnavailableTargetTool implements LifeCycle{
    private final Map<EntityID, Map<UnavailableType, Integer>> lastUnavailableTimeMap;

    /**
     * 本周期我看到的 unavailable 的 targets
     */
    private final Set<EntityID> unavailableTargetsISee;



    @Autowired
    AgentInfo agentInfo;
    @Autowired
    WorldInfo worldInfo;
    @Autowired
    ScenarioInfo scenarioInfo;
    @Autowired
    DevelopData developData;
    @Autowired
    ModuleManager moduleManager;
    @Autowired
    Logger logger;
    //Tools
    @Autowired
    MessageManageTool messageTool;

    public UnavailableTargetTool(){
        this.lastUnavailableTimeMap = new HashMap<>();
        this.unavailableTargetsISee = new HashSet<>();
    }

    @Override
    public void updateInfo(MessageManager messageManager) {
        messageTool.updateInfo(messageManager);
        unavailableTargetsISee.clear();

        // 从消息更新
        messageTool.getReceivedMessages(UnavailableMessage.class)
                .forEach(m -> {
                    UnavailableType type = m.getUnavailableType();
                    EntityID unavailableTarget = m.getUnavailableTarget();
                    if (type == UnavailableType.noBuried && worldInfo.getEntity(agentInfo.getID()) instanceof FireBrigade)
                        setUnavailableAndWait(unavailableTarget, type, Integer.MAX_VALUE, false);
                    else if (type == UnavailableType.noHurtDefinitely && worldInfo.getEntity(agentInfo.getID()) instanceof AmbulanceTeam)
                        setUnavailableAndWait(unavailableTarget, type, Integer.MAX_VALUE, false);
                });

        // wait time 每周期减 1
        for (Map.Entry<EntityID, Map<UnavailableType, Integer>> entry : lastUnavailableTimeMap.entrySet()) {
            EntityID areaId = entry.getKey();
            Map<UnavailableType, Integer> map = entry.getValue();
            int maxWaitTime = Integer.MIN_VALUE;
            UnavailableType minWaitType = null;
            for (Map.Entry<UnavailableType, Integer> e : map.entrySet()) {
                UnavailableType type = e.getKey();
                int value = e.getValue();
                if (value != 0 && value != Integer.MAX_VALUE) // 设为永久 unavailable 的不减
                    e.setValue(value - 1);
                if (value > maxWaitTime) {
                    maxWaitTime = value;
                    minWaitType = type;
                }
            }
            logger.info("areaId=" + areaId + ", minWaitType=" + minWaitType + ", waitTime=" + maxWaitTime);
        }
    }

    @Override
    public void afterCalc(CalcContext context) {
        Painter<?> unavailableLayer =Debugger.getPainterOfAgent(agentInfo);
        worldInfo.getAllEntities()
                .stream()
                .filter(e -> e instanceof Human)
                .forEach(human -> {
                    var p = worldInfo.getLocation(human);

                    unavailableLayer.addPaintables("UnavailableTarget",false,
                            Paintable.pointcross(p.first(),p.second(),1000,Color.RED));
                });
    }

    public void setUnavailableAndWait(
            @Nonnull EntityID unavailableTargetAreaId,
            @Nonnull UnavailableType unavailableType,
            int waitTime,
            boolean fromSight
    ) {
        if (!lastUnavailableTimeMap.containsKey(unavailableTargetAreaId)) {
            Map<UnavailableType, Integer> map = new HashMap<>();
            for (UnavailableType type : UnavailableType.values())
                map.put(type, 0);
            map.put(unavailableType, waitTime);
            lastUnavailableTimeMap.put(unavailableTargetAreaId, map);
            return;
        }
        lastUnavailableTimeMap.get(unavailableTargetAreaId)
                .compute(unavailableType, (k, v) -> Math.max(v, waitTime));
        if (fromSight)
            unavailableTargetsISee.add(unavailableTargetAreaId);
    }

    /**
     * 判断一个 target area 是否 unavailable
     */
    public boolean isUnavailable(@Nonnull EntityID areaId, @Nonnull UnavailableType... types) {
        Map<UnavailableType, Integer> map = lastUnavailableTimeMap.get(areaId);
        if (map == null)
            return false;
        for (UnavailableType type : types) {
            Integer count = map.get(type);
            if (count != null && count != 0)
                return true;
        }
        return false;
    }

    /**
     * 判断一个 target area 是否曾经被标记为 unavailable
     */
    public boolean wasEverUnavailable(@Nonnull EntityID areaId, @Nonnull UnavailableType... types) {
        Map<UnavailableType, Integer> map = lastUnavailableTimeMap.get(areaId);
        if (map == null)
            return false;
        for (UnavailableType type : types) {
            Integer count = map.get(type);
            if (count != null) // 不管计数是多少
                return true;
        }
        return false;
    }

    /**
     * 判断是否是本周期我看到的 unavailable 的 target
     */
    public boolean isUnavailableTargetISee(@Nonnull EntityID areaId) {
        return unavailableTargetsISee.contains(areaId);
    }

    /**
     * 返回所有指定类型 unavailable 的 targets
     */
    public Stream<EntityID> getAllUnavailableTargets(@Nonnull UnavailableType... types) {
        return lastUnavailableTimeMap.entrySet()
                .stream()
                .filter(e -> {
                    Map<UnavailableType, Integer> type2tick = e.getValue();
                    return Arrays.stream(types)
                            .map(type2tick::get)
                            .anyMatch(i -> i > 0);
                })
                .map(Map.Entry::getKey);
    }

    /**
     * 返回所有指定类型 unavailable 的 targets
     * <p>
     * 如果一个 target 有多个 unavailableType 计数不为 0，则拆成多个 tuple 返回
     */
    public List<Tuple2<EntityID, UnavailableType>> getAllUnavailableTargetAndTypesFromMyself(@Nonnull UnavailableType... types) {
        Set<UnavailableType> queriedTypes = new HashSet<>(List.of(types));
        List<Tuple2<EntityID, UnavailableType>> ret = new ArrayList<>();
        for (Map.Entry<EntityID, Map<UnavailableType, Integer>> entry : lastUnavailableTimeMap.entrySet()) {
            EntityID targetId = entry.getKey();
            Map<UnavailableType, Integer> type2time = entry.getValue();
            for (Map.Entry<UnavailableType, Integer> entry1 : type2time.entrySet()) {
                if (!queriedTypes.contains(entry1.getKey()))
                    continue;
                Integer time = entry1.getValue();
                if (time == null || time == 0)
                    continue;
                UnavailableType type = entry1.getKey();
                ret.add(Tuple2.of(targetId, type));
            }
        }
        return ret;
    }

    public Integer getWaitTimeOf(@Nonnull EntityID entityID){
        Map<UnavailableType, Integer> unavailableTypeIntegerMap = lastUnavailableTimeMap.get(entityID);
        if(unavailableTypeIntegerMap == null)
            return 0;
        return Collections.max(unavailableTypeIntegerMap.values());
    }


}
