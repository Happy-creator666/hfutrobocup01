package hfutinvincible.tool.human;

import adf.core.agent.communication.MessageManager;
import adf.core.agent.develop.DevelopData;
import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.ScenarioInfo;
import adf.core.agent.info.WorldInfo;
import adf.core.agent.module.ModuleManager;
import adf.core.agent.precompute.PrecomputeData;
import hfutinvincible.Tuple2;
import hfutinvincible.anno.After;
import hfutinvincible.anno.AgentType;
import hfutinvincible.anno.Autowired;
import hfutinvincible.anno.HfutTool;
import hfutinvincible.message.TargetBlockMessage;
import hfutinvincible.tool.LifeCycle;
import hfutinvincible.tool.Params;
import hfutinvincible.tool.comm.MessageManageTool;
import hfutinvincible.tool.criterion.TargetBlockCriterion;
import hfutinvincible.tool.edge.AreaPassableTool;
import hfutinvincible.tool.misc.OptionsTool;
import hfutinvincible.tool.path.Path;
import hfutinvincible.tool.path.SearchPathTool;
import org.apache.log4j.Logger;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;

import javax.annotation.Nonnull;
import java.util.*;

@HfutTool
@AgentType({AmbulanceTeam.class, PoliceForce.class, FireBrigade.class})
public class TargetBlockTool implements LifeCycle {
    /** areaId |-> 被这个 area 卡住而无法到达目标所在地的 humanIds
     * (只有 pf 使用，用于计算 {@link hfutinvincible.tool.criterion.TargetBlockCriterion}) */
    private final Map<EntityID, Set<EntityID>> blockMap;

    private Set<EntityID> buildingSet;
    private Set<EntityID> refugeIds;
    private Params params;

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
    @Autowired
    Logger logger;

    //Tools
    @Autowired
    AreaPassableTool areaPassableTool;
    @Autowired
    MessageManageTool messageTool;
    @Autowired
    SearchPathTool searchPathTool;
    @Autowired
    TimeToDeadTool timeToDeadTool;
    @Autowired
    HumanPositionTool humanPositionTool;
    @Autowired
    StuckInfoTool stuckInfoTool;

    public TargetBlockTool(){
        this.blockMap = new HashMap<>();
        this.buildingSet = new HashSet<>();
        this.params = new Params(getClass().getSimpleName());
    }

    @Override
    public void resume(PrecomputeData precomputeData) {
        worldInfo.getAllEntities()
                .stream()
                .filter(Building.class::isInstance)
                .map(StandardEntity::getID)
                .forEach(buildingSet::add);
        refugeIds = new HashSet<>(worldInfo.getEntityIDsOfType(StandardEntityURN.REFUGE));
    }

