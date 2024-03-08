package hfutinvincible.tool.targetprovider;

import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.WorldInfo;
import hfutinvincible.Tuple2;
import hfutinvincible.anno.AgentType;
import hfutinvincible.anno.Autowired;
import hfutinvincible.anno.HfutTool;
import hfutinvincible.tool.*;
import hfutinvincible.tool.comm.MessageManageTool;
import hfutinvincible.tool.edge.AreaPassableTool;
import hfutinvincible.tool.edge.HFUTEdge;
import hfutinvincible.tool.human.BuriednessTool;
import hfutinvincible.tool.human.HumanPositionTool;
import hfutinvincible.tool.human.TimeToDeadTool;
import hfutinvincible.tool.misc.OptionsTool;
import hfutinvincible.tool.partition.Partition;
import hfutinvincible.tool.partition.StaticPartitionTool;
import hfutinvincible.tool.path.InPartitionSearchPathTool;
import org.apache.log4j.Logger;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;

import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@HfutTool
@AgentType({AmbulanceTeam.class})
public class AtTargetProvider implements LifeCycle,TargetProvider<Area> {


    // 防止因为在两个分区间移动导致前一个周期选中的目标下一个周期都不会作为候选，
    // 记录前一个周期的目标，并添加到这个周期的候选中
    private Area prevTarget;

    @Autowired
    AgentInfo agentInfo;
    @Autowired
    WorldInfo worldInfo;
    @Autowired
    Logger logger;
    //Tools
    @Autowired
    StaticPartitionTool staticPartitionTool;
    @Autowired
    HumanPositionTool humanPositionTool;
    @Autowired
    MessageManageTool messageTool;
    @Autowired
    InPartitionSearchPathTool inPartitionSearchPathTool;
    @Autowired
    AreaPassableTool areaPassableTool;
    @Autowired
    TimeToDeadTool timeToDeadTool;
    @Autowired
    UnavailableTargetTool unavailableTargetTool;
    //Params
    private Params params = new Params(getClass().getSimpleName());

