package hfutinvincible.tool.partition;

import adf.core.agent.communication.MessageManager;
import adf.core.agent.develop.DevelopData;
import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.ScenarioInfo;
import adf.core.agent.info.WorldInfo;
import adf.core.agent.module.ModuleManager;
import adf.core.agent.precompute.PrecomputeData;
import hfutinvincible.anno.AgentType;
import hfutinvincible.anno.Autowired;
import hfutinvincible.anno.HfutTool;
import hfutinvincible.debug.MiscLayer;
import hfutinvincible.debug.Painter;
import hfutinvincible.tool.LifeCycle;
import hfutinvincible.tool.Params;
import hfutinvincible.tool.human.HumanPositionTool;
import hfutinvincible.tool.misc.OptionsTool;
import org.apache.commons.math3.ml.clustering.CentroidCluster;
import org.apache.commons.math3.ml.clustering.Clusterable;
import org.apache.commons.math3.ml.clustering.KMeansPlusPlusClusterer;
import org.apache.log4j.Logger;
import rescuecore2.misc.geometry.Point2D;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;

import javax.annotation.Nonnull;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.stream.Stream;
import hfutinvincible.debug.Debugger;
import hfutinvincible.debug.paintable.Paintable;

import static java.util.stream.Collectors.*;

@HfutTool
@AgentType({AmbulanceTeam.class, PoliceForce.class, FireBrigade.class})
public class StaticPartitionTool implements LifeCycle {
    private Map<EntityID, Integer> areaWhichPartition;
    private Map<EntityID, Integer> agentAssignedPartition;
    private List<SinglePartition<EntityID>> partitions;
    private double avgDistanceBetweenCenters;
    private int avgSizeOfPartition;
    private Map<Integer, Set<Integer>> neighborPartitions;

    //
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

    /*
     * Maybe Error
     * */
    protected final Params params;

    //Tools
    @Autowired
    HumanPositionTool humanPositionTool;
    public StaticPartitionTool(){
        areaWhichPartition = new HashMap<>();
        agentAssignedPartition = new HashMap<>();
        partitions = new ArrayList<>();
        neighborPartitions = new HashMap<>();
        params=new Params(getClass().getSimpleName());
    }



    @Override
    public void precompute(PrecomputeData precomputeData) {
        init();
        // 写入 precomputeData
        precomputeData.setInteger("StaticPartitionTool.numPartitions", partitions.size());
        for (int i = 0; i < partitions.size(); i ++) {
            String prefix = "StaticPartitionTool.partition" + i;
            SinglePartition<EntityID> partition = partitions.get(i);
            precomputeData.setEntityIDList(prefix + ".elements", partition.getElements().toList());
            precomputeData.setEntityID(prefix + ".centerElement", partition.getCenterElement());
            precomputeData.setIntegerList(prefix + ".neighbors", neighborPartitions.get(i).stream().toList());
        }
        precomputeData.setEntityIDList("StaticPartitionTool.agentAssignedPartition.keys", agentAssignedPartition.keySet().stream().toList());
        precomputeData.setIntegerList("StaticPartitionTool.agentAssignedPartition.values", agentAssignedPartition.values().stream().toList());
        precomputeData.setDouble("StaticPartitionTool.avgDistanceBetweenCenters", avgDistanceBetweenCenters);
        precomputeData.setInteger("avgSizeOfPartition", avgSizeOfPartition);
        logger.info("areaWhichPartition=" + areaWhichPartition);
        logger.info("agentAssignedPartition=" + agentAssignedPartition);
    }

