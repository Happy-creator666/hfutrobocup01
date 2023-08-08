package hfutinvincible.tool.test;

import adf.core.agent.action.common.ActionMove;
import adf.core.agent.action.common.ActionRest;
import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.WorldInfo;
import hfutinvincible.anno.AgentType;
import hfutinvincible.anno.Autowired;
import hfutinvincible.anno.HfutTool;
import hfutinvincible.tool.CalcContext;
import hfutinvincible.tool.LifeCycle;
import org.apache.log4j.Logger;
import rescuecore2.standard.entities.Area;
import rescuecore2.standard.entities.PoliceForce;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.worldmodel.EntityID;

import java.util.ArrayList;
import java.util.List;

@HfutTool
@AgentType(PoliceForce.class)
public class WalkNorthTool implements LifeCycle {

    @Autowired
    AgentInfo agentInfo;
    @Autowired
    WorldInfo worldInfo;
    @Autowired
    Logger logger;

    public WalkNorthTool(){

    }

    @Override
    public void calc(CalcContext calcContext){
        StringBuilder sb = new StringBuilder();

        Area myArea = agentInfo.getPositionArea();
        List<EntityID> neighbours = myArea.getNeighbours();
        if(neighbours==null){
            calcContext.resultAction = new ActionRest();
            return;
        }

        List<EntityID> list = new ArrayList<>();
        list.add(agentInfo.getPosition());
        list.add(neighbours.get(0));
        int maxY = Integer.MIN_VALUE;

        for(EntityID neighbourID : neighbours){
            StandardEntity neighbour = worldInfo.getEntity(neighbourID);
            if(neighbour instanceof Area){
                if(maxY<((Area) neighbour).getY()){
                    maxY = ((Area) neighbour).getY();
                    list.set(1,neighbour.getID());
                }
            }
        }

        StandardEntity target = worldInfo.getEntity(list.get(1));
        if(target instanceof Area){
            sb.append("北方向的Area：").append(list.get(1)).append(";X:").append(((Area) target).getX())
                    .append(", Y:").append(((Area) target).getY());
            logger.info(sb.toString());
            calcContext.resultAction = new ActionMove(list);
        }else{
            sb.append("休息");
            logger.info(sb.toString());
            calcContext.resultAction = new ActionRest();
        }
    }
}
