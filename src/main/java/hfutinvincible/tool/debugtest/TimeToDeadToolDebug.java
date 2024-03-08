package hfutinvincible.tool.debugtest;

/*import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.ScenarioInfo;
import adf.core.agent.info.WorldInfo;
import hfutinvincible.anno.AgentType;
import hfutinvincible.anno.Autowired;
import hfutinvincible.anno.Disabled;
import hfutinvincible.anno.HfutTool;
import hfutinvincible.tool.CalcContext;
import hfutinvincible.tool.LifeCycle;
import hfutinvincible.tool.comm.MessageManageTool;
import hfutinvincible.tool.human.TimeToDeadTool;
import hfutinvincible.tool.path.SearchPathTool;
import org.apache.log4j.Logger;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;

@HfutTool
@Disabled
@AgentType({AmbulanceTeam.class, PoliceForce.class, FireBrigade.class})
public class TimeToDeadToolDebug implements LifeCycle {
    @Autowired
    AgentInfo agentInfo;
    @Autowired
    WorldInfo worldInfo;
    @Autowired
    ScenarioInfo scenarioInfo;
    @Autowired
    SearchPathTool searchPathTool;
    @Autowired
    MessageManageTool messageTool;
    @Autowired
    Logger logger;

    @Autowired
    TimeToDeadTool timeToDeadTool;

    @Override
    public void afterCalc(CalcContext context) {
        logger.info("TimeToDeadTool Debug");
        worldInfo.getAllEntities()
                .stream()
                .filter(e -> e instanceof Human)
                .forEach(e -> {
                    Integer LastTime=timeToDeadTool.getLatestTimeToDead(e.getID());
                    logger.info("Test Result is"+LastTime);
                });
    }
}
*/