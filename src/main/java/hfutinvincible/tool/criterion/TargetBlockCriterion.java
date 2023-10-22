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
import hfutinvincible.tool.human.TargetBlockTool;
import hfutinvincible.tool.path.SearchPathTool;
import org.jblas.DoubleMatrix;
import rescuecore2.standard.entities.*;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;

@HfutTool
@AgentType({AmbulanceTeam.class, FireBrigade.class, PoliceForce.class})
public class TargetBlockCriterion implements LifeCycle, ICriterion<Area> {
    @Autowired
    private WorldInfo worldInfo;

    // Tools
    @Autowired
    private TargetBlockTool targetBlockTool;
    @Autowired
    private SearchPathTool searchPathTool;


    protected final Params params;

    public TargetBlockCriterion() {
        this.params = new Params(getClass().getSimpleName());
    }

    @Nonnull
    @Override
    public Map<CriterionEnum, DoubleMatrix> evaluate(@Nonnull List<Area> candidates) {

        final int numCandidates = candidates.size();

        final DoubleMatrix blockTargetPriority = DoubleMatrix.zeros(numCandidates);

        for (int i = 0; i < numCandidates; i ++) {
            Area c = candidates.get(i);
            double x = targetBlockTool.getBlockedAgentIdsBy(c.getID())
                    .stream()
                    .mapToDouble(humanId -> { // 根据智能体类型计算总优先级
                        Human human = (Human) worldInfo.getEntity(humanId);
                        if (human == null)
                            return 0.;
                        if (human instanceof Civilian)
                            return params.getDouble("cvBlock");
                        if (human instanceof AmbulanceTeam)
                            return params.getDouble("atBlock");
                        if (human instanceof FireBrigade)
                            return params.getDouble("fbBlock");
                        return 0.;
                    })
                    .sum();
            blockTargetPriority.put(i, x);
        }

        return Map.of(CriterionEnum.blockTargetPriority, blockTargetPriority);
    }
}