    @Override
    public void resume(PrecomputeData precomputeData) {
        int numPartitions = precomputeData.getInteger("StaticPartitionTool.numPartitions");
        for (int i = 0; i < numPartitions; i ++) {
            String prefix = "StaticPartitionTool.partition" + i;
            List<EntityID> elements = precomputeData.getEntityIDList(prefix + ".elements");
            EntityID centerElement = precomputeData.getEntityID(prefix + ".centerElement");
            SinglePartition<EntityID> partition = new SinglePartition<>(i, elements.stream().collect(toUnmodifiableSet()), centerElement);
            partitions.add(partition);
            for (EntityID element : elements)
                areaWhichPartition.put(element, i);
            List<Integer> neighbors = precomputeData.getIntegerList(prefix + ".neighbors");
            neighborPartitions.put(i, neighbors.stream().collect(toUnmodifiableSet()));
        }
        List<EntityID> keys = precomputeData.getEntityIDList("StaticPartitionTool.agentAssignedPartition.keys");
        List<Integer> values = precomputeData.getIntegerList("StaticPartitionTool.agentAssignedPartition.values");
        for (int i = 0; i < keys.size(); i ++) {
            agentAssignedPartition.put(keys.get(i), values.get(i));
        }
        avgDistanceBetweenCenters = precomputeData.getDouble("StaticPartitionTool.avgDistanceBetweenCenters");
        avgSizeOfPartition = precomputeData.getInteger("avgSizeOfPartition");
        logger.info("areaWhichPartition=" + areaWhichPartition);
        logger.info("agentAssignedPartition=" + agentAssignedPartition);
    }

    @Override
    public void preparate() {
        init();
    }

    @Override
    public void updateInfo(MessageManager messageManager) {
        Painter<?> staticPartitionLayer=Debugger.getPainterOfAgent(agentInfo);
        for(int i=0;i<partitions.size();i++){
            SinglePartition<EntityID> partition=partitions.get(i);
            Iterator<EntityID> areaIdIter =partition.getElements().iterator();
            while (areaIdIter.hasNext()){
                Area area =(Area) worldInfo.getEntity(areaIdIter.next());
                if(area==null)
                    continue;
                staticPartitionLayer.addPaintables("StaticPartitionTool",false,
                        Paintable.shape(area.getShape(),OptionsTool.ithColor(i),true));
            }
            EntityID centerId =partition.getCenterElement();
            Area center =(Area) worldInfo.getEntity(centerId);
            assert center!=null;
            Point2D cen=new Point2D(center.getX(),center.getY());
            staticPartitionLayer.addPaintables("StaticPartitionTool",false,
                            Paintable.line(cen.getX()-4000,cen.getY(),4000*2,0,Color.WHITE),
                            Paintable.line(cen.getX(),cen.getY()-4000,0,4000*2,Color.WHITE));
        }
    }

    /**
     * 返回我被分配到的分区
     */
    @Nonnull
    public Partition<EntityID> getAssignedPartition() {
        EntityID myId = agentInfo.getID();
        int partitionId = agentAssignedPartition.get(myId);
        return partitions.get(partitionId);
    }

    /**
     * 返回我被分配到的分区 id
     */
    public int getAssignedPartitionId() {
        EntityID myId = agentInfo.getID();
        return agentAssignedPartition.get(myId);
    }

    /**
     * 返回分区平均大小（其中 area 数量）
     */
    public int getAvgSizeOfPartition() {
        return avgSizeOfPartition;
    }

    /**
     * 返回我当前所在分区
     */
    @Nonnull
    public Partition<EntityID> getCurrentPartition() {
        EntityID myId = agentInfo.getID();
        EntityID myPosAreaId = humanPositionTool.getPositionAreaId(myId);
        int partitionId = areaWhichPartition.get(myPosAreaId);
        return partitions.get(partitionId);
    }

    /**
     * 返回我当前所在分区 id
     */
    public int getCurrentPartitionId() {
        EntityID myId = agentInfo.getID();
        EntityID myPosAreaId = humanPositionTool.getPositionAreaId(myId);
        return areaWhichPartition.get(myPosAreaId);
    }

    /**
     * 返回我当前的工作分区（所在分区 + 被分配到的分区）
     */
    public Partition<EntityID> getWorkingPartition() {
        if (getAssignedPartitionId() != getCurrentPartitionId())
            return new MultiplePartition<>(getAssignedPartition(), getCurrentPartition());
        else return getAssignedPartition();
    }

    /**
     * 返回指定 id 的分区
     */
    public Partition<EntityID> getPartition(int id) {
        return partitions.get(id);
    }

