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
import hfutinvincible.tool.Params;
import hfutinvincible.tool.building.RefugeConditionTool;
import hfutinvincible.tool.misc.OptionsTool;
import org.jblas.DoubleMatrix;
import rescuecore2.standard.entities.*;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;

/**
 * 返回两个指标列：
 * <ol>
 *     <li>如果这个 area 是 refuge，则为这个 refuge 的空床位数。如果没有空床位，则为 - 排队数。如果不是 refuge，则为 0.
 *     <li>这个 area 是不是 refuge，是则为 1</li></li>
 * </ol>
 */
@HfutTool
@AgentType({AmbulanceTeam.class, FireBrigade.class, PoliceForce.class})
public class RefugeConditionCriterion implements LifeCycle, ICriterion<Area> {

    // Tools
    @Autowired
    private RefugeConditionTool refugeConditionTool;


    protected final Params params;

    public RefugeConditionCriterion() {
        this.params = new Params(getClass().getSimpleName());
    }

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

    private double fun(int freeBedsNum) {
        if (freeBedsNum < 0)
            return freeBedsNum;
        else if (freeBedsNum > params.getInt("enoughFreeBeds"))
            return 2;
        else return 1;
    }
}