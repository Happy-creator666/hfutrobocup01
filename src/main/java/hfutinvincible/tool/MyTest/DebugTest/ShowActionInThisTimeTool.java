package hfutinvincible.tool.MyTest.DebugTest;

import adf.core.agent.action.Action;
import adf.core.agent.action.common.ActionRest;
import adf.core.agent.info.AgentInfo;
import hfutinvincible.anno.AgentType;
import hfutinvincible.anno.Autowired;
import hfutinvincible.anno.HfutTool;
import hfutinvincible.debug.Debugger;
import hfutinvincible.debug.paintable.Paintable;
import hfutinvincible.tool.CalcContext;
import hfutinvincible.tool.LifeCycle;
import rescuecore2.standard.entities.*;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

@HfutTool
//@Disabled
@AgentType({PoliceForce.class, FireBrigade.class, AmbulanceTeam.class})
public class ShowActionInThisTimeTool implements LifeCycle {

    //显示智能体本周期执行的动作 (比如移动，就将移动路径着色；休息，就标一个 rest 在智能体上方)

    @Autowired
    AgentInfo agentInfo;

    List<double[]> path = new ArrayList<>();

    public ShowActionInThisTimeTool(){

    }

    @Override
    public void afterCalc(CalcContext context) {
        Action action = agentInfo.getExecutedAction(-1);
        if(action instanceof ActionRest){
            //休息：显示rest，蓝色文字
            Debugger.getPainterOfAgent(agentInfo)
                    .addPaintables("Action In This Time", false,
                            Paintable.text(agentInfo.getX(), agentInfo.getY(), "rest", Color.BLUE));
        }else{
            int time = agentInfo.getTime();
            if(time<2){
                path.add(new double[]{agentInfo.getX(), agentInfo.getY()});
            }

            Area area = agentInfo.getPositionArea();
            double[] northEdge = {path.get(0)[0], path.get(0)[1]};
            //找出当前Area最北边的Edge的中点坐标存储在northEdge
            area.getEdges().forEach(
                edge -> {
                    double Y = (double) (edge.getStartY() + edge.getEndY()) /2;
                    double X = (double) (edge.getStartX() + edge.getEndX()) /2;
                    if(Y > northEdge[1]){
                        northEdge[0] = X;
                        northEdge[1] = Y;
                    }
                }
            );

            //画一条从一个Area最北边的Edge的中点到另一个Area的红色线段线段
            Debugger.getPainterOfAgent(agentInfo)
                    .addPaintables("Action In This Time", false,
                            Paintable.line(path.get(0)[0], path.get(0)[1], northEdge[0],northEdge[1],Color.RED));

            path.set(0,northEdge);
        }
    }
}