    /**
     * 获得指定 area 在哪个分区里
     * @param id area 的 EntityID
     * @return 分区的 id，再使用 {@link StaticPartitionTool#getPartition(int)} 即可获得分区
     */
    public int whichPartition(@Nonnull EntityID id) {
        Integer partitionId = areaWhichPartition.get(id);
        return partitionId == null
                ? -1
                : partitionId;
    }

    private void init() {
        StandardEntity me = agentInfo.me();

        // 使用 KMeans++ 算法计算出分区中心
        List<Area> centers = calcCenters(getElements(), getNumCenters());
        logger.info("Centers found! " + centers);

        // 分区
        areaWhichPartition = partitioning(centers);
        logger.info("Partitioning finished!");

        Map<Integer, Set<EntityID>> id2areasInIt = areaWhichPartition.entrySet().stream()
                .collect(groupingBy(Map.Entry::getValue, mapping(Map.Entry::getKey, toUnmodifiableSet())));
        for (Map.Entry<Integer, Set<EntityID>> entry : id2areasInIt.entrySet()) {
            int id = entry.getKey();
            Set<EntityID> areaIds = entry.getValue();
            Area center = centers.get(id);
            SinglePartition<EntityID> partition = new SinglePartition<>(id, areaIds, center.getID());
            partitions.add(partition);
        }

        // 分派智能体
        assignAgents(getTeamAgentIds().toList());

        // 计算中心间平均距离
        avgDistanceBetweenCenters = calcAvgDistanceBetweenCenters(centers);
        logger.info("avgDistanceBetweenCenters=" + avgDistanceBetweenCenters);

        // 计算每个分区的邻居分区
        neighborPartitions = calcNeighborPartitions(areaWhichPartition, centers);
        logger.info("neighborPartitions=" + neighborPartitions);
    }

    /**
     * 返回分区中心间的平均距离
     */
    public double getAvgDistanceBetweenCenters() {
        return avgDistanceBetweenCenters;
    }

    /**
     * 获得一个分区的所有邻居分区 id
     */
    public Set<Integer> getNeighborPartition(int partitionId) {
        return neighborPartitions.get(partitionId);
    }

    private Stream<EntityID> getTeamAgentIds() {
        Class<? extends StandardEntity> myClass = agentInfo.me().getClass();
        return worldInfo.getAllEntities()
                .stream()
                .filter(myClass::isInstance)
                .map(StandardEntity::getID);
    }

    private Stream<Area> getElements() {
        return worldInfo.getAllEntities()
                .stream()
                .filter(Area.class::isInstance)
                .map(Area.class::cast);
    }

    private int getNumCenters() {
        StandardEntity me = agentInfo.me();
        if (me instanceof PoliceForce)
            return scenarioInfo.getScenarioAgentsPf();
        else if (me instanceof FireBrigade)
            return scenarioInfo.getScenarioAgentsFb();
        else if (me instanceof AmbulanceTeam)
            return scenarioInfo.getScenarioAgentsAt();
        throw new RuntimeException("Unknown agent type " + me);
    }

    public static double dist(double Ax, double Ay, double Bx, double By) {
        return Math.hypot(Ax - Bx, Ay - By);
    }

    private EntityID getNearestAgent(
            @Nonnull double[][] costMatrix,
            @Nonnull List<EntityID> agentIds,
            int clusterId
    ) {
        EntityID result = null;
        double cost = Integer.MAX_VALUE;
        for (EntityID agentId : agentIds) {
            if (result == null) {
                result = agentId;
            } else {
                if (costMatrix[agentIds.indexOf(agentId)][clusterId] < cost) {
                    result = agentId;
                    cost = costMatrix[agentIds.indexOf(agentId)][clusterId];
                }
            }
        }
        return result;
    }

