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
import hfutinvincible.tool.building.BuildingEntranceTool;
import org.apache.log4j.Logger;
import org.jblas.DoubleMatrix;
import rescuecore2.standard.entities.AmbulanceTeam;
import rescuecore2.standard.entities.Area;
import rescuecore2.standard.entities.FireBrigade;
import rescuecore2.standard.entities.PoliceForce;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;

@HfutTool
@AgentType({AmbulanceTeam.class, PoliceForce.class, FireBrigade.class})
public class RefugeEntranceProximityCriterion implements LifeCycle,ICriterion<Area> {
    @Autowired
    AgentInfo agentInfo;
    @Autowired
    WorldInfo worldInfo;
    @Autowired
    ScenarioInfo scenarioInfo;
    @Autowired
    ModuleManager moduleManager;
    @Autowired
    DevelopData developData;
    @Autowired
    Logger logger;

    //Tools
    @Autowired
    BuildingEntranceTool buildingEntranceTool;
    @Nonnull
    @Override
    public Map<CriterionEnum, DoubleMatrix> evaluate(@Nonnull List<Area> candidates) {

        int numCandidates = candidates.size();

        final DoubleMatrix isRefugeEntrance = DoubleMatrix.zeros(numCandidates);
        final DoubleMatrix refugeEntranceProximity = DoubleMatrix.zeros(numCandidates);

        for (int i = 0; i < numCandidates; i ++) {
            Area area = candidates.get(i);
            isRefugeEntrance.put(i, buildingEntranceTool.isRefugeEntrance(area.getID()) ? 1.0 : 0.0);
            double result = buildingEntranceTool.getRefugeEntranceProximityOf(area.getID());
            refugeEntranceProximity.put(i, result);
        }

        return Map.of(
                CriterionEnum.isRefugeEntrance,         isRefugeEntrance,
                CriterionEnum.refugeEntranceProximity,  refugeEntranceProximity);
    }

}
