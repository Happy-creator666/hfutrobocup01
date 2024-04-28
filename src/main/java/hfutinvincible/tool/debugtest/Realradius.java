package hfutinvincible.tool.debugtest;

import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.ScenarioInfo;
import adf.core.agent.info.WorldInfo;
import hfutinvincible.anno.AgentType;
import hfutinvincible.anno.Autowired;
import hfutinvincible.anno.Disabled;
import hfutinvincible.anno.HfutTool;
import hfutinvincible.debug.Debugger;
import hfutinvincible.debug.Painter;
import hfutinvincible.debug.paintable.Paintable;
import hfutinvincible.tool.CalcContext;
import hfutinvincible.tool.LifeCycle;
import org.apache.log4j.Logger;
import rescuecore2.standard.entities.AmbulanceTeam;
import rescuecore2.standard.entities.FireBrigade;
import rescuecore2.standard.entities.PoliceForce;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.worldmodel.EntityID;

import java.awt.*;

@HfutTool
@AgentType({FireBrigade.class, AmbulanceTeam.class, PoliceForce.class})
public class Realradius implements LifeCycle {
    @Autowired
    AgentInfo agentInfo;
    @Autowired
    WorldInfo worldInfo;
    @Autowired
    ScenarioInfo scenarioInfo;
    @Autowired
    Logger logger;

    @Override
    public void afterCalc(CalcContext context) {
        Painter<?> painter = Debugger.getPainterOfAgent(agentInfo);
        EntityID entityID = agentInfo.getID();
        StandardEntity standardEntity = worldInfo.getEntity(entityID);

        double x = agentInfo.getX();
        double y = agentInfo.getY();
        if(standardEntity instanceof PoliceForce){
            painter.addPaintables("Realradius", false,
                    Paintable.pie(x, y, 500, Paintable.addAlpha(Color.blue, 50)));
        } else if (standardEntity instanceof AmbulanceTeam) {
            painter.addPaintables("Realradius", false,
                    Paintable.pie(x, y, 500, Paintable.addAlpha(Color.white, 50)));
        } else if (standardEntity instanceof FireBrigade){
            painter.addPaintables("Realradius", false,
                    Paintable.pie(x, y, 500, Paintable.addAlpha(Color.red, 50)));
        }
    }

}
