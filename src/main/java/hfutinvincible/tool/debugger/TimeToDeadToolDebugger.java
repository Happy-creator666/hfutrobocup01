package hfutinvincible.tool.debugger;

import adf.core.agent.communication.MessageManager;
import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.WorldInfo;
import hfutinvincible.anno.AgentType;
import hfutinvincible.anno.Autowired;
import hfutinvincible.anno.Disabled;
import hfutinvincible.anno.HfutTool;
import hfutinvincible.debug.Debugger;
import hfutinvincible.debug.paintable.Paintable;
import hfutinvincible.tool.CalcContext;
import hfutinvincible.tool.LifeCycle;
//import hfutinvincible.tool.human.TimeToDeadTool;
import hfutinvincible.tool.human.TimeToDeadTool;
import hfutinvincible.tool.misc.OptionsTool;
import org.apache.log4j.Logger;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;

import java.awt.*;

@HfutTool
//@Disabled
@AgentType({PoliceForce.class, FireBrigade.class, AmbulanceTeam.class})
public class TimeToDeadToolDebugger implements LifeCycle {

    @Autowired
    private AgentInfo agentInfo;
    @Autowired
    private WorldInfo worldInfo;
    @Autowired
    private Logger logger;

    @Autowired
    private TimeToDeadTool timeToDeadTool;

    @Override
    public void afterCalc(CalcContext context) {
        EntityID agentID = agentInfo.getID();

//        Human human = (Human) worldInfo.getEntity(agentID);
//        assert human != null;
//        int hp = human.isHPDefined() ? human.getHP() : 0;
//        int damage = human.isDamageDefined() ? human.getDamage() : 0;
//        int timeToDead = hp == 0
//                ? 0
//                : (damage == 0
//                ? OptionsTool.KERNEL_TIMESTEPS
//                : hp / damage + ((hp % damage) != 0 ? 1 : 0));
//        logger.info("timeToDead：" + timeToDead);

        Integer timeToDead = timeToDeadTool.getTimeToDeadNowEstimate(agentID);
        logger.info("timeToDead：" + timeToDead);

        Debugger.getPainterOfAgent(agentInfo)
                .addPaintables("TimeToDead", false,
                        Paintable.text(agentInfo.getX() - 100, agentInfo.getY() + 100, String.valueOf(timeToDead), Color.GREEN));
    }
}
