package hfutinvincible.tool.test;

import adf.core.agent.action.common.ActionMove;
import adf.core.agent.action.common.ActionRest;
import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.WorldInfo;
import hfutinvincible.anno.After;
import hfutinvincible.anno.AgentType;
import hfutinvincible.anno.Autowired;
import hfutinvincible.anno.HfutTool;
import hfutinvincible.tool.CalcContext;
import hfutinvincible.tool.LifeCycle;
import org.apache.log4j.Logger;
import rescuecore2.standard.entities.FireBrigade;
import rescuecore2.standard.entities.Human;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.worldmodel.EntityID;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@HfutTool
@AgentType(FireBrigade.class)
public class RescueBuriedTool implements LifeCycle {

    @Autowired
    AgentInfo agentInfo;
    @Autowired
    WorldInfo worldInfo;
    @Autowired
    Logger logger;

    public RescueBuriedTool(){

    }

    @Override
    @After("WalkNorthTool")
    public void calc(CalcContext calcContext){
        StringBuilder sb = new StringBuilder();

        List<EntityID> list = new ArrayList<>();

        Collection<StandardEntity> allEntities = worldInfo.getAllEntities();
        allEntities.stream().filter(Human.class::isInstance)
                .map(Human.class::cast)
                .forEach(human->{
                    if(human.isBuriednessDefined()){
                        list.add(human.getID());
                    }
                });
        int numBuriedHuman = list.size();
        if(numBuriedHuman > 0){
            int randomInt = ThreadLocalRandom.current().nextInt(numBuriedHuman);
            EntityID randomTarget = list.get(randomInt);
            Human target = (Human) worldInfo.getEntity(randomTarget);

            assert target != null;
            sb.append("救援目标：").append(randomTarget).append(";X:").append(target.getX())
                    .append(", Y:").append(target.getY());
            logger.info(sb.toString());

            calcContext.resultAction = new ActionMove(List.of(agentInfo.getPosition(),randomTarget));
        }else{
            sb.append("休息");
            logger.info(sb.toString());
            calcContext.resultAction = new ActionRest();
        }
    }
}
