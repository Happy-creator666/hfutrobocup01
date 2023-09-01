package hfutinvincible.tool.comm;

import adf.core.agent.develop.DevelopData;
import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.ScenarioInfo;
import adf.core.agent.info.WorldInfo;
import adf.core.agent.module.ModuleManager;
import adf.core.agent.precompute.PrecomputeData;
import hfutinvincible.anno.AgentType;
import hfutinvincible.anno.Autowired;
import hfutinvincible.anno.HfutTool;
import hfutinvincible.common.BitArray;
import hfutinvincible.tool.LifeCycle;
import hfutinvincible.tool.edge.HFUTEdge;
import rescuecore2.misc.Pair;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.Entity;
import rescuecore2.worldmodel.EntityID;

import java.util.*;

/**
 * 由于 Property 做了压缩编码，编解码时需要反复用到一些数据，
 * 因此将这些数据的初始化单独用 PropertyManageTool 模块管理，
 * 并尽可能提前到预计算阶段.
 *
 * @author stardust
 */
@HfutTool
@AgentType({PoliceForce.class, AmbulanceTeam.class, FireBrigade.class})
public class PropertyManageTool implements LifeCycle {

    @Autowired
    WorldInfo worldInfo;

    // AreaIdProperty 需要的数据
    private final List<EntityID> areaIdList;
    private Map<EntityID, Integer> areaIdToIndex;
    private int areaIdListBitLength;

    // BuildingIdProperty 需要的数据
    private final List<EntityID> buildingIdList;
    private final Map<EntityID, Integer> buildingIdToIndex;
    private int buildingIdListBitLength;

    // PfIdProperty 需要的数据
    private final List<EntityID> pfIdList;
    private final Map<EntityID, Integer> pfIdToIndex;
    private int pfIdListBitLength;

    // AtIdProperty 需要的数据
    private final List<EntityID> atIdList;
    private final Map<EntityID, Integer> atIdToIndex;
    private int atIdListBitLength;

    // FbIdProperty 需要的数据
    private final List<EntityID> fbIdList;
    private final Map<EntityID, Integer> fbIdToIndex;
    private int fbIdListBitLength;

    // AgentIdProperty 需要的数据
    private final List<EntityID> agentIdList;
    private final Map<EntityID, Integer> agentIdToIndex;
    private int agentIdListBitLength;

    public List<EntityID> getPfIdList() {
        return pfIdList;
    }

    public Map<EntityID, Integer> getPfIdToIndex() {
        return pfIdToIndex;
    }

    public List<EntityID> getAtIdList() {
        return atIdList;
    }

    public Map<EntityID, Integer> getAtIdToIndex() {
        return atIdToIndex;
    }

    public List<EntityID> getFbIdList() {
        return fbIdList;
    }

    public Map<EntityID, Integer> getFbIdToIndex() {
        return fbIdToIndex;
    }

    // RefugeIdProperty 需要的数据
    private final List<EntityID> refugeIdList;
    private final Map<EntityID, Integer> refugeIdToIndex;
    private int refugeIdListBitLength;

    // AreaAccessibleProperty 需要的数据 TODO 需要删除
    private final Map<Area, List<EntityID>> neighborMap;
    private int neighbourCountExp;
    private int neighbourIndexExp;

    // AreaEdgePairPassableMessage 需要的数据
    private final Map<EntityID, List<Pair<HFUTEdge, HFUTEdge>>> areaEdgePairsMap;

    public PropertyManageTool() {
        this.areaIdList = new ArrayList<>();
        this.areaIdToIndex = new HashMap<>();
        this.buildingIdList = new ArrayList<>();
        this.buildingIdToIndex = new HashMap<>();
        this.pfIdList = new ArrayList<>();
        this.pfIdToIndex = new HashMap<>();
        this.atIdList = new ArrayList<>();
        this.atIdToIndex = new HashMap<>();
        this.fbIdList = new ArrayList<>();
        this.fbIdToIndex = new HashMap<>();
        this.agentIdList = new ArrayList<>();
        this.agentIdToIndex = new HashMap<>();
        this.refugeIdList = new ArrayList<>();
        this.refugeIdToIndex = new HashMap<>();
        this.neighborMap = new HashMap<>();
        this.areaEdgePairsMap = new HashMap<>();
    }

