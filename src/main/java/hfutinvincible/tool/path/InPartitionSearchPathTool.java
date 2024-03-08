package hfutinvincible.tool.path;

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
import hfutinvincible.tool.LifeCycle;
import hfutinvincible.tool.edge.AreaPassableTool;
import hfutinvincible.tool.edge.HFUTEdge;
import hfutinvincible.tool.human.HumanPositionTool;
import hfutinvincible.tool.misc.HFUTGeometryTool;
import hfutinvincible.tool.misc.OptionsTool;
import org.apache.log4j.Logger;
import org.locationtech.jts.geom.CoordinateXY;
import rescuecore2.standard.entities.AmbulanceTeam;
import rescuecore2.standard.entities.Area;
import rescuecore2.standard.entities.FireBrigade;
import rescuecore2.standard.entities.PoliceForce;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.worldmodel.EntityID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;

@HfutTool
@AgentType({AmbulanceTeam.class, FireBrigade.class, PoliceForce.class})
public class InPartitionSearchPathTool implements LifeCycle {
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

    /** 记录每个结点的前趋 */
    private Map<HFUTEdge, HFUTEdge> predecessorMap;
    /** 记录到每个结点的通行代价 */
    private Map<HFUTEdge, Double> passCostMap;

    //Tools
    @Autowired
    AreaPassableTool areaPassableTool;
    @Autowired
    HumanPositionTool humanPositionTool;

    @Override
    @After("AreaPassableTool")
    public void precompute(PrecomputeData precomputeData) {
    }

    @Override
    @After("AreaPassableTool")
    public void resume(PrecomputeData precomputeData) {
    }

    @Override
    @After("AreaPassableTool")
    public void preparate() {
    }

    @Override
    public void updateInfo(MessageManager messageManager) {
        this.areaPassableTool.updateInfo(messageManager);

        // 还是直接以整个地图为目标，花不了多少时间
        long currentTime = System.currentTimeMillis();
        Set<HFUTEdge> edges = worldInfo.getAllEntities()
                .stream()
                .filter(Area.class::isInstance)
                .map(StandardEntity::getID)
                .map(areaId -> this.areaPassableTool.getHfutEdgesOf(areaId))
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .collect(Collectors.toSet());
        dijkstra(edges);
        logger.info("---------- [" + (System.currentTimeMillis() - currentTime) + "ms]");
    }

    /**
     * 计算一条从我所在 area 到目标边的最短边路径和对应的通行代价
     * @param toEdge 目标边
     * @return 如果找不到所有边都在我所在分区内的路径，返回 null
     */
    @Nullable
    public Tuple2<LinkedList<HFUTEdge>, Double> getEdgePathAndCost(@Nonnull HFUTEdge toEdge) {
        if (this.passCostMap.containsKey(toEdge)) {
            // 根据前趋 map 重构路径
            LinkedList<HFUTEdge> edgePath = new LinkedList<>();
            HFUTEdge iter = toEdge;
            do {
                edgePath.addFirst(iter);
                iter = this.predecessorMap.get(iter);
            } while (iter != null);
            return Tuple2.of(edgePath, this.passCostMap.get(toEdge));
        }
        return null;
    }

    /**
     * 计算一条从我所在 area 到目标 area 的最短边路径和对应的通行代价
     * @param toAreaId 目标 area
     * @return 如果找不到所有边都在我所在分区内的路径，返回 null
     */
    @Nullable
    public Tuple2<LinkedList<HFUTEdge>, Double> getEdgePathAndCost(@Nonnull StandardEntity toAreaId) {
        List<HFUTEdge> edges = areaPassableTool.getHfutEdgesOf(toAreaId.getID());
        if (edges == null || edges.isEmpty())
            return null;
        return edges.stream()
                .map(this::getEdgePathAndCost)
                .filter(Objects::nonNull)
                .min(Comparator.comparingDouble(t -> t.e1))
                .orElse(null);
    }

    /**
     * 计算一条我所在 area 到目标边的最小通行代价
     * @param toEdge 目标边
     * @return 如果找不到所有边都在我所在分区内的路径，返回 null
     */
    @Nullable
    public Double getCost(@Nonnull HFUTEdge toEdge) {
        return this.passCostMap.get(toEdge);
    }

    /**
     * 计算一条我所在 area 到目标边的最小通行代价
     * @param toEdge 目标边
     */
    public double getCostOrDefault(@Nonnull HFUTEdge toEdge) {
        return this.passCostMap.getOrDefault(toEdge, Double.MAX_VALUE);
    }

    public double getCostOrDefault(@Nonnull StandardEntity toAreaId) {
        Tuple2<LinkedList<HFUTEdge>, Double> result = this.getEdgePathAndCost(toAreaId);
        return  result == null ? Double.MAX_VALUE : result.e1;
    }

    //////////////////////////////////////////////////

    private void dijkstra(@Nonnull final Set<HFUTEdge> targets) {

        this.predecessorMap = new HashMap<>();
        this.passCostMap = new HashMap<>();
        final Map<HFUTEdge, Double> searchCostMap = new HashMap<>();
        final PriorityQueue<HFUTEdge> searchQueue = new PriorityQueue<>(
                Comparator.comparingDouble(e -> searchCostMap.getOrDefault(e, Double.MAX_VALUE)));

        // 我所在 area 的所有边先全部加入队列
        final List<HFUTEdge> mePosAreaEdges = areaPassableTool.getHfutEdgesOf(agentInfo.getPosition());

        if (mePosAreaEdges == null) {
            String errMsg = "Cannot get edges of my position area.";
            logger.error(errMsg);
            throw new RuntimeException(errMsg);
        }

        double mePosX = agentInfo.getX();
        double mePosY = agentInfo.getY();
        for (HFUTEdge hfutEdge : mePosAreaEdges) {
            double initialCost = areaPassableTool.getPoint2EdgePassCostByAgentType(
                    new CoordinateXY(mePosX, mePosY),
                    humanPositionTool.getMyPositionArea(),
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
            this.passCostMap.put(currEdge, currCost);
            for (HFUTEdge adjacentEdge : areaPassableTool.getAdjacentEdges(currEdge)) {
                // adjacentEdge 不是搜索目标，跳过
                if (!targets.contains(adjacentEdge))
                    continue;
                // 到 adjacentEdge 的最短路已经确定，跳过
                if (this.passCostMap.containsKey(adjacentEdge))
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
                    this.predecessorMap.put(adjacentEdge, currEdge);
                    // 加入搜索队列
                    searchQueue.offer(adjacentEdge);
                }
            }
        }
    }
}
