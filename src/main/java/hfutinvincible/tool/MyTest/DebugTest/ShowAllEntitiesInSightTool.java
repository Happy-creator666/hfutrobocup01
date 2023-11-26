package hfutinvincible.tool.MyTest.DebugTest;

import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.WorldInfo;
import hfutinvincible.anno.AgentType;
import hfutinvincible.anno.Autowired;
import hfutinvincible.anno.HfutTool;
import hfutinvincible.debug.Debugger;
import hfutinvincible.debug.paintable.Paintable;
import hfutinvincible.tool.CalcContext;
import hfutinvincible.tool.LifeCycle;
import rescuecore2.standard.entities.*;

import java.awt.*;

@HfutTool
//@Disabled
@AgentType({PoliceForce.class, FireBrigade.class, AmbulanceTeam.class})
public class ShowAllEntitiesInSightTool implements LifeCycle {

    //显示一个智能体视野范围内的所有 Entity (有 Area 或者其他智能体)

    @Autowired
    AgentInfo agentInfo;
    @Autowired
    WorldInfo worldInfo;

    public ShowAllEntitiesInSightTool(){

    }

    @Override
    public void calc(CalcContext context) {
        worldInfo.getChanged().getChangedEntities().stream()
                .map(worldInfo::getEntity)  //获取视野内所有的StandardEntity
                .forEach(entity->{
                    if(entity instanceof Area){
                        //视野内的Area用黄色标记
                        Debugger.getPainterOfAgent(agentInfo)
                                .addPaintables("Area In Sight", false,
                                        Paintable.shape(((Area) entity).getShape(), Color.YELLOW, true));
                    }else if(entity instanceof Human){
                        //视野内的Human用粉色pie呈现
                        Debugger.getPainterOfAgent(agentInfo)
                                .addPaintables("Human In Sight", false,
                                        Paintable.pie(((Human) entity).getX(), ((Human) entity).getY(), 900, Color.PINK));
                    }
                });
    }
}
