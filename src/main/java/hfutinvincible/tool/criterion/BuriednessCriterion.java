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
import hfutinvincible.tool.human.BuriednessTool;
import hfutinvincible.tool.human.HumanPositionTool;
import org.jblas.DoubleMatrix;
import rescuecore2.standard.entities.AmbulanceTeam;
import rescuecore2.standard.entities.Area;
import rescuecore2.standard.entities.FireBrigade;
import rescuecore2.standard.entities.PoliceForce;
import rescuecore2.worldmodel.EntityID;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;

@HfutTool
@AgentType({AmbulanceTeam.class, PoliceForce.class, FireBrigade.class})
public class BuriednessCriterion implements LifeCycle, ICriterion<Area> {

    // Tools
    @Autowired
    private BuriednessTool buriednessTool;
    @Autowired
    private HumanPositionTool humanPositionTool;

    public BuriednessCriterion() {

    }

    @Nonnull
    @Override
    public Map<CriterionEnum, DoubleMatrix> evaluate(@Nonnull List<Area> candidates) {

        final int candidateNum = candidates.size();

        final DoubleMatrix minBuriedness = DoubleMatrix.zeros(candidateNum);
        final DoubleMatrix hasBuriedHuman = DoubleMatrix.zeros(candidateNum);

        for (int i = 0; i < candidateNum; i ++) {
            Area c = candidates.get(i);
            double min = Double.MAX_VALUE;
            for (EntityID humanId : humanPositionTool.getHumanIdsOnArea(c.getID())) {
                Integer buriedness = buriednessTool.getBuriedness(humanId);
                if (buriedness == null)
                    continue;
                if (buriedness < min)
                    min = buriedness;
            }
            minBuriedness.put(i, min);
            hasBuriedHuman.put(i, min == Double.MAX_VALUE ? 0.0 : 1.0);

        }

        return Map.of(
                CriterionEnum.hasBuriedHuman, hasBuriedHuman,
                CriterionEnum.minBuriedness, minBuriedness);
    }
}