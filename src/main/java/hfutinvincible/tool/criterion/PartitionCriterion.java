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
import hfutinvincible.tool.partition.Partition;
import hfutinvincible.tool.partition.StaticPartitionTool;
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
public class PartitionCriterion implements LifeCycle, ICriterion<Area> {

    // tools
    @Autowired
    private StaticPartitionTool staticPartitionTool;

    public PartitionCriterion() {

    }


    @Nonnull
    @Override
    public Map<CriterionEnum, DoubleMatrix> evaluate(@Nonnull List<Area> candidates) {

        final int numCandidates = candidates.size();

        final DoubleMatrix isInMyCurrentPartition = DoubleMatrix.zeros(numCandidates);
        final DoubleMatrix isInMyAssignedPartition = DoubleMatrix.zeros(numCandidates);

        Partition<EntityID> meAssignedPartition = staticPartitionTool.getAssignedPartition();
        Partition<EntityID> meCurrentPartition = staticPartitionTool.getCurrentPartition();

        for (int i = 0; i < numCandidates; i ++) {
            Area c = candidates.get(i);
            if (meAssignedPartition.contains(c.getID()))
                isInMyAssignedPartition.put(i, 1.0);
            if (meCurrentPartition.contains(c.getID()))
                isInMyCurrentPartition.put(i, 1.0);
        }

        return Map.of(
                CriterionEnum.isInMyAssignedPartition, isInMyAssignedPartition,
                CriterionEnum.isInMyCurrentPartition, isInMyCurrentPartition
        );
    }
}