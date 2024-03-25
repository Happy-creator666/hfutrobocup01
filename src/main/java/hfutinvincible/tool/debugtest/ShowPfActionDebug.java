package hfutinvincible.tool.debugtest;

import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.ScenarioInfo;
import adf.core.agent.info.WorldInfo;
import hfutinvincible.anno.*;
import hfutinvincible.debug.Debugger;
import hfutinvincible.debug.paintable.Paintable;
import org.apache.log4j.Logger;
import rescuecore2.standard.entities.*;
import hfutinvincible.tool.*;
import java.awt.*;

@HfutTool
@Disabled
@AgentType({ PoliceForce.class })
public class ShowPfActionDebug implements LifeCycle {

    @Autowired
    AgentInfo agentInfo;
    @Autowired
    WorldInfo worldInfo;
    @Autowired
    ScenarioInfo scenarioInfo;
    @Autowired
    Logger logger;

    public ShowPfActionDebug() {

    }

    @After("PfExecutor")
    public void afterCalc(CalcContext context) {
        logger.info("ShowPfActionDebug");
        String pfActionString = context.getResultAction().toString();
        Debugger.getPainterOfAgent(agentInfo)
                .addPaintables("PfActionText", false,
                        Paintable.text(agentInfo.getX(), agentInfo.getY(),
                                pfActionString.substring(0, pfActionString.indexOf('[')),
                                Color.green));
    }

}