    private void assignAgents(@Nonnull List<EntityID> agentIdsToAssign) {
        final int numPartitions = partitions.size();
        List<Human> agents = agentIdsToAssign.stream()
                .map(worldInfo::getEntity)
                .filter(Human.class::isInstance)
                .map(Human.class::cast)
                .toList();
        double[][] centerArray = new double[numPartitions][3];
        for (int i = 0; i < numPartitions; i++) {
            centerArray[i][0] = 0;
            centerArray[i][1] = 0;
            centerArray[i][2] = 0;
        }
        for (Map.Entry<EntityID, Integer> e : areaWhichPartition.entrySet()) {
            final EntityID areaId = e.getKey();
            final int partitionIndex = e.getValue();
            final Area area = (Area) worldInfo.getEntity(areaId);
            assert area != null;
            centerArray[partitionIndex][0] += area.getX();
            centerArray[partitionIndex][1] += area.getY();
            centerArray[partitionIndex][2] += 1;
        }
        for (int i = 0; i < numPartitions; i++) {
            if (centerArray[i][2] != 0) {
                centerArray[i][0] /= centerArray[i][2];
                centerArray[i][1] /= centerArray[i][2];
            }
        }
        double costs[][] = new double[agents.size()][numPartitions];
        for (int i = 0; i < costs.length; i++) {
            for (int j = 0; j < costs[0].length; j++) {
                Human agent = agents.get(i);
                double ax = agent.getX();
                double ay = agent.getY();
                double dist = dist(ax, ay, centerArray[j][0], centerArray[j][1]);
                costs[i][j] = dist;
            }
        }
        int[] allocations = new HungarianAgentAssign(costs).execute();
        for (int i = 0; i < agentIdsToAssign.size(); i ++)
            agentAssignedPartition.put(agentIdsToAssign.get(i), allocations[i]);
    }

    /**
     * 使用 KMeans++ 算法，计算出最合适的分区中心
     * @param areas 所有 areas
     * @param numCenters 中心数
     * @return 选为中心的 areas
     */
    private List<Area> calcCenters(
            @Nonnull Stream<Area> areas,
            int numCenters
    ) {
        // 生成 cluster nodes
        final List<ClusterNode> clusterNodes = areas
                .map(ClusterNode::new)
                .toList();

        // 使用 Kmeans++ 算法
        final KMeansPlusPlusClusterer<ClusterNode> kmeans =
                new KMeansPlusPlusClusterer<>(numCenters, params.getInt("maxIterations"));
        kmeans.getRandomGenerator().setSeed(1);
        final List<CentroidCluster<ClusterNode>> clusters = kmeans.cluster(clusterNodes);

        // 计算每个聚类中心位于哪个 Area
        final List<Area> centers = new ArrayList<>();
        for (CentroidCluster<ClusterNode> cluster : clusters) {
            double[] centerPoint = cluster.getCenter().getPoint();
            // 以 10000 为初始半径向外扩张搜索
            for (int range = 10000; ; range += 10000) {
                Collection<StandardEntity> entities =
                        worldInfo.getObjectsInRange((int) centerPoint[0], (int) centerPoint[1], range);
                Area nearestArea = entities.stream()
                        .filter(Area.class::isInstance)
                        .map(Area.class::cast)
                        .min(Comparator.comparingDouble(area -> dist(centerPoint[0], centerPoint[1], area.getX(), area.getY())))
                        .orElse(null);
                if (nearestArea != null) { // 找到了距离最近的 area
                    centers.add(nearestArea);
                    break;
                }
            }
        }

        return centers;
    }

