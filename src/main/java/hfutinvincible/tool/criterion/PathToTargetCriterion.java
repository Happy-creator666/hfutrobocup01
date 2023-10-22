package hfutinvincible.tool.criterion;

import adf.core.agent.develop.DevelopData;
import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.ScenarioInfo;
import adf.core.agent.info.WorldInfo;
import adf.core.agent.module.ModuleManager;
import hfutinvincible.anno.AgentType;
import hfutinvincible.anno.Autowired;
import hfutinvincible.anno.HfutTool;
import hfutinvincible.tool.LifeCycle;
import hfutinvincible.tool.edge.AreaPassableTool;
import hfutinvincible.tool.edge.HFUTEdge;
import hfutinvincible.tool.human.AgentTargetPathTool;
import hfutinvincible.tool.human.HumanPositionTool;
import hfutinvincible.tool.misc.OptionsTool;
import hfutinvincible.tool.path.InPartitionSearchPathTool;
import hfutinvincible.tool.path.PathUtilTool;
import hfutinvincible.tool.path.SearchPathTool;
import org.jblas.DoubleMatrix;
import rescuecore2.standard.entities.AmbulanceTeam;
import rescuecore2.standard.entities.Area;
import rescuecore2.standard.entities.FireBrigade;
import rescuecore2.standard.entities.PoliceForce;
import rescuecore2.worldmodel.EntityID;

import javax.annotation.Nonnull;
import java.util.*;

/**
 * 返回四个指标列：
 * <ol>
 *     <li>通行距离</li>
 *     <li>通行代价</li>
 *     <li>堵塞程度</li>
 *     <li>一路上经过的所有 area 的访问计数和</li>
 *     <li>一路上经过的所有 area 有比例为多少和其他警察的清理路径重合</li>
 * </ol>
 */
@HfutTool
@AgentType({AmbulanceTeam.class, FireBrigade.class, PoliceForce.class})
public class PathToTargetCriterion implements LifeCycle, ICriterion<Area> {
    @Autowired
    private AgentInfo agentInfo;

    // Tools
    @Autowired
    private InPartitionSearchPathTool inPartitionSearchPathTool;
    @Autowired
    private SearchPathTool searchPathTool;
    @Autowired
    private AreaPassableTool areaPassableTool;
    @Autowired
    private PathUtilTool pathUtilTool;
    @Autowired
    private AgentTargetPathTool agentTargetPathTool;
    @Autowired
    private HumanPositionTool humanPositionTool;


    public PathToTargetCriterion() {

    }

