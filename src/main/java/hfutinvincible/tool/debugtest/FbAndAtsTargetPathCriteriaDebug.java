package hfutinvincible.tool.debugtest;

import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.WorldInfo;
import hfutinvincible.anno.AgentType;
import hfutinvincible.anno.Autowired;
import hfutinvincible.anno.Disabled;
import hfutinvincible.anno.HfutTool;
import hfutinvincible.tool.CalcContext;
import hfutinvincible.tool.LifeCycle;
import hfutinvincible.tool.criterion.FbAndAtsTargetPathCriteria;
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
@Disabled
@AgentType({FireBrigade.class, AmbulanceTeam.class, PoliceForce.class})
public class FbAndAtsTargetPathCriteriaDebug implements LifeCycle {
    @Autowired
    AgentInfo agentInfo;
    @Autowired
    WorldInfo worldInfo;
    @Autowired
    Logger logger;
    @Autowired
    FbAndAtsTargetPathCriteria fbAndAtsTargetPathCriteria;
    private List<Area> candidate;
    public FbAndAtsTargetPathCriteriaDebug(){
        candidate= new ArrayList<>();
    }

    @Override
    public void calc(CalcContext context) {
        candidate=worldInfo.getAllEntities()
                .stream()
                .filter(e -> e instanceof Area)
                .map(e -> (Area)e)
                .collect(Collectors.toList());
        System.out.println("This is the "+agentInfo.getTime()+"time to exert FbAndAtsTargetPathCriteriaDebug");
        Map<?,?> result=fbAndAtsTargetPathCriteria.evaluate(candidate);
        logger.info("This is the FbAndAtsTargetPathCriteriaDebug");
        logger.info(result);
    }
}