    @Override
    @After({"AreaPassableTool","SearchPathTool","TimeToDeadTool","HumanPositionTool","StuckInfoTool"})
    public void updateInfo(MessageManager messageManager) {

        // 从 blockMap 中移除所有完全可通行的 area
        blockMap.keySet().removeIf(areaId -> {
            double impassableRatio = areaPassableTool.getImpassableRatio(areaId);
            if (impassableRatio == 0) {
                logger.info("area " + areaId + " is not stuck anymore. remove it.");
                return true;
            }
            return false;
        });
        // 从消息更新
        messageTool.getReceivedMessages(TargetBlockMessage.class)
                .forEach(message -> {
                    Area fromArea = (Area) worldInfo.getEntity(message.getFromAreaId());
                    Area toArea = (Area) worldInfo.getEntity(message.getToAreaId());
                    if (fromArea == null || toArea == null)
                        return;
                    Path areaPath = searchPathTool.setFromAndTo(fromArea, toArea)
                            .ignoreBlockades()
                            .calc()
                            .getAreaPath();
                    if (areaPath == null)
                        return;
                    EntityID blockedAgentId = message.getAgentId();
                    for (EntityID areaId : areaPath.getIDList()) {
                        if (areaPassableTool.getImpassableRatio(areaId) == 0) // 可以通行 跳过
                            continue;
                        if (!blockMap.containsKey(areaId))
                            blockMap.put(areaId, new HashSet<>());
                        blockMap.get(areaId).add(blockedAgentId);
                    }
                });

        // 从视野更新
        int time = agentInfo.getTime();
        Set<EntityID> changedEntities = worldInfo.getChanged().getChangedEntities();
        for (EntityID id : changedEntities) {
            StandardEntity entity = worldInfo.getEntity(id);
            if (!(entity instanceof Human human))
                continue;
            EntityID humanPosAreaId = humanPositionTool.getPositionAreaId(id);
            if (humanPosAreaId == null) { // IMPOSSIBLE BRANCH
                logger.error("IMPOSSIBLE! I should always know the position area of human in sight.");
                continue;
            }
            Area humanPosArea = (Area) worldInfo.getEntity(humanPosAreaId);
            if (humanPosArea == null) // IMPOSSIBLE
                continue;
            if (needFb(human)) {
                // 找出最合适救这个人的 fb
                Tuple2<EntityID, Area> bestFb = worldInfo.getEntityIDsOfType(StandardEntityURN.FIRE_BRIGADE)
                        .stream()
                        .filter(fbId -> {
                            int lastUpdateTime = humanPositionTool.getUpdateTime(fbId);
                            int laggingTime = time - lastUpdateTime;
                            return laggingTime < params.getInt("minFbLaggingTime");
                        })
                        .map(fbId -> {
                            EntityID fbPosAreaId = humanPositionTool.getPositionAreaId(fbId);
                            if (fbPosAreaId == null)
                                return null;
                            Area fbPosArea = (Area) worldInfo.getEntity(fbPosAreaId);
                            if (fbPosArea == null)
                                return null;
                            return Tuple2.of(fbId, fbPosArea);
                        })
                        .filter(Objects::nonNull)
                        .min(Comparator.comparingInt(t -> worldInfo.getDistance(humanPosArea, t.e1)))
                        .orElse(null);

                // 如果找到了合适的 fb，将这个 fb 到目标的路径标记为 block
                if (bestFb != null) {
                    Path areaPath = searchPathTool
                            .setFromAndTo(humanPosArea, bestFb.e1)
                            .ignoreBlockades()
                            .calc()
                            .getAreaPath();
                    if (areaPath == null)
                        continue;
                    logger.info("Find human " + human + " need fb, path=" + areaPath);
                    for (EntityID areaId : areaPath.getIDList()) {
                        if (areaPassableTool.getImpassableRatio(areaId) == 0) // 可以通行 跳过
                            continue;
                        if (!blockMap.containsKey(areaId))
                            blockMap.put(areaId, new HashSet<>());
                        blockMap.get(areaId).add(bestFb.e0);
                    }
                }
            }
            if (needAt(human)) {
                // 找出最合适救这个人的 at
                Tuple2<EntityID, Area> bestAt = worldInfo.getEntityIDsOfType(StandardEntityURN.AMBULANCE_TEAM)
                        .stream()
                        .filter(atId -> {
                            int lastUpdateTime = humanPositionTool.getUpdateTime(atId);
                            int laggingTime = time - lastUpdateTime;
                            return laggingTime < params.getInt("minAtLaggingTime");
                        })
                        .map(atId -> {
                            EntityID fbPosAreaId = humanPositionTool.getPositionAreaId(atId);
                            if (fbPosAreaId == null)
                                return null;
                            Area fbPosArea = (Area) worldInfo.getEntity(fbPosAreaId);
                            if (fbPosArea == null)
                                return null;
                            return Tuple2.of(atId, fbPosArea);
                        })
                        .filter(Objects::nonNull)
                        .min(Comparator.comparingInt(t -> worldInfo.getDistance(humanPosArea, t.e1)))
                        .orElse(null);

                // 如果找到了合适的 at，将这个 at 到目标的路径标记为 block
                if (bestAt != null) {
                    Path areaPath = searchPathTool
                            .setFromAndTo(humanPosArea, bestAt.e1)
                            .ignoreBlockades()
                            .calc()
                            .getAreaPath();
                    if (areaPath == null)
                        continue;
                    logger.info("Find human " + human + " need at, path=" + areaPath);
                    for (EntityID areaId : areaPath.getIDList()) {
                        if (areaPassableTool.getImpassableRatio(areaId) == 0) // 可以通行 跳过
                            continue;
                        if (!blockMap.containsKey(areaId))
                            blockMap.put(areaId, new HashSet<>());
                        blockMap.get(areaId).add(bestAt.e0);
                    }
                }
            }
            // 看到一个市民，将其到目标 refuge 路上的 blocked area 设为 block
            if (human instanceof Civilian && human.isDamageDefined() && human.getDamage() == 0) {
                List<EntityID> areaIdPath = breadthFirstSearchForCivilian(humanPosAreaId, refugeIds);
                if (areaIdPath != null) {
                    logger.info("Find civilian " + human + " , path=" + areaIdPath);
                    for (EntityID areaId : areaIdPath) {
                        if (areaPassableTool.getImpassableRatio(areaId) == 0) // 可以通行 跳过
                            continue;
                        if (!blockMap.containsKey(areaId))
                            blockMap.put(areaId, new HashSet<>());
                        blockMap.get(areaId).add(human.getID());
                    }
                }
            }
        }
    }
    private boolean needFb(Human human) {
        if (human.isBuriednessDefined() && human.getBuriedness() > 0)
            return true;
        return false;
    }