    @Nonnull
    public Map<CriterionEnum, DoubleMatrix> evaluate(@Nonnull final List<Area> candidates) {

        int candidateNum = candidates.size();

        final DoubleMatrix stepsByPassDistanceToTarget = DoubleMatrix.zeros(candidateNum);
        final DoubleMatrix stepsByPassCostToTarget = DoubleMatrix.zeros(candidateNum);
        final DoubleMatrix blockRatio = DoubleMatrix.zeros(candidateNum);
        final DoubleMatrix lastVisitTime = DoubleMatrix.zeros(candidateNum);
        final DoubleMatrix pfCleanPathOverlapRatio = DoubleMatrix.zeros(candidateNum);

        Area fromArea = humanPositionTool.getMyPositionArea();
        for (int i = 0; i < candidateNum; i ++) {
            Area toArea = candidates.get(i);
            final int ii = i;
            double fromX = agentInfo.getX();
            double fromY = agentInfo.getY();
            List<HFUTEdge> edges = areaPassableTool.getHfutEdgesOf(toArea.getID());
            if (edges == null || edges.isEmpty())
                continue;
            int time = agentInfo.getTime();
            Set<EntityID> areasOnOtherPfCleanPaths = agentTargetPathTool.getAreasOnOtherPfCleanPaths();
            edges.stream()
                    .map(inPartitionSearchPathTool::getEdgePathAndCost)
                    .filter(Objects::nonNull)
                    .min(Comparator.comparingDouble(t -> t.e1))
                    .ifPresentOrElse(
                            t -> {
                                LinkedList<HFUTEdge> edgePath = t.e0;
                                double passDistance = pathUtilTool.getEdgePathPassDistance(edgePath, fromX, fromY);
                                double passCost = t.e1;
                                double steps1 = (int) (passDistance / OptionsTool.MOVING_DISTANCE);
                                double steps2 = (int) (passCost / OptionsTool.MOVING_DISTANCE);
                                stepsByPassDistanceToTarget.put(ii, steps1); // 根据 passDistance 计算的步数
                                stepsByPassCostToTarget.put(ii, steps2); // 根据 passCost 计算的步数
                                blockRatio.put(ii, (steps2 + 1) / (steps1 + 1)); // 堵塞程度
                                // 计算这条路径每个 area （当前时间 - 上次访问时间）之和
                                LinkedList<Area> areaPath = pathUtilTool.edgePath2AreaPath(edgePath, fromArea, toArea);
                                double totalLastVisitTime = 0.0;
                                for (Area area : areaPath) {
                                    int x = humanPositionTool.getLastVisitTime(area.getID());
                                    totalLastVisitTime += time - x;
                                }
                                lastVisitTime.put(ii, totalLastVisitTime);
                                // 计算一路上经过的所有 area 有比例为多少和其他警察的清理路径重合
                                final int overlapCount = areaPath.stream()
                                        .map(Area::getID)
                                        .map(areasOnOtherPfCleanPaths::contains)
                                        .mapToInt(b -> b ? 1 : 0)
                                        .sum();
                                final double overlapRatio = areaPath.isEmpty()
                                        ? 0.0
                                        : overlapCount / (double) areaPath.size();
                                pfCleanPathOverlapRatio.put(ii, overlapRatio);
                            },
                            () -> { // 否则使用 searchPathTool 再试
                                var result = searchPathTool
                                        .setFromAndTo(humanPositionTool.getMyPositionArea(), toArea)
                                        .calc();
                                List<HFUTEdge> edgePath = result.getEdgePath();
                                if (edgePath == null) {
                                    stepsByPassDistanceToTarget.put(ii, Double.MAX_VALUE);
                                    stepsByPassCostToTarget.put(ii, Double.MAX_VALUE);
                                    blockRatio.put(ii, Double.MAX_VALUE);
                                    lastVisitTime.put(ii, Double.MAX_VALUE);
                                } else {
                                    double passDistance = pathUtilTool.getEdgePathPassDistance(edgePath, fromX, fromY);
                                    Double passCost = result.getPassCost();
                                    assert passCost != null;
                                    double steps1 = (int) (passDistance / OptionsTool.MOVING_DISTANCE);
                                    double steps2 = (int) (passCost / OptionsTool.MOVING_DISTANCE);
                                    stepsByPassDistanceToTarget.put(ii, steps1);
                                    stepsByPassCostToTarget.put(ii, steps2);
                                    blockRatio.put(ii, (steps2 + 1) / (steps1 + 1)); // 堵塞程度
                                    LinkedList<Area> areaPath = pathUtilTool.edgePath2AreaPath(edgePath, fromArea, toArea);
                                    double totalLastVisitTime = 0.0;
                                    for (Area area : areaPath) {
                                        int x = humanPositionTool.getLastVisitTime(area.getID());
                                        totalLastVisitTime += time - x;
                                    }
                                    lastVisitTime.put(ii, totalLastVisitTime);
                                    // 计算一路上经过的所有 area 有比例为多少和其他警察的清理路径重合
                                    final int overlapCount = areaPath.stream()
                                            .map(Area::getID)
                                            .map(areasOnOtherPfCleanPaths::contains)
                                            .mapToInt(b -> b ? 1 : 0)
                                            .sum();
                                    final double overlapRatio = areaPath.isEmpty()
                                            ? 0.0
                                            : overlapCount / (double) areaPath.size();
                                    pfCleanPathOverlapRatio.put(ii, overlapRatio);
                                }
                            }
                    );
        }

        return Map.of(
                CriterionEnum.stepsByPassDistanceToTarget,      stepsByPassDistanceToTarget,
                CriterionEnum.stepsByPassCostToTarget,          stepsByPassCostToTarget,
                CriterionEnum.blockRatio,                       blockRatio,
                CriterionEnum.totalLastVisitTime,               lastVisitTime,
                CriterionEnum.pfCleanPathOverlapRatio,          pfCleanPathOverlapRatio
        );
    }
}