    @Override
    public void resume(PrecomputeData precomputeData) {
        calc();
    }

    @Override
    public void preparate() {
        calc();
    }

    public int getPfIdListBitLength() {
        return pfIdListBitLength;
    }

    public int getAtIdListBitLength() {
        return atIdListBitLength;
    }

    public int getFbIdListBitLength() {
        return fbIdListBitLength;
    }

    public PropertyManageTool calc() {
        Collection<StandardEntity> entities = worldInfo.getAllEntities();
        this.areaIdToIndex = new HashMap<>(entities.size());
//        统计每个Entity数量并且记录
        int countArea = 0;
        int countBuilding = 0;
        int countPf = 0;
        int countAt = 0;
        int countFb = 0;
        int countAgent = 0;
        int countRefuge = 0;
        int maxLenOfAreaNeighborList = 0;
        for (StandardEntity entity: entities) {
            EntityID id = entity.getID();
            if (entity instanceof Area area) {
                this.areaIdList.add(id);
                this.areaIdToIndex.put(id, countArea++);

                Set<EntityID> areaSet = new HashSet<>(area.getNeighbours());
                List<EntityID> areaNeighborList = new ArrayList<>(areaSet);
                areaNeighborList.sort(new EntityIdComparator());
                this.neighborMap.put(area, areaNeighborList);
                if (areaNeighborList.size() > maxLenOfAreaNeighborList)
                    maxLenOfAreaNeighborList = areaNeighborList.size();

                if (entity instanceof Building) {
                    this.buildingIdList.add(id);
                    this.buildingIdToIndex.put(id, countBuilding++);
                }

                if (entity instanceof Refuge) {
                    this.refugeIdList.add(id);
                    this.refugeIdToIndex.put(id, countRefuge++);
                }

            } else if ((entity instanceof PoliceForce) ||
                    (entity instanceof FireBrigade) ||
                    (entity instanceof AmbulanceTeam)) {
                this.agentIdList.add(id);
                this.agentIdToIndex.put(id, countAgent++);

                if (entity instanceof PoliceForce) {
                    this.pfIdList.add(id);
                    this.pfIdToIndex.put(id, countPf++);
                } else if (entity instanceof AmbulanceTeam) {
                    this.atIdList.add(id);
                    this.atIdToIndex.put(id, countAt++);
                } else if (entity instanceof FireBrigade) {
                    this.fbIdList.add(id);
                    this.fbIdToIndex.put(id, countFb++);
                }
            }
        }
        this.areaIdListBitLength = areaIdList.isEmpty() ? 0 :
                BitArray.upperBitLength(areaIdList.size() - 1);
        this.buildingIdListBitLength = buildingIdList.isEmpty() ? 0 :
                BitArray.upperBitLength(buildingIdList.size() - 1);
        this.agentIdListBitLength = agentIdList.isEmpty() ? 0 :
                BitArray.upperBitLength(agentIdList.size() - 1);
        this.refugeIdListBitLength = refugeIdList.isEmpty() ? 0 :
                BitArray.upperBitLength(refugeIdList.size() - 1);
        this.pfIdListBitLength = pfIdList.isEmpty() ? 0 :
                BitArray.upperBitLength(pfIdList.size() - 1);
        this.atIdListBitLength = atIdList.isEmpty() ? 0 :
                BitArray.upperBitLength(atIdList.size() - 1);
        this.fbIdListBitLength = fbIdList.isEmpty() ? 0 :
                BitArray.upperBitLength(fbIdList.size() - 1);
        this.neighbourCountExp = BitArray.upperBitLength(maxLenOfAreaNeighborList);
        this.neighbourIndexExp = BitArray.upperBitLength(maxLenOfAreaNeighborList - 1);

//        记录每一个Area上的边对
        for (EntityID areaId : areaIdList) {
            StandardEntity entity = worldInfo.getEntity(areaId);
            if (entity instanceof Area area) {
                List<Edge> edges = area.getEdges();
                List<Pair<HFUTEdge, HFUTEdge>> edgePairs = new ArrayList<>();
                for (int i = 0; i < edges.size(); i++) {
                    Edge ei = edges.get(i);
//                    过滤无效边
                    if (!ei.isPassable()) continue;
                    for (int j = i + 1; j < edges.size(); j++) {
                        Edge ej = edges.get(j);
                        if (!ej.isPassable()) continue;
                        edgePairs.add(new Pair<>(new HFUTEdge(ei), new HFUTEdge(ej)));
                    }
                }
                this.areaEdgePairsMap.put(areaId, edgePairs);
            }
        }

        return this;
    }

