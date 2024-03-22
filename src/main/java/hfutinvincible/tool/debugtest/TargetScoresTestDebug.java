package hfutinvincible.tool.debugtest;

import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.WorldInfo;
import hfutinvincible.Tuple2;
import hfutinvincible.anno.AgentType;
import hfutinvincible.anno.Autowired;
import hfutinvincible.anno.HfutTool;
import hfutinvincible.debug.Debugger;
import hfutinvincible.debug.Painter;
import hfutinvincible.debug.paintable.Paintable;
import hfutinvincible.debug.parametermodification.TargetWithItsScores;
import hfutinvincible.tool.CalcContext;
import hfutinvincible.tool.LifeCycle;
import org.apache.log4j.Logger;
import rescuecore2.standard.entities.*;

import java.awt.*;
import java.util.List;
@HfutTool
@AgentType({AmbulanceTeam.class, PoliceForce.class, FireBrigade.class})
public class TargetScoresTestDebug implements LifeCycle {
    @Autowired
    AgentInfo agentInfo;
    @Autowired
    WorldInfo worldInfo;
    @Autowired
    Logger logger;
    @Autowired
    TargetWithItsScores targetWithItsScores;

    @Override
    public void afterCalc(CalcContext context) {
        List<Human> humans = worldInfo.getAllEntities()
                .stream()
                .filter(it -> it instanceof Human)
                .map(it -> (Human) it)
                .toList();
        for (int i = 0; i <humans.size() ; i++) {
            Human human = humans.get(i);
            if(human instanceof FireBrigade){
                List<Tuple2<Area,Double>> fbtargets = targetWithItsScores.getFbSortedTargets();
                Painter<?> fbcandidatespainter = Debugger.getPainterOfAgent(agentInfo);
                Painter<?> fbrankpainter = Debugger.getPainterOfAgent(agentInfo);
                for (int j = 0; j < fbtargets.size() ; j++) {
                    Area area = fbtargets.get(j).e0;
                    if(j<3){
                        fbcandidatespainter.addPaintables("Fbcandidate",false,
                                Paintable.shape(area.getShape(), Color.RED,true));
                        fbrankpainter.addPaintables("Fbranks",false,
                                Paintable.text(area.getX(),area.getY() +100,String.valueOf(j),Color.WHITE));
                    }
                    else {
                        fbcandidatespainter.addPaintables("Fbcandidate",false,
                                Paintable.shape(area.getShape(), Color.ORANGE,true));
                        fbrankpainter.addPaintables("Fbranks",false,
                                Paintable.text(area.getX(),area.getY() +100,String.valueOf(j),Color.gray));
                    }
                }
            }
            else if (human instanceof PoliceForce){
                List<Tuple2<Area,Double>> pftargets = targetWithItsScores.getPfSortedTargets();
                Painter<?> pfcandidatespainter = Debugger.getPainterOfAgent(agentInfo);
                Painter<?> pfrankpainter = Debugger.getPainterOfAgent(agentInfo);
                for (int j = 0; j < pftargets.size() ; j++) {
                    Area area = pftargets.get(j).e0;
                    if(j<3){
                        pfcandidatespainter.addPaintables("Fbcandidate",false,
                                Paintable.shape(area.getShape(), Color.RED,true));
                        pfrankpainter.addPaintables("Fbranks",false,
                                Paintable.text(area.getX(),area.getY() +100,String.valueOf(j),Color.WHITE));
                    }
                    else {
                        pfcandidatespainter.addPaintables("Fbcandidate",false,
                                Paintable.shape(area.getShape(), Color.ORANGE,true));
                        pfrankpainter.addPaintables("Fbranks",false,
                                Paintable.text(area.getX(),area.getY() +100,String.valueOf(j),Color.gray));
                    }
                }
            }
            else if (human instanceof AmbulanceTeam){
                List<Tuple2<Area,Double>> attargets = targetWithItsScores.getAtSortedTargets();
                Painter<?> atcandidatespainter = Debugger.getPainterOfAgent(agentInfo);
                Painter<?> atrankpainter = Debugger.getPainterOfAgent(agentInfo);
                for (int j = 0; j < attargets.size() ; j++) {
                    Area area = attargets.get(j).e0;
                    if(j<3){
                        atcandidatespainter.addPaintables("Fbcandidate",false,
                                Paintable.shape(area.getShape(), Color.RED,true));
                        atrankpainter.addPaintables("Fbranks",false,
                                Paintable.text(area.getX(),area.getY() +100,String.valueOf(j),Color.WHITE));
                    }
                    else {
                        atcandidatespainter.addPaintables("Fbcandidate",false,
                                Paintable.shape(area.getShape(), Color.ORANGE,true));
                        atrankpainter.addPaintables("Fbranks",false,
                                Paintable.text(area.getX(),area.getY() +100,String.valueOf(j),Color.gray));
                    }
                }
            }
        }
    }
}
