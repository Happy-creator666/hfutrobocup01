package hfutinvincible.tool.criterion;

import adf.core.agent.develop.DevelopData;
import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.ScenarioInfo;
import adf.core.agent.info.WorldInfo;
import adf.core.agent.module.ModuleManager;
import hfutinvincible.anno.AgentType;
import hfutinvincible.anno.HfutTool;
import hfutinvincible.tool.CalcContext;
import hfutinvincible.tool.LifeCycle;
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
public class PrevScoreCriterion implements LifeCycle, ICriterion<Area> {

    private Area prevTarget;
    private Double prevScore;

    public PrevScoreCriterion() {

    }

    @Override
    public void afterCalc(CalcContext context) {
        this.prevTarget = context.getTarget();
        this.prevScore = context.getResultScore();
    }

    @Nonnull
    @Override
    public Map<CriterionEnum, DoubleMatrix> evaluate(@Nonnull List<Area> candidates) {

        final DoubleMatrix prevScore = DoubleMatrix.zeros(candidates.size());

        if (prevTarget != null) {
            int prevTargetIndex = candidates.indexOf(prevTarget);
            if (prevTargetIndex != -1)
                prevScore.put(prevTargetIndex, this.prevScore == null ? 0.0 : this.prevScore);
        }

        return Map.of(CriterionEnum.prevScore, prevScore);
    }
}