    public List<EntityID> getAreaIdList() {
        return areaIdList;
    }

    public Map<EntityID, Integer> getAreaIdToIndex() {
        return areaIdToIndex;
    }

    public int getAreaIdListBitLength() {
        return areaIdListBitLength;
    }

    public List<EntityID> getBuildingIdList() {
        return buildingIdList;
    }

    public Map<EntityID, Integer> getBuildingIdToIndex() {
        return buildingIdToIndex;
    }

    public int getBuildingIdListBitLength() {
        return buildingIdListBitLength;
    }

    public List<EntityID> getAgentIdList() {
        return agentIdList;
    }

    public Map<EntityID, Integer> getAgentIdToIndex() {
        return agentIdToIndex;
    }

    public int getAgentIdListBitLength() {
        return agentIdListBitLength;
    }

    public List<EntityID> getRefugeIdList() {
        return refugeIdList;
    }

    public Map<EntityID, Integer> getRefugeIdToIndex() {
        return refugeIdToIndex;
    }

    public int getRefugeIdListBitLength() {
        return refugeIdListBitLength;
    }

    public Map<Area, List<EntityID>> getNeighborMap() {
        return neighborMap;
    }

    public int getNeighbourCountExp() {
        return neighbourCountExp;
    }

    public int getNeighbourIndexExp() {
        return neighbourIndexExp;
    }

    public WorldInfo getWorldInfo() {
        return this.worldInfo;
    }

    public Map<EntityID, List<Pair<HFUTEdge, HFUTEdge>>> getAreaEdgePairsMap() {
        return areaEdgePairsMap;
    }

    private static class EntityComparator implements Comparator<Entity> {
        private final Map<Class<?>, Integer> classMap = Map.ofEntries(
                Map.entry(World.class, 0),
                Map.entry(PoliceForce.class, 1),
                Map.entry(FireBrigade.class, 2),
                Map.entry(AmbulanceTeam.class, 3),
                Map.entry(Civilian.class, 4),
                Map.entry(Human.class, 5),
                Map.entry(Building.class, 6),
                Map.entry(Refuge.class, 7),
                Map.entry(PoliceOffice.class, 8),
                Map.entry(FireStation.class, 9),
                Map.entry(AmbulanceCentre.class, 10),
                Map.entry(GasStation.class, 11),
                Map.entry(Hydrant.class, 12),
                Map.entry(Road.class, 13),
                Map.entry(Area.class, 14),
                Map.entry(Blockade.class, 15),
                Map.entry(StandardEntity.class, 16)
        );

        private int getIndex(Class<?> clazz) {
            Integer index = classMap.get(clazz);
            if (index == null)
                return classMap.size();
            else return index;
        }

        @Override
        public int compare(Entity o1, Entity o2) {
            if (o1 == null && o2 == null)
                return 0;
            else if (o1 == null)
                return -1;
            else if (o2 == null)
                return 1;
            int i1 = getIndex(o1.getClass());
            int i2 = getIndex(o2.getClass());
            if (i1 != i2) {
                return i1 - i2;
            }
            else {
                String s1 = o1.getID().toString();
                String s2 = o2.getID().toString();
                return s1.compareToIgnoreCase(s2);
            }
        }
    }

    private class EntityIdComparator implements Comparator<EntityID> {
        @Override
        public int compare(EntityID o1, EntityID o2) {
            Entity e1 = worldInfo.getEntity(o1);
            Entity e2 = worldInfo.getEntity(o2);
            return (new EntityComparator()).compare(e1, e2);
        }
    }
}
