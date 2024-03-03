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
import hfutinvincible.tool.UnavailableTargetTool;
import org.apache.log4j.Logger;
import org.jblas.DoubleMatrix;
import rescuecore2.standard.entities.AmbulanceTeam;
import rescuecore2.standard.entities.Area;
import rescuecore2.standard.entities.FireBrigade;
import rescuecore2.standard.entities.PoliceForce;
import rescuecore2.worldmodel.EntityID;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@HfutTool
@AgentType({AmbulanceTeam.class, FireBrigade.class, PoliceForce.class})
public class UnavailableCriterion implements LifeCycle,ICriterion<Area> {
    private final Map<EntityID, Integer> lastUnavailableTimeMap;

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
    UnavailableTargetTool unavailableTargetTool;

    public UnavailableCriterion(){
        this.lastUnavailableTimeMap = new HashMap<>();
    }
    @Nonnull
    @Override
    public Map<CriterionEnum, DoubleMatrix> evaluate(@Nonnull List<Area> candidates) {

        DoubleMatrix lastUnavailableTime = DoubleMatrix.zeros(candidates.size());
        lastUnavailableTime.fill(0.0);

        for (int i = 0; i < candidates.size(); i ++) {
            Area c = candidates.get(i);
            double x = unavailableTargetTool.isUnavailable(c.getID())
                    ? 1.0
                    : 0.0;
            lastUnavailableTime.put(i, x);
        }

        return Map.of(
                CriterionEnum.isUnavailable, lastUnavailableTime
        );
    }

}
