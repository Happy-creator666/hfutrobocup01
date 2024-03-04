package hfutinvincible.tool.MyTest.DebugTest;

import adf.core.agent.info.AgentInfo;
import hfutinvincible.anno.AgentType;
import hfutinvincible.anno.Autowired;
import hfutinvincible.anno.Disabled;
import hfutinvincible.anno.HfutTool;
import hfutinvincible.debug.Debugger;
import hfutinvincible.debug.paintable.Paintable;
import hfutinvincible.tool.CalcContext;
import hfutinvincible.tool.LifeCycle;
import rescuecore2.standard.entities.AmbulanceTeam;
import rescuecore2.standard.entities.FireBrigade;
import rescuecore2.standard.entities.PoliceForce;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

@HfutTool
@Disabled
@AgentType({PoliceForce.class, FireBrigade.class, AmbulanceTeam.class})
public class ShowLocationsInHistoryTool implements LifeCycle {

    //显示智能体的历史轨迹 (把过去若干周期智能体的位置用线连起来即可)

    @Autowired
    private AgentInfo agentInfo;

    Map<Integer,double[]> locationMap = new HashMap<>();//用于记录每周期agent的位置

    public ShowLocationsInHistoryTool(){

    }

    @Override
    public void afterCalc(CalcContext context) {
        int time = agentInfo.getTime();
        locationMap.put(time, new double[]{agentInfo.getX(), agentInfo.getY()});//加入当前周期agent的位置
        if(time<2){
            return;
        }

        double preX = locationMap.get(time-1)[0];
        double preY = locationMap.get(time-1)[1];

        //智能体的历史位置用橙色线段连接
        Debugger.getPainterOfAgent(agentInfo)
                .addPaintables("locations", false,
                        Paintable.line(preX, preY, agentInfo.getX(), agentInfo.getY(), Color.ORANGE));
    }
}
