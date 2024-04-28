package hfutinvincible.tool.criterion;

import hfutinvincible.anno.AgentType;
import hfutinvincible.anno.HfutTool;
import hfutinvincible.tool.LifeCycle;
import org.jblas.DoubleMatrix;
import rescuecore2.standard.entities.*;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;

@HfutTool
@AgentType({AmbulanceTeam.class, PoliceForce.class, FireBrigade.class})
public class EntityTypeCriterion implements LifeCycle,ICriterion<Area> {

    @Nonnull
    @Override
    public Map<CriterionEnum, DoubleMatrix> evaluate(@Nonnull List<Area> candidates) {
        final int candidateNum = candidates.size();

        final DoubleMatrix isBuilding = DoubleMatrix.zeros(candidateNum);
        final DoubleMatrix isRefuge = DoubleMatrix.zeros(candidateNum);
        final DoubleMatrix isRoad = DoubleMatrix.zeros(candidateNum);

        for (int i = 0; i < candidateNum; i ++) {
            Area c = candidates.get(i);
            if (c instanceof Building)
                isBuilding.put(i, 1.0);
            if (c instanceof Refuge)
                isRefuge.put(i, 1.0);
            if (c instanceof Road)
                isRoad.put(i, 1.0);
        }

        return Map.of(
                CriterionEnum.isBuilding, isBuilding,
                CriterionEnum.isRefuge, isRefuge,
                CriterionEnum.isRoad, isRoad
        );
    }
}
