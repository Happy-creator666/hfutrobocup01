package hfutinvincible.tool.debugtest;

import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.WorldInfo;
import hfutinvincible.anno.AgentType;
import hfutinvincible.anno.Autowired;
import hfutinvincible.anno.Disabled;
import hfutinvincible.anno.HfutTool;
import hfutinvincible.tool.CalcContext;
import hfutinvincible.tool.LifeCycle;
import hfutinvincible.tool.criterion.AreaEdgePairPassableCriterion;
import org.apache.log4j.Logger;
import rescuecore2.standard.entities.AmbulanceTeam;
import rescuecore2.standard.entities.Area;
import rescuecore2.standard.entities.FireBrigade;
import rescuecore2.standard.entities.PoliceForce;
import rescuecore2.worldmodel.EntityID;

import java.util.ArrayList;
import java.util.List;

@HfutTool
@Disabled
@AgentType({PoliceForce.class, AmbulanceTeam.class, FireBrigade.class})
public class CriterionTest implements LifeCycle {
    @Autowired
    AreaEdgePairPassableCriterion areaEdgePairPassableCriterion;
    @Autowired
    Logger logger;
    @Autowired
    AgentInfo agentInfo;
    @Autowired
    WorldInfo worldInfo;

    @Override
    public void calc(CalcContext context) {
        List<Area> candidates = new ArrayList<>();
        List<EntityID> areaId = agentInfo.getPositionArea().getNeighbours();
        for (EntityID entityID : areaId) {
            Area area = (Area) worldInfo.getEntity(entityID);
            candidates.add(area);
        }
        logger.info("Test"+ areaEdgePairPassableCriterion.evaluate(candidates));
    }
}
