package hfutinvincible.tool.targetprovider;

import adf.core.agent.communication.MessageManager;
import hfutinvincible.anno.Autowired;
import hfutinvincible.tool.CalcContext;
import hfutinvincible.tool.UnavailableTargetTool;
import hfutinvincible.tool.UnavailableType;
import hfutinvincible.tool.criterion.*;
import hfutinvincible.tool.edge.AreaPassableTool;
import hfutinvincible.tool.edge.HFUTEdge;
import hfutinvincible.tool.human.HumanPositionTool;
import hfutinvincible.tool.human.StuckInfoTool;
import hfutinvincible.tool.misc.OptionsTool;
import hfutinvincible.tool.partition.StaticPartitionTool;
import hfutinvincible.tool.path.InPartitionSearchPathTool;
import rescuecore2.standard.entities.Road;
import rescuecore2.standard.entities.Area;
import rescuecore2.worldmodel.EntityID;

import javax.annotation.Nonnull;

import java.util.*;
import java.util.stream.Stream;

public class PfCriteriaBasedTargetProvider extends CriteriaBasedTargetProvider<Area> {
    @Autowired
    StaticPartitionTool staticPartitionTool;
    @Autowired
    HumanPositionTool humanPositionTool;
    @Autowired
    StuckInfoTool stuckInfoTool;
    @Autowired
    UnavailableTargetTool unavailableTargetTool;
    @Autowired
    InPartitionSearchPathTool inPartitionSearchPathTool;
    @Autowired
    AreaPassableTool areaPassableTool;

    // Criteria
    private AreaEdgePairPassableCriterion areaEdgePairPassableCriterion;
    private BuildingEntranceProximityCriterion buildingEntranceProximityCriterion;
    private PathToTargetCriterion pathToTargetCriterion;
    private RefugeEntranceProximityCriterion refugeEntranceProximityCriterion;
    private StuckHumanConditionCriterion stuckHumanConditionCriterion;
    private UnavailableCriterion unavailableCriterion;
    private FbAndAtsTargetPathCriteria fbAndAtsTargetPathCriteria;
    private PartitionCriterion partitionCriterion;
    private TargetBlockCriterion targetBlockCriterion;
    private PrevScoreCriterion prevScoreCriterion;
    private HelpOuchCriterion helpOuchCriterion;

    // Data
    /** 记录每个 area 上被卡的人 */
    private final Map<EntityID, Set<EntityID>> stuckHumanMap;
    /** 记录每个 area 上有哪些人 */
    private final Map<EntityID, Set<EntityID>> humanOnPosMap;
    /** 上一个周期选择的目标 */
    private Area prevTarget;

    public PfCriteriaBasedTargetProvider(){
        this.stuckHumanMap = new HashMap<>();
        this.humanOnPosMap = new HashMap<>();
    }

    @Override
    public void updateInfo(MessageManager messageManager) {
        // 更新 stuckHumanMap
        this.stuckHumanMap.clear();
        this.stuckInfoTool.getStuckHumanIds(OptionsTool.IGNORE_STUCK_HUMAN_AFTER)
                .forEach(humanId -> {
                    EntityID humanPosAreaId = humanPositionTool.getPositionAreaId(humanId);
                    this.stuckHumanMap.compute(humanPosAreaId, (k, v) -> {
                        Set<EntityID> ret = v == null ? new HashSet<>() : v;
                        ret.add(humanId);
                        return ret;
                    });
                });

        // 更新 humanOnPosMap
        this.humanOnPosMap.clear();
        this.humanPositionTool.recordMap.forEach((humanId, value) -> {
            EntityID humanPosId = value.getHumanPosId();
            this.humanOnPosMap.compute(humanPosId, (k, v) -> {
                Set<EntityID> ret = v == null ? new HashSet<>() : v;
                ret.add(humanId);
                return ret;
            });
        });
    }

    @Override
    public void afterCalc(CalcContext context) {
        Area target = context.getTarget();
        if (target != null)
            prevTarget = target;
    }

    @Override
    @Nonnull
    protected List<Area> generateCandidates() {

        Set<Area> candidates = new HashSet<>();

        // 分区里的建筑入口
        staticPartitionTool.getWorkingPartition()
                .getElements()
                .filter(id -> !unavailableTargetTool.isUnavailable(
                        id, UnavailableType.pfNoAction))
                .map(worldInfo::getEntity)
                .filter(Road.class::isInstance) // 只考虑 road，忽略所有建筑
                .map(Area.class::cast)
                .forEach(candidates::add);


        // 卡住人的路
        stuckInfoTool.getStuckHumanIds(OptionsTool.IGNORE_STUCK_HUMAN_AFTER)
                .map(stuckHumanId -> humanPositionTool.getPositionAreaId(stuckHumanId))
                .filter(Objects::nonNull)
                .filter(id -> !unavailableTargetTool.isUnavailable(
                        id, UnavailableType.pfNoAction))
                .flatMap(stuckHumanAreaId -> { // 将卡住的人所在的 area 和其周围 area 加入
                    Area stuckHumanArea = (Area) worldInfo.getEntity(stuckHumanAreaId);
                    if (stuckHumanArea == null)
                        return Stream.of();
                    Stream<Area> neighbors = stuckHumanArea.getNeighbours()
                            .stream()
                            .map(worldInfo::getEntity)
                            .filter(Area.class::isInstance)
                            .map(Area.class::cast);
                    return Stream.concat(Stream.of(stuckHumanArea), neighbors);
                })
                .filter(area -> {
                    List<HFUTEdge> edges = areaPassableTool.getHfutEdgesOf(area.getID());
                    if (edges == null || edges.isEmpty())
                        return false;
                    double minPassCost = edges.stream()
                            .mapToDouble(inPartitionSearchPathTool::getCostOrDefault)
                            .min()
                            .orElse(Double.MAX_VALUE);
                    int minPassSteps = (int) (minPassCost / OptionsTool.MOVING_DISTANCE);
                    return minPassSteps < 5;
                })
                .forEach(candidates::add);

        if (prevTarget != null && !unavailableTargetTool.isUnavailable(prevTarget.getID(), UnavailableType.pfNoAction))
            candidates.add(prevTarget);

        return new ArrayList<>(candidates);
    }
}
