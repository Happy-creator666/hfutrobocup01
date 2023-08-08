package hfutinvincible.tool;

import adf.core.agent.info.AgentInfo;
import hfutinvincible.anno.AgentType;
import hfutinvincible.anno.Autowired;
import hfutinvincible.anno.Disabled;
import hfutinvincible.anno.HfutTool;
import hfutinvincible.debug.Debugger;
import hfutinvincible.debug.paintable.Paintable;
import rescuecore2.standard.entities.*;

import java.awt.*;

@HfutTool
@Disabled
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
            double midX = (fromX + toX) / 2.;
            double midY = (fromY + toY) / 2.;
            Debugger.getPainterOfAgent(ai)
                    .addPaintables("Edge inspect", false,
                            // area 加上红色半透明遮罩
                            Paintable.shape(area.getShape(), Paintable.addAlpha(Color.RED, 50), true),
                            // 以边 e 起点为圆心，画一个半径 1200. 的红色圆圈
                            Paintable.circle(fromX, fromY, 1200., Color.RED),
                            // 以边 e 终点为圆心，画一个半径 1600. 的蓝色圆圈
                            Paintable.circle(toX, toY, 1600., Color.BLUE),
                            // 边 e 中点处标出中点坐标
                            Paintable.text(midX, midY, "(" + midX + ", " + midY + ")", Color.BLUE));
            break;
        }
    }
}