    @Override
    public List<Tuple2<Area, Double>> getSortedCandidatesAndScores() {
        Map<Area, Integer> TargetWithItsPriority = new HashMap<>();
        boolean withHumanLoaded = agentInfo.someoneOnBoard() != null;
        Set<Area> candidates = new HashSet<>();
        if(withHumanLoaded){
            // 如果装了人，则将所有 refuges 作为目标
            // 无法准确的获取refuges的剩余容量
            for (StandardEntity e : worldInfo.getEntitiesOfType(StandardEntityURN.REFUGE))
                if (e instanceof Refuge refuge)
                    candidates.add(refuge);
        } else {
            // 被分派到的分区内所有未被标记为 unavailable 的 building 加入
            Partition<EntityID> myAssignedPartition = staticPartitionTool.getAssignedPartition();
            myAssignedPartition.getElements()
                    .filter(id -> !unavailableTargetTool.isUnavailable(
                            id,
                            UnavailableType.block,
                            UnavailableType.noHurtNotSure,
                            UnavailableType.noHurtDefinitely,
                            UnavailableType.buried))
                    .map(worldInfo::getEntity)
                    .filter(Building.class::isInstance)
                    .map(Building.class::cast)
                    .filter(b -> !(b instanceof Refuge)) // 先不考虑 refuge
                    .forEach(candidates::add);

            //Todo 错误的目标选取

            // 指定周期内能到达的有受伤的人的 buildings 加入
            Stream.concat(timeToDeadTool.getHurtHumanIds(),
                            worldInfo.getEntityIDsOfType(StandardEntityURN.CIVILIAN).stream())
                    .map(humanPositionTool::getPositionAreaId)
                    .filter(Objects::nonNull)
                    .filter(id -> !unavailableTargetTool.isUnavailable(
                            id,
                            UnavailableType.block,
                            UnavailableType.noHurtNotSure,
                            UnavailableType.noHurtDefinitely,
                            UnavailableType.buried))
                    .map(worldInfo::getEntity)
                    .filter(Building.class::isInstance)
                    .map(Building.class::cast)
                    .filter(b -> !(b instanceof Refuge))
                    .filter(building -> {
                        List<HFUTEdge> edges = areaPassableTool.getHfutEdgesOf(building.getID());
                        if (edges == null || edges.isEmpty())
                            return false;
                        double minPassCost = edges.stream()
                                .mapToDouble(inPartitionSearchPathTool::getCostOrDefault)
                                .min()
                                .orElse(Double.MAX_VALUE);
                        int minPassSteps = (int) (minPassCost / OptionsTool.MOVING_DISTANCE);
                        return minPassSteps < params.getInt("hurtHumanConsiderRange");
                    })
                    .forEach(candidates::add);

            // 上一周期的目标（如果有，且不是 unavailable 的）也加入
            if (prevTarget != null
                    && !(prevTarget instanceof Refuge)
                    && !unavailableTargetTool.isUnavailable(
                    prevTarget.getID(),
                    UnavailableType.block,
                    UnavailableType.noHurtNotSure,
                    UnavailableType.noHurtDefinitely,
                    UnavailableType.buried))
                candidates.add(prevTarget);

            // 如果目标不够，再将以我为中心，通行代价递增，逐步加入目标
            List<Building> allBuildings = worldInfo.getAllEntities()
                    .stream()
                    .filter(Building.class::isInstance)
                    .map(Building.class::cast)
                    .toList();
            int start = params.getInt("additionalCandidatesStartRadius");
            int max = params.getInt("additionalCandidatesMaxRadius");
            int delta = params.getInt("additionalCandidatesDeltaRadius");
            List<Building> additionalCandidates = new ArrayList<>();
            for (int i = start; i < max; i += delta) {
                additionalCandidates.clear();
                for (Building b : allBuildings) {
                    if (b instanceof Refuge) // 忽略 refuge
                        continue;
                    if (unavailableTargetTool.isUnavailable(
                            b.getID(),
                            UnavailableType.block,
                            UnavailableType.noHurtNotSure,
                            UnavailableType.noHurtDefinitely,
                            UnavailableType.buried))
                        continue;
                    List<HFUTEdge> edges = areaPassableTool.getHfutEdgesOf(b.getID());
                    if (edges == null || edges.isEmpty())
                        continue;
                    double minPassCost = edges.stream()
                            .mapToDouble(inPartitionSearchPathTool::getCostOrDefault)
                            .min()
                            .orElse(Double.MAX_VALUE);
                    int minPassSteps = (int) (minPassCost / OptionsTool.MOVING_DISTANCE);
                    if (minPassSteps < i)
                        additionalCandidates.add(b);
                }
                int enoughTargetsThreshold = params.getInt("enoughTargetsThreshold");
                if (candidates.size() + additionalCandidates.size() > enoughTargetsThreshold)
                    break;
            }
            candidates.addAll(additionalCandidates);
        }
        // TODO 如果目标还不够，把因为 block 而 unavailable 的去掉再来

        //Todo 需要更改目标的优先级程度
        /**将剩余存在于candidates中的所有目标的优先级设置为最低 */
        candidates.stream()
                .map(targets -> (Area)targets)
                .forEach(area -> TargetWithItsPriority.put(area,2));
        /**将存在有受伤的人的 buildings的优先级设置为第二层 */
        candidates.stream()
                .filter(builds -> builds instanceof Building)
                .map(Building.class::cast)
                .filter(b -> !(b instanceof Refuge))
                .filter(building -> {
                    List<HFUTEdge> edges = areaPassableTool.getHfutEdgesOf(building.getID());
                    if (edges == null || edges.isEmpty())
                        return false;
                    double minPassCost = edges.stream()
                            .mapToDouble(inPartitionSearchPathTool::getCostOrDefault)
                            .min()
                            .orElse(Double.MAX_VALUE);
                    int minPassSteps = (int) (minPassCost / OptionsTool.MOVING_DISTANCE);
                    return minPassSteps < params.getInt("hurtHumanConsiderRange");
                })
                .forEach(building -> TargetWithItsPriority.put(building, 1));

        /** 当装有人时将Refuge的优先级设置为最高*/
        candidates.stream()
                .filter(buildings -> buildings instanceof Refuge)
                .map(refuges -> (Area) refuges)
                .forEach(building -> TargetWithItsPriority.put(building, 0));

        return TargetWithItsPriority.entrySet().stream()
                .sorted(Map.Entry.<Area,Integer>comparingByValue())
                .map(res -> Tuple2.of(res.getKey(),(double)res.getValue()))
                .collect(Collectors.toList());
    }

    @Override
    public void afterCalc(CalcContext context) {
        if(context.getTarget()!=null){
            prevTarget =context.getTarget();
        }
    }
}

