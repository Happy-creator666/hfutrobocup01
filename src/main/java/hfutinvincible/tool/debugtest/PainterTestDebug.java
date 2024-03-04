package hfutinvincible.tool.debugtest;

import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.WorldInfo;
import hfutinvincible.anno.AgentType;
import hfutinvincible.anno.Autowired;
import hfutinvincible.anno.HfutTool;
import hfutinvincible.debug.Debugger;
import hfutinvincible.debug.Painter;
import hfutinvincible.debug.paintable.Paintable;
import hfutinvincible.tool.CalcContext;
import hfutinvincible.tool.LifeCycle;
import org.apache.log4j.Logger;
import rescuecore2.standard.entities.AmbulanceTeam;
import rescuecore2.standard.entities.FireBrigade;

import java.awt.*;
import java.util.Optional;

@HfutTool
@AgentType({AmbulanceTeam.class, FireBrigade.class})
public class PainterTestDebug implements LifeCycle {
    @Autowired
    AgentInfo agentInfo;
    @Autowired
    WorldInfo worldInfo;
    @Autowired
    Logger logger;


    @Override
    public void calc(CalcContext context) {
        logger.info("---------------------------This is PainterArrowHead DebugTool-------------------------");
        Painter<?> painter = Debugger.getPainterOfAgent(agentInfo);
//        List<FireBrigade> fbs=worldInfo.getAllEntities().stream()
//                .filter(human -> human instanceof FireBrigade)
//                .map(FireBrigade.class::cast)
//                .toList();
//        List<AmbulanceTeam> ats =worldInfo.getAllEntities()
//                .stream()
//                .filter(human -> human instanceof AmbulanceTeam)
//                .map(AmbulanceTeam.class::cast)
//                .toList();
//        int num1 = fbs.size();
//        int num2 = ats.size();
//        for (FireBrigade fb : fbs) {
//            for (AmbulanceTeam at : ats) {
//                painter.addPaintables("PaintArrowHeadLayer", true,
//                        Paintable.arrowhead(fb.getX(), fb.getY(), at.getX(), at.getY(), 2000,  Color.PINK));
//            }
//        }
        Optional<FireBrigade> fb =worldInfo.getAllEntities()
                .stream()
                .filter(human -> human instanceof FireBrigade)
                .map(FireBrigade.class::cast)
                .findFirst();
        Optional<AmbulanceTeam> at =worldInfo.getAllEntities()
                .stream()
                .filter(human -> human instanceof AmbulanceTeam)
                .map(AmbulanceTeam.class::cast)
                .findFirst();
        if(fb.isPresent() && at.isPresent())
        {
            painter.addPaintables("PaintArrowHeadLayer",true,
                    Paintable.arrowhead(fb.get().getX(),fb.get().getY(),at.get().getX(),at.get().getY(),3000, Color.PINK));
        }

        logger.info("--------------------------Time"+agentInfo.getTime()+"is End----------------------------");
    }
}
