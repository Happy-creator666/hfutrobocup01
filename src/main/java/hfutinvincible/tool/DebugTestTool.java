package hfutinvincible.tool;

import adf.core.agent.info.AgentInfo;
import hfutinvincible.anno.AgentType;
import hfutinvincible.anno.Autowired;
import hfutinvincible.anno.HfutTool;
import hfutinvincible.debug.Debugger;
import hfutinvincible.debug.paintable.Paintable;
import rescuecore2.standard.entities.*;

import java.awt.*;

@HfutTool
@AgentType({FireBrigade.class, AmbulanceTeam.class, PoliceForce.class})
public class DebugTestTool implements LifeCycle {

    @Autowired AgentInfo ai;

    @Override
    public void calc(CalcContext context) {
        Area area = ai.getPositionArea();
        for (Edge e : area.getEdges()) {
            int fromX = e.getStartX();
            int fromY = e.getStartY();
            int toX = e.getEndX();
            int toY = e.getEndY();
            Debugger.getPainterOfAgent(ai)
                    .addPaintables("Edge inspect", false,
                            Paintable.circle(fromX, fromY, 1200., Color.RED),
                            Paintable.circle(toX, toY, 1600., Color.BLUE));
            break;
        }
    }
}
