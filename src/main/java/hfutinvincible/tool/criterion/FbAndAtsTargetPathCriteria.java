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
import hfutinvincible.tool.human.AgentTargetPathTool;
import org.jblas.DoubleMatrix;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 对 PF 来说，已知的 FB 和 FB 要走的目标路径应该优先清理。而且路径起点的 area 比路径终点的 area 更重要。
 */
@HfutTool
@AgentType({AmbulanceTeam.class, FireBrigade.class, PoliceForce.class})
public class FbAndAtsTargetPathCriteria implements LifeCycle, ICriterion<Area> {
    @Autowired
    private WorldInfo worldInfo;

    // Tools
    @Autowired
    private AgentTargetPathTool agentTargetPathTool;

    public FbAndAtsTargetPathCriteria() {

    }

    @Nonnull
    @Override
    public Map<CriterionEnum, DoubleMatrix> evaluate(@Nonnull List<Area> candidates) {

        int numCandidates = candidates.size();

        Map<EntityID, Double> fbTargetPathPriorityMap = new HashMap<>();
        Map<EntityID, Double> atTargetPathPriorityMap = new HashMap<>();

        worldInfo.getEntityIDsOfType(StandardEntityURN.FIRE_BRIGADE)
                .stream()
                .map(agentTargetPathTool::getTargetPath)
                .filter(Objects::nonNull)
                .forEach(ids -> {
                    double i = 1.0;
                    for (EntityID id : ids) {
                        double oldValue = fbTargetPathPriorityMap.getOrDefault(id, 0.0);
                        fbTargetPathPriorityMap.put(id, Math.max(oldValue, i));
                        i /= 2.0;
                        if (i < 0.1)
                            break;
                    }
                });

        worldInfo.getEntityIDsOfType(StandardEntityURN.AMBULANCE_TEAM)
                .stream()
                .map(agentTargetPathTool::getTargetPath)
                .filter(Objects::nonNull)
                .forEach(ids -> {
                    double i = 1.0;
                    for (EntityID id : ids) {
                        double oldValue = atTargetPathPriorityMap.getOrDefault(id, 0.0);
                        atTargetPathPriorityMap.put(id, Math.max(oldValue, i));
                        i /= 2.0;
                        if (i < 0.1)
                            break;
                    }
                });

        DoubleMatrix fbTargetPathPriority = DoubleMatrix.zeros(numCandidates);
        DoubleMatrix atTargetPathPriority = DoubleMatrix.zeros(numCandidates);

        for (int i = 0; i < numCandidates; i ++) {
            Area area = candidates.get(i);
            fbTargetPathPriority.put(i, fbTargetPathPriorityMap.getOrDefault(area.getID(), 0.0));
            atTargetPathPriority.put(i, atTargetPathPriorityMap.getOrDefault(area.getID(), 0.0));
        }

        return Map.of(
                CriterionEnum.fbTargetPathPriority, fbTargetPathPriority,
                CriterionEnum.atTargetPathPriority, atTargetPathPriority);
    }
}