package hfutinvincible.tool.building;

import adf.core.agent.develop.DevelopData;
import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.ScenarioInfo;
import adf.core.agent.info.WorldInfo;
import adf.core.agent.module.ModuleManager;
import adf.core.agent.precompute.PrecomputeData;
import hfutinvincible.anno.AgentType;
import hfutinvincible.anno.Autowired;
import hfutinvincible.anno.HfutTool;
import hfutinvincible.tool.LifeCycle;
import hfutinvincible.tool.misc.OptionsTool;
import org.apache.log4j.Logger;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@HfutTool
@AgentType({AmbulanceTeam.class, PoliceForce.class, FireBrigade.class})
public class BuildingEntranceTool implements LifeCycle {
    /**
     * 距离建筑入口的接近程度
     */
    private Map<EntityID, Double> buildingEntranceScore;

    /**
     * 距离 refuge 入口的接近程度
     */
    private Map<EntityID, Double> refugeEntranceScore;

    private double maxBuildingProximity;

    private Set<EntityID> refugeEntrances;

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

    @Override
    public void precompute(PrecomputeData precomputeData) {
        {
            calcRefugeEntrances();
            final String className = this.getClass().getCanonicalName();
            List<EntityID> refugeEntranceList = refugeEntrances.stream().toList();
            precomputeData.setEntityIDList(className + ".refugeentrancelist", refugeEntranceList);
        }
        {
            calcBuildingEntranceProximity();
            final String className = this.getClass().getCanonicalName();
            var entrySet = buildingEntranceScore.entrySet();
            List<EntityID> keyList = entrySet.stream().map(Map.Entry::getKey).toList();
            List<Double> valueList = entrySet.stream().map(Map.Entry::getValue).toList();
            precomputeData.setEntityIDList(className + ".buildingkeylist", keyList);
            precomputeData.setDoubleList(className + ".buildingvaluelist", valueList);
        }

        {
            calcRefugeEntranceProximity();
            final String className = this.getClass().getCanonicalName();
            var entrySet = refugeEntranceScore.entrySet();
            List<EntityID> keyList = entrySet.stream().map(Map.Entry::getKey).toList();
            List<Double> valueList = entrySet.stream().map(Map.Entry::getValue).toList();
            precomputeData.setEntityIDList(className + ".refugekeylist", keyList);
            precomputeData.setDoubleList(className + ".refugevaluelist", valueList);
        }
    }

    @Override
    public void resume(PrecomputeData precomputeData) {
        {
            final String className = this.getClass().getCanonicalName();
            refugeEntrances = new HashSet<>(precomputeData.getEntityIDList(className + ".refugeentrancelist"));
        }
        {
            final String className = this.getClass().getCanonicalName();
            List<EntityID> keyList = precomputeData.getEntityIDList(className + ".buildingkeylist");
            List<Double> valueList = precomputeData.getDoubleList(className + ".buildingvaluelist");
            assert keyList != null && valueList != null && keyList.size() == valueList.size();

            buildingEntranceScore = IntStream.range(0, keyList.size())
                    .boxed()
                    .collect(Collectors.toMap(keyList::get, valueList::get));

            maxBuildingProximity = 0.0;
            for (Double val : buildingEntranceScore.values()) {
                if (val > maxBuildingProximity)
                    maxBuildingProximity = val;
            }
        }

        {
            final String className = this.getClass().getCanonicalName();

            List<EntityID> keyList = precomputeData.getEntityIDList(className + ".refugekeylist");
            List<Double> valueList = precomputeData.getDoubleList(className + ".refugevaluelist");
            assert keyList != null && valueList != null && keyList.size() == valueList.size();

            refugeEntranceScore = IntStream.range(0, keyList.size())
                    .boxed()
                    .collect(Collectors.toMap(keyList::get, valueList::get));
        }
    }

    @Override
    public void preparate() {
        calcRefugeEntrances();
        calcBuildingEntranceProximity();
        calcRefugeEntranceProximity();
        maxBuildingProximity = 0.0;
        for (Double val : buildingEntranceScore.values()) {
            if (val > maxBuildingProximity)
                maxBuildingProximity = val;
        }
    }

    @Nonnull
    public List<Road> getEntrancesOf(@Nonnull EntityID buildingId) {
        Building building = (Building) worldInfo.getEntity(buildingId);
        if (building == null) {
            String errMsg = "Invalid building id " + buildingId;
            throw new RuntimeException(errMsg);
        }
        List<Road> neighbors = new ArrayList<>();
        for (EntityID neighborId : building.getNeighbours()) {
            StandardEntity neighborEntity = worldInfo.getEntity(neighborId);
            if (neighborEntity instanceof Road road)
                neighbors.add(road);
        }
        return neighbors;
    }

    @Nonnull
    public Set<EntityID> getRefugeEntrances() {
        return refugeEntrances;
    }

    @Nonnull Set<EntityID> getBuildingEntrances() {
        return buildingEntranceScore.entrySet()
                .stream()
                .filter(e -> e.getValue() == maxBuildingProximity)
                .map(e -> e.getKey())
                .collect(Collectors.toSet());
    }

    public boolean isBuildingEntrance(@Nonnull EntityID id) {
        Double val = buildingEntranceScore.get(id);
        if (val == null)
            return false;
        return val == maxBuildingProximity;
    }

    public boolean isRefugeEntrance(@Nonnull EntityID id) {
        return refugeEntrances.contains(id);
    }

    private void calcRefugeEntrances() {

        refugeEntrances = new HashSet<>();

        for (StandardEntity entity : worldInfo.getEntitiesOfType(StandardEntityURN.REFUGE)) {
            if (!(entity instanceof Refuge refuge))
                continue;
            for (EntityID neighbor : refuge.getNeighbours()) {
                StandardEntity neighborEntity = worldInfo.getEntity(neighbor);
                if (!(neighborEntity instanceof Road))
                    continue;
                refugeEntrances.add(neighbor);
            }
        }
    }

    private void calcBuildingEntranceProximity() {

        buildingEntranceScore = new HashMap<>();

        Set<EntityID> buildingIds = Set.copyOf(worldInfo.getEntityIDsOfType(StandardEntityURN.BUILDING));

        Deque<EntityID> searchQueue = new ArrayDeque<>(); // 搜索队列
        Map<EntityID, Integer> stepCountMap = new HashMap<>(); // 离起点的距离
        Set<EntityID> determined = new HashSet<>(); // 离起点最短距离已确定的结点

        for (EntityID fromBuildingId : buildingIds) {
            searchQueue.clear();
            stepCountMap.clear();
            determined.clear();

            searchQueue.offer(fromBuildingId);
            stepCountMap.put(fromBuildingId, 0);
            while (!searchQueue.isEmpty()) {
                EntityID currId = searchQueue.poll();
                determined.add(currId);
                Area currArea = (Area) worldInfo.getEntity(currId);
                assert currArea != null;
                int currStepCount = stepCountMap.get(currId);
                int newStepCount = currStepCount + 1;
                if (newStepCount > OptionsTool.BUILDING_ENTRANCE_STOP_PROPAGATION)
                    continue;
                List<EntityID> neighborAreaIds = currArea.getNeighbours()
                        .stream()
                        .filter(id -> !buildingIds.contains(id))
                        .filter(id -> !determined.contains(id))
                        .filter(id -> worldInfo.getEntity(id) instanceof Area)
                        .toList();
                for (EntityID neighborAreaId : neighborAreaIds) {
                    int oldStepCount = stepCountMap.getOrDefault(neighborAreaId, Integer.MAX_VALUE);
                    if (newStepCount < oldStepCount) {
                        stepCountMap.put(neighborAreaId, newStepCount);
                        searchQueue.offer(neighborAreaId);
                    }
                }
            }

//            logger.debug(stepCountMap);

            for (Map.Entry<EntityID, Integer> entry : stepCountMap.entrySet()) {
                EntityID areaId = entry.getKey();
                double stepCount = entry.getValue();
                if (stepCount == 0) // 忽略起点 building
                    continue;
                double newScore = 1.0 / stepCount;
                buildingEntranceScore.compute(areaId, (k, v) ->
                        v == null ? newScore : Math.max(v, newScore));
            }
        }
    }

    private void calcRefugeEntranceProximity() {

        refugeEntranceScore = new HashMap<>();

        Set<EntityID> refugeIds = Set.copyOf(worldInfo.getEntityIDsOfType(StandardEntityURN.REFUGE));

        Deque<EntityID> searchQueue = new ArrayDeque<>(); // 搜索队列
        Map<EntityID, Integer> stepCountMap = new HashMap<>(); // 离起点的距离
        Set<EntityID> determined = new HashSet<>(); // 离起点最短距离已确定的结点

        for (EntityID fromRefugeId : refugeIds) {
            searchQueue.clear();
            stepCountMap.clear();
            determined.clear();

            searchQueue.offer(fromRefugeId);
            stepCountMap.put(fromRefugeId, 0);
            while (!searchQueue.isEmpty()) {
                EntityID currId = searchQueue.poll();
                determined.add(currId);
                Area currArea = (Area) worldInfo.getEntity(currId);
                assert currArea != null;
                int currStepCount = stepCountMap.get(currId);
                int newStepCount = currStepCount + 1;
                if (newStepCount > OptionsTool.BUILDING_ENTRANCE_STOP_PROPAGATION)
                    continue;
                List<EntityID> neighborAreaIds = currArea.getNeighbours()
                        .stream()
                        .filter(id -> !refugeIds.contains(id))
                        .filter(id -> !determined.contains(id))
                        .filter(id -> worldInfo.getEntity(id) instanceof Area)
                        .toList();
                for (EntityID neighborAreaId : neighborAreaIds) {
                    int oldStepCount = stepCountMap.getOrDefault(neighborAreaId, Integer.MAX_VALUE);
                    if (newStepCount < oldStepCount) {
                        stepCountMap.put(neighborAreaId, newStepCount);
                        searchQueue.offer(neighborAreaId);
                    }
                }
            }

//            logger.debug(stepCountMap);

            for (Map.Entry<EntityID, Integer> entry : stepCountMap.entrySet()) {
                EntityID areaId = entry.getKey();
                int stepCount = entry.getValue();
                if (stepCount == 0) // 忽略起点 refuge
                    continue;
                double newScore = 1.0 / stepCount;
                refugeEntranceScore.compute(areaId, (k, v) ->
                        v == null ? newScore : Math.max(v, newScore));
            }
        }
    }

    /**
     * 返回一个 area 和建筑入口的接近程度
     */
    public double getBuildingEntranceProximityOf(@Nonnull EntityID areaId) {
        return buildingEntranceScore.getOrDefault(areaId, 0.0);
    }

    /**
     * 返回一个 area 和 refuge 入口的接近程度
     */
    public double getRefugeEntranceProximityOf(@Nonnull EntityID areaId) {
        return refugeEntranceScore.getOrDefault(areaId, 0.0);
    }
}
