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
import org.apache.log4j.Logger;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;
import hfutinvincible.tool.human.StuckInfoTool;
import java.awt.*;

@HfutTool
@Disabled
@AgentType({PoliceForce.class, FireBrigade.class, AmbulanceTeam.class})
public class StuckInfoToolDebugger implements LifeCycle {

    @Autowired
    private AgentInfo agentInfo;
    @Autowired
    private WorldInfo worldInfo;
    @Autowired
    private Logger logger;
    @Autowired
    private StuckInfoTool stuckInfoTool;
    @Override
    public void afterCalc(CalcContext context){
        EntityID agentID=agentInfo.getID();
        if(stuckInfoTool.isStuck(agentID)){
            Debugger.getPainterOfAgent(agentInfo)
                    .addPaintables("Stuck", false,
                            Paintable.text(agentInfo.getX() - 100, agentInfo.getY() + 100, String.valueOf("you are stuck"), Color.yellow));
        }
    }
}