    /**
     * 生成分区
     * @param centers 指定的初始分区中心
     * @return 一个 map，指示每个 area 被分到哪个分区
     */
    protected Map<EntityID, Integer> partitioning(@Nonnull List<Area> centers) {
        final int numCenters = centers.size();
        final Map<EntityID, Integer> areaWhichPartition = new HashMap<>();
        for (int i = 0; i < numCenters; i ++)
            areaWhichPartition.put(centers.get(i).getID(), i);

        // 记录每个分区中元素个数
        final int[] elementCounts = new int[numCenters];
        // 每个分区的扩张边界
        final Queue<Area>[] expandQueues = new Queue[numCenters];
        // 记录 elementCounts 中第 i 大元素的下标
        final Integer[] ithIndexes = new Integer[numCenters];
        // 记录扩张的 area 距离中心的距离
        final Map<Area, Double>[] distMaps = new Map[numCenters];

        // 一开始，每个分区内只有中心一个元素
        for (int i = 0; i < numCenters; i ++) {
            elementCounts[i] = 1;
            Area center = centers.get(i);
            distMaps[i] = new HashMap<>();
            Map<Area, Double> distMap = distMaps[i];
            distMap.put(center, 0.0);
            expandQueues[i] = new PriorityQueue<>( // 距离中心近的优先扩展
                    Comparator.comparingDouble(distMap::get));
            expandQueues[i].offer(center); // 中心先加入队列
            ithIndexes[i] = i;
        }

        for (;;) { // 每次选出元素最少的分区扩张，直至所有分区都无法扩张
            Arrays.sort(ithIndexes, Comparator.comparingInt(i -> elementCounts[i]));
            logger.debug(Arrays.toString(elementCounts));
            int i;
            for (i = 0; i < numCenters; i ++) {
                int partitionIndex = ithIndexes[i];
                Queue<Area> expandQueue = expandQueues[partitionIndex];
                Map<Area, Double> distMap = distMaps[partitionIndex];
                if (expandQueue.isEmpty())
                    continue;
                Area first = expandQueue.poll();
                double dist = distMap.get(first);
                areaWhichPartition.put(first.getID(), partitionIndex);
                for (EntityID neighborId : first.getNeighbours()) {
                    Area neighbor = (Area) worldInfo.getEntity(neighborId);
                    if (neighbor == null)
                        continue;
                    if (areaWhichPartition.containsKey(neighborId))
                        continue;
                    distMap.put(neighbor, dist + worldInfo.getDistance(first, neighbor));
                    expandQueue.offer(neighbor);
                    elementCounts[partitionIndex] += 1;
                }
                break;
            }
            if (i >= numCenters)
                break;
        }

        avgSizeOfPartition = (int) Arrays.stream(elementCounts).average().getAsDouble();

        return areaWhichPartition;
    }

    private double calcAvgDistanceBetweenCenters(@Nonnull List<Area> centers) {
        double tot = 0.0;
        int N = centers.size();
        if (N == 0) {
            logger.debug("Empty data set! Average distance between samples is set to 0.0.");
            return 0.0;
        }
        for (int i = 0; i < N; i ++) {
            for (int j = i +1; j < N; j ++) {
                Area a1 = centers.get(i);
                Area a2 = centers.get(j);
                tot += worldInfo.getDistance(a1, a2);
            }
        }
        return 2.0 * tot / (N * (N - 1));
    }

    private Map<Integer, Set<Integer>> calcNeighborPartitions(
            @Nonnull Map<EntityID, Integer> areaWhichPartition,
            @Nonnull List<Area> centers
    ) {
        final Map<Integer, Set<Integer>> result = new HashMap<>();
        final Queue<Area> searchQueue = new ArrayDeque<>();
        final Set<EntityID> visited = new HashSet<>();
        for (int i = 0; i < centers.size(); i ++) {
            final Area center = centers.get(i);
            final Set<Integer> neighborPartitionIds = new HashSet<>();
            searchQueue.clear();
            searchQueue.add(center);
            visited.clear();
            while (!searchQueue.isEmpty()) {
                final Area first = searchQueue.poll();
                visited.add(first.getID());
                for (EntityID neighborId : first.getNeighbours()) {
                    Integer partitionId = areaWhichPartition.get(neighborId);
                    if (visited.contains(neighborId))
                        continue;
                    if (partitionId != i)
                        neighborPartitionIds.add(partitionId);
                    else searchQueue.add((Area) worldInfo.getEntity(neighborId));
                }
            }
            result.put(i, neighborPartitionIds);
        }
        return result;
    }

    private record ClusterNode(Area area) implements Clusterable {
        private ClusterNode(@Nonnull Area area) {
            this.area = area;
        }
        @Override
        public double[] getPoint() {
            return new double[]{area.getX(), area.getY()};
        }
    }

}