    private boolean needAt(Human human) {
        if (human instanceof AmbulanceTeam) // at 永远不需要 at 来救!!
            return false;
        Integer timeToDead = timeToDeadTool.getTimeToDeadNowEstimate(human.getID());
        if (timeToDead == null) { // IMPOSSIBLE BRANCH
            logger.error("IMPOSSIBLE! I should always know the timeToDead of human in sight.");
            return false;
        }
        return timeToDead < OptionsTool.KERNEL_TIMESTEPS && timeToDead > params.getInt("ignoreTimeToDead");
    }

    /**
     * 返回所有被 areaId 卡住的智能体的 EntityID
     */
    @Nonnull
    public Set<EntityID> getBlockedAgentIdsBy(@Nonnull EntityID areaId) {
        return blockMap.getOrDefault(areaId, new HashSet<>());
    }

    /**
     * 返回被 areaId 卡住的智能体的数量
     */
    public int getBlockedAgentsNumBy(@Nonnull EntityID areaId) {
        Set<EntityID> blockedAgentIds = blockMap.get(areaId);
        return blockedAgentIds == null
                ? 0
                : blockedAgentIds.size();
    }

    // server 抄来的
    public List<EntityID> breadthFirstSearchForCivilian( EntityID start,
                                                         Set<EntityID> goals ) {

        List<EntityID> open = new LinkedList<EntityID>();
        Map<EntityID, EntityID> ancestors = new HashMap<EntityID, EntityID>();
        open.add( start );
        EntityID next = null;
        boolean found = false;
        ancestors.put( start, start );
        do {
            next = open.remove( 0 );
            if (goals.contains(next)) {
                found = true;
                break;
            }
            Collection<EntityID> neighbours = ((Area) worldInfo.getEntity(next)).getNeighbours();
            if ( neighbours.isEmpty() ) {
                continue;
            }
            for ( EntityID neighbour : neighbours ) {
                if (goals.contains(neighbour)) {
                    ancestors.put( neighbour, next );
                    next = neighbour;
                    found = true;
                    break;
                } else {
                    if ( !ancestors.containsKey( neighbour ) ) {
                        if ( buildingSet.contains( next )
                                || !buildingSet.contains( neighbour ) ) {
                            open.add( neighbour );
                            ancestors.put( neighbour, next );
                        }
                    }
                }
            }
        } while ( !found && !open.isEmpty() );
        if ( !found ) {
            // No path
            return null;
        }
        // Walk back from goal to start
        EntityID current = next;
        List<EntityID> path = new LinkedList<EntityID>();
        do {
            path.add( 0, current );
            current = ancestors.get( current );
            if ( current == null ) {
                throw new RuntimeException(
                        "Found a node with no ancestor! Something is broken." );
            }
        } while ( current != start );
        return path;
    }
}
