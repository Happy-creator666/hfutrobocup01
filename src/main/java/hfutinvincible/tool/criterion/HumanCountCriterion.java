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
import hfutinvincible.tool.human.HumanPositionTool;
import org.jblas.DoubleMatrix;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;
import java.util.Set;

@HfutTool
@AgentType({AmbulanceTeam.class, FireBrigade.class, PoliceForce.class})
public class HumanCountCriterion implements LifeCycle, ICriterion<Area> {
    @Autowired
    private WorldInfo worldInfo;

    // Tools
    @Autowired
    private HumanPositionTool humanPositionTool;

    public HumanCountCriterion() {

    }

    @Nonnull
    @Override
    public Map<CriterionEnum, DoubleMatrix> evaluate(@Nonnull List<Area> candidates) {

        int candidateNum = candidates.size();

        final DoubleMatrix cvNum = DoubleMatrix.zeros(candidateNum);
        final DoubleMatrix pfNum = DoubleMatrix.zeros(candidateNum);
        final DoubleMatrix atNum = DoubleMatrix.zeros(candidateNum);
        final DoubleMatrix fbNum = DoubleMatrix.zeros(candidateNum);

        for (int i = 0; i < candidateNum; i ++) {
            Area area = candidates.get(i);
            int ithCvNum = 0;
            int ithPfNum = 0;
            int ithAtNum = 0;
            int ithFbNum = 0;
            Set<EntityID> humanIdsOnArea = humanPositionTool.getHumanIdsOnArea(area.getID());
            for (EntityID humanId : humanIdsOnArea) {
                Human human = (Human) worldInfo.getEntity(humanId);
                if (human instanceof Civilian)              ithCvNum += 1;
                else if (human instanceof PoliceForce)      ithPfNum += 1;
                else if (human instanceof AmbulanceTeam)    ithAtNum += 1;
                else if (human instanceof FireBrigade)      ithFbNum += 2;
            }
            cvNum.put(i, ithCvNum);
            pfNum.put(i, ithPfNum);
            atNum.put(i, ithAtNum);
            fbNum.put(i, ithFbNum);
        }

        return Map.of(
                CriterionEnum.cvNum, cvNum,
                CriterionEnum.pfNum, pfNum,
                CriterionEnum.atNum, atNum,
                CriterionEnum.fbNum, fbNum
        );
    }
}