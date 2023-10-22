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
public class BuildingEntranceProximityCriterion implements LifeCycle, ICriterion<Area> {

    // Tools
    @Autowired
    private BuildingEntranceTool buildingEntranceTool;

    public BuildingEntranceProximityCriterion() {

    }

    @Nonnull
    @Override
    public Map<CriterionEnum, DoubleMatrix> evaluate(@Nonnull List<Area> candidates) {

        final DoubleMatrix buildingEntranceProximity = DoubleMatrix.zeros(candidates.size());

        for (int i = 0; i < candidates.size(); i ++) {
            Area area = candidates.get(i);
            Double result = buildingEntranceTool.getBuildingEntranceProximityOf(area.getID());
            buildingEntranceProximity.put(i, result == null ? 0.0 : result);
        }

        return Map.of(CriterionEnum.buildingEntranceProximity, buildingEntranceProximity);
    }
}