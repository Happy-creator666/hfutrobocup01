package hfutinvincible.tool.debugtest;

import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.WorldInfo;
import hfutinvincible.anno.AgentType;
import hfutinvincible.anno.Autowired;
import hfutinvincible.anno.HfutTool;
import hfutinvincible.tool.CalcContext;
import hfutinvincible.tool.LifeCycle;
import hfutinvincible.tool.criterion.BuildingEntranceProximityCriterion;
import org.apache.log4j.Logger;
import rescuecore2.standard.entities.AmbulanceTeam;
import rescuecore2.standard.entities.Area;
import rescuecore2.standard.entities.FireBrigade;
import rescuecore2.standard.entities.PoliceForce;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@HfutTool
@AgentType({AmbulanceTeam.class, PoliceForce.class, FireBrigade.class})
public class BuildingEntranceProximityCriterionDebug implements LifeCycle {
    @Autowired
    AgentInfo agentInfo;
    @Autowired
    WorldInfo worldInfo;
    @Autowired
    Logger logger;
    @Autowired
    BuildingEntranceProximityCriterion buildingEntranceProximityCriterion;

    private List<Area> candidate;
    public BuildingEntranceProximityCriterionDebug(){
        this.candidate = new ArrayList<>();
    }
    @Override
    public void calc(CalcContext context) {
        candidate = worldInfo.getAllEntities().stream()
                .filter(e -> e instanceof Area)
                .map(e -> (Area)e)
                .collect(Collectors.toList());
        Map<?,?> result=buildingEntranceProximityCriterion.evaluate(candidate);
        System.out.println("Now is the "+agentInfo.getTime()+"time BuildingEntrancePromimityCriterionDebug");
        logger.info("-----This is the BuildingEntrancePromimityCriterionDebug------");
        logger.info(result);
    }
}
