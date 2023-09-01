package hfutinvincible.tool.path;

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
import hfutinvincible.tool.LifeCycle;
import hfutinvincible.tool.edge.AreaPassableTool;
import hfutinvincible.tool.edge.HFUTEdge;
import hfutinvincible.tool.misc.HFUTGeometryTool;
import hfutinvincible.tool.misc.OptionsTool;
import org.apache.log4j.Logger;
import org.locationtech.jts.geom.CoordinateXY;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 此工具每个周期会使用 dijkstra 算法计算出以 refuge 为中心，所有其他 area 为目标的最短路径。
 * 之后可以非常快地（相比 {@link SearchPathTool}）找到满足要求的最短路。
 */
@HfutTool
@AgentType({PoliceForce.class, AmbulanceTeam.class, FireBrigade.class})
public final class PathToRefugeTool implements LifeCycle {

    @Autowired
    WorldInfo worldInfo;
    @Autowired
    AgentInfo agentInfo;
    @Autowired
    Logger logger;

    private final Map<EntityID, Map<HFUTEdge, HFUTEdge>> predecessorMapMap;
    private final Map<EntityID, Map<HFUTEdge, Double>> passCostMapMap;

    // Tools
    @Autowired
    private AreaPassableTool areaPassableTool;

    public PathToRefugeTool() {

        this.predecessorMapMap = new HashMap<>();
        this.passCostMapMap = new HashMap<>();
    }

    @Override
    public void updateInfo(MessageManager messageManager) {

        this.areaPassableTool.updateInfo(messageManager);

        // 以整个地图为目标，花不了多少时间
        long currentTime = System.currentTimeMillis();
        Set<HFUTEdge> edges = worldInfo.getAllEntities()
                .stream()
                .filter(Area.class::isInstance)
                .map(StandardEntity::getID)
                .map(areaId -> this.areaPassableTool.getHfutEdgesOf(areaId))
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .collect(Collectors.toSet());

        for (StandardEntity e : worldInfo.getEntitiesOfType(StandardEntityURN.REFUGE))
            dijkstra((Refuge) e, edges);

        logger.info("---------- [" + (System.currentTimeMillis() - currentTime) + "ms]");
    }

    /**
     * 计算从一个 refuge 出发，到一条边 toEdge 的最短边路径以及对应通行代价
     * @param refugeId 出发 refuge 的 EntityID
     * @param toEdge 目标 edge
     * @return 如果找不到所有边都在我所在分区内的路径，返回 null
     */
    public Tuple2<LinkedList<HFUTEdge>, Double> getEdgePathAndCost(
            @Nonnull EntityID refugeId,
            @Nonnull HFUTEdge toEdge
    ) {
        if (!predecessorMapMap.containsKey(refugeId))
            return null; // IMPOSSIBLE
        // 根据前趋 map 重构路径
        Map<HFUTEdge, HFUTEdge> predecessorMap = predecessorMapMap.get(refugeId);
        Map<HFUTEdge, Double> passCostMap = passCostMapMap.get(refugeId);
        LinkedList<HFUTEdge> edgePath = new LinkedList<>();
        HFUTEdge iter = toEdge;
        do {
            edgePath.addFirst(iter);
            iter = predecessorMap.get(iter);
        } while (iter != null);
        return Tuple2.of(edgePath, passCostMap.get(toEdge));
    }

    private void dijkstra(
            @Nonnull final Refuge fromRefuge,
            @Nonnull final Set<HFUTEdge> targets
    ) {
        final Map<HFUTEdge, HFUTEdge> predecessorMap = new HashMap<>();
        final Map<HFUTEdge, Double> passCostMap = new HashMap<>();
        final Map<HFUTEdge, Double> searchCostMap = new HashMap<>();
        final PriorityQueue<HFUTEdge> searchQueue = new PriorityQueue<>(
                Comparator.comparingDouble(e -> searchCostMap.getOrDefault(e, Double.MAX_VALUE)));

        final List<HFUTEdge> refugeEdges = areaPassableTool.getHfutEdgesOf(fromRefuge.getID());

        if (refugeEdges == null) {
            String errMsg = "Cannot get edges of my position area.";
            logger.error(errMsg);
            throw new RuntimeException(errMsg);
        }

        final double refugePosX = fromRefuge.getX();
        final double refugePosY = fromRefuge.getY();
        for (HFUTEdge hfutEdge : refugeEdges) {
            double initialCost = areaPassableTool.getPoint2EdgePassCostByAgentType(
                    new CoordinateXY(refugePosX, refugePosY),
                    fromRefuge,
                    hfutEdge,
                    agentInfo.me().getClass());
            searchCostMap.put(hfutEdge, initialCost);
            searchQueue.offer(hfutEdge);
        }

        // 不断扩展搜索边界，直至搜索队列为空
        while (!searchQueue.isEmpty()) {
            HFUTEdge currEdge = searchQueue.poll();
            double currCost = searchCostMap.get(currEdge);

            // currEdge 是搜索目标，则此时其最小通行代价已经确定
            // 就是 currCost，将其加入 passCostMap
            passCostMap.put(currEdge, currCost);
            for (HFUTEdge adjacentEdge : areaPassableTool.getAdjacentEdges(currEdge)) {
                // adjacentEdge 不是搜索目标，跳过
                if (!targets.contains(adjacentEdge))
                    continue;
                // 到 adjacentEdge 的最短路已经确定，跳过
                if (passCostMap.containsKey(adjacentEdge))
                    continue;
                // 可通行且长度大于 EDGE_PASSABLE_MIN_LENGTH 的邻接边作为邻接结点
                if (HFUTGeometryTool.getLength(adjacentEdge.getEdge()) < OptionsTool.EDGE_PASSABLE_MIN_LENGTH)
                    continue;
                double newCost = currCost + areaPassableTool.getEdgePairPassCostByAgentType(
                        currEdge, adjacentEdge, agentInfo.me().getClass());
                // 可以继续扩展
                if (newCost < searchCostMap.getOrDefault(adjacentEdge, Double.MAX_VALUE)) {
                    // 更新 cost
                    searchCostMap.put(adjacentEdge, newCost);
                    // 记录当前前趋
                    predecessorMap.put(adjacentEdge, currEdge);
                    // 加入搜索队列
                    searchQueue.offer(adjacentEdge);
                }
            }
        }

        predecessorMapMap.put(fromRefuge.getID(), predecessorMap);
        passCostMapMap.put(fromRefuge.getID(), passCostMap);
    }
}
