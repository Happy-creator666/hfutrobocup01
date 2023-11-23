package hfutinvincible.tool;

import adf.core.agent.develop.DevelopData;
import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.ScenarioInfo;
import adf.core.agent.info.WorldInfo;
import adf.core.agent.module.ModuleManager;
import hfutinvincible.anno.AgentType;
import hfutinvincible.anno.Autowired;
import hfutinvincible.anno.HfutTool;
import hfutinvincible.tool.misc.MeasureLengthTool;
import org.apache.log4j.Logger;
import rescuecore2.standard.entities.AmbulanceTeam;
import rescuecore2.standard.entities.FireBrigade;
import rescuecore2.standard.entities.PoliceForce;

@HfutTool
@AgentType({AmbulanceTeam.class, FireBrigade.class, PoliceForce.class})
public class MeasureToolTest implements LifeCycle {
    @Autowired
    AgentInfo agentInfo;
    @Autowired
    WorldInfo worldInfo;
    @Autowired
    ScenarioInfo scenarioInfo;
    @Autowired
    DevelopData developData;
    @Autowired
    ModuleManager moduleManager;
    @Autowired
    Logger logger;
    @Autowired
    MeasureLengthTool measureLengthTool;

    @Override
    public void calc(CalcContext context) {
        double manhattan=measureLengthTool.getManhattanLength(agentInfo.getPositionArea(),agentInfo.getPositionArea());
        logger.info("manhattan is "+manhattan);
    }
}
