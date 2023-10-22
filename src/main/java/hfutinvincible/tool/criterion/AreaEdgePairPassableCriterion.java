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
import org.jblas.DoubleMatrix;
import rescuecore2.standard.entities.AmbulanceTeam;
import rescuecore2.standard.entities.Area;
import rescuecore2.standard.entities.FireBrigade;
import rescuecore2.standard.entities.PoliceForce;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;

@HfutTool
@AgentType({AmbulanceTeam.class, FireBrigade.class, PoliceForce.class})
public class AreaEdgePairPassableCriterion implements LifeCycle, ICriterion<Area> {

    // Tools
    @Autowired
    private AreaPassableTool areaPassableTool;

    public AreaEdgePairPassableCriterion() {

    }

    @Nonnull
    @Override
    public Map<CriterionEnum, DoubleMatrix> evaluate(@Nonnull List<Area> candidates) {

        int candidateNum = candidates.size();
        DoubleMatrix impassableRatio = DoubleMatrix.zeros(candidateNum);

        for (int i = 0; i < candidateNum; i ++) {
            Area area = candidates.get(i);
            impassableRatio.put(i, areaPassableTool.getImpassableRatio(area));
        }

        return Map.of(CriterionEnum.impassableRatio, impassableRatio);
    }
}