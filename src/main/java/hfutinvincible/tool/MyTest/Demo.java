package hfutinvincible.tool.MyTest;

import adf.core.agent.action.common.ActionMove;
import adf.core.agent.action.common.ActionRest;
import adf.core.agent.action.police.ActionClear;
import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.WorldInfo;
import hfutinvincible.anno.AgentType;
import hfutinvincible.anno.Autowired;
import hfutinvincible.anno.Disabled;
import hfutinvincible.anno.HfutTool;
import hfutinvincible.tool.CalcContext;
import hfutinvincible.tool.LifeCycle;
import org.apache.log4j.Logger;
import rescuecore2.standard.entities.Area;
import rescuecore2.standard.entities.Blockade;
import rescuecore2.standard.entities.PoliceForce;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.worldmodel.EntityID;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Disabled
@HfutTool
@AgentType(PoliceForce.class)
public class Demo implements LifeCycle {

    @Autowired
    AgentInfo agentInfo;
    @Autowired
    WorldInfo worldInfo;
    @Autowired
    Logger logger;

    public Demo(){

    }

    @Override
    public void calc(CalcContext calcContext){
        StringBuilder sb = new StringBuilder();

        Area myArea = agentInfo.getPositionArea();
        List<EntityID> neighbours = myArea.getNeighbours();

        List<EntityID> pathList = new ArrayList<>();
        pathList.add(agentInfo.getPosition());//加入当前所在Area
        EntityID targetID = null;
        int maxY = Integer.MIN_VALUE;

        //从相邻Area中选取Y值最大的Area，注意：这个Y值不一定比agent的Y值大。
        for(EntityID neighbourID : neighbours){
            StandardEntity neighbour = worldInfo.getEntity(neighbourID);
            if(neighbour instanceof Area){
                if(maxY<((Area) neighbour).getY()){
                    maxY = ((Area) neighbour).getY();
                    targetID = neighbour.getID();
                }
            }
        }

        pathList.add(targetID);//将targetID加入pathList
        StandardEntity target = worldInfo.getEntity(pathList.get(1));

        if(target instanceof Area){

            //若target的Y比agent的Y要小，则休息
            if(((Area) target).getY() < agentInfo.getY()){
                sb.append("没有更北方向的Area，休息");
                logger.info(sb.toString());
                calcContext.setResultAction(new ActionRest());
                return;
            }

            //否则，移动或清理障碍
            Collection<Blockade> nowBlockades = worldInfo.getBlockades(myArea);//当前Area的障碍
            Collection<Blockade> targetBlockades = worldInfo.getBlockades((Area) target);//目标Area的障碍

            //避免一直重复清理
            if(!(agentInfo.getExecutedAction(-1) instanceof ActionClear)){
                Blockade targetBlockade = null;
                double targetY = agentInfo.getY();
                //先清理当前Area的障碍，避免卡路
                for(Blockade blockade : nowBlockades){
                    //只选择清理比agent的Y大的blockade
                    if(blockade.getY() > targetY){
                        targetBlockade = blockade;
                        targetY = blockade.getY();
                    }
                }
                if(targetBlockade != null){
                    sb.append("清理当前Area内偏北的blockade");
                    logger.info(sb.toString());
                    calcContext.setResultAction(new ActionClear(targetBlockade.getX(), targetBlockade.getY()));
                    calcContext.setResultTarget(targetID);
                    return;
                }

                //再选择清理目标Area的障碍，使得可以到达target
                for(Blockade blockade : targetBlockades){
                    //只选择清理比agent的Y大的blockade
                    if(blockade.getY() > targetY){
                        targetBlockade = blockade;
                        targetY = blockade.getY();
                    }
                }
                if(targetBlockade != null){
                    sb.append("清理目标Area内偏北的blockade");
                    logger.info(sb.toString());
                    calcContext.setResultAction(new ActionClear(targetBlockade.getX(), targetBlockade.getY()));
                    calcContext.setResultTarget(targetID);
                    return;
                }
            }

            //前方暂无障碍，向着目标Area移动
            sb.append("畅通无阻，移动到北方向的Area：").append(pathList.get(1)).append(";X:").append(((Area) target).getX())
                    .append(", Y:").append(((Area) target).getY());
            logger.info(sb.toString());
            calcContext.setResultAction(new ActionMove(pathList));
            calcContext.setResultTarget(targetID);
        }
    }
}
