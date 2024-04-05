package hfutinvincible.tool.criterion;

import hfutinvincible.anno.AgentType;
import hfutinvincible.anno.Autowired;
import hfutinvincible.anno.HfutTool;
import hfutinvincible.tool.LifeCycle;
import hfutinvincible.tool.Params;
import hfutinvincible.tool.building.RefugeConditionTool;
import hfutinvincible.tool.misc.OptionsTool;
import org.jblas.DoubleMatrix;
import rescuecore2.standard.entities.*;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;

@HfutTool
@AgentType({FireBrigade.class, PoliceForce.class, AmbulanceTeam.class})
public class RefugeConditionCriterion implements LifeCycle,ICriterion<Area> {
    @Autowired
    RefugeConditionTool refugeConditionTool;
    private Params params = new Params(getClass().getSimpleName());


    @Nonnull
    @Override
    public Map<CriterionEnum, DoubleMatrix> evaluate(@Nonnull List<Area> candidates) {
        int candidateNum = candidates.size();

        final DoubleMatrix refugeCondition = DoubleMatrix.zeros(candidateNum);

        for (int i = 0; i < candidates.size(); i ++) {
            Area c = candidates.get(i);
            if (c instanceof Refuge refuge) {
                Boolean isFull = this.refugeConditionTool.isFull(refuge.getID());
                int freeBedsNum = isFull == null // 如果不知道这个 refuge 的情况，则估计
                        ? (int) (refuge.getBedCapacity() * OptionsTool.UNKNOWN_REFUGE_FREE_BEDS_ESTIMATE_FACTOR)
                        : isFull
                        ? - refuge.getWaitingListSize() // 已经满了，空床位数为负
                        : refuge.getBedCapacity() - refuge.getOccupiedBeds(); // 还未满
                refugeCondition.put(i, fun(freeBedsNum));
            }
        }

        return Map.of(CriterionEnum.freeBedsCondition,  refugeCondition);
    }
    private double fun (int freeBedsNum) {
        if (freeBedsNum < 0)
            return freeBedsNum;
        else if (freeBedsNum > params.getInt("enoughFreeBeds"))
            return 2;
        else return 1;
    }
}
