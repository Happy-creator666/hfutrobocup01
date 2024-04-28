package hfutinvincible.tool.debugtest;

import adf.core.agent.communication.MessageManager;
import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.ScenarioInfo;
import adf.core.agent.info.WorldInfo;
import hfutinvincible.anno.*;
import hfutinvincible.debug.Debugger;
import hfutinvincible.debug.Painter;
import hfutinvincible.debug.paintable.Paintable;
import hfutinvincible.message.AreaEdgePairPassableMessage;
import hfutinvincible.message.TargetBlockMessage;
import hfutinvincible.tool.CalcContext;
import hfutinvincible.tool.LifeCycle;
import hfutinvincible.tool.comm.MessageManageTool;
import hfutinvincible.tool.edge.AreaPassableTool;
import hfutinvincible.tool.path.Path;
import hfutinvincible.tool.path.SearchPathTool;
import org.apache.log4j.Logger;
import rescuecore2.standard.entities.*;

import java.awt.*;

@HfutTool
@Disabled
@AgentType({AmbulanceTeam.class, PoliceForce.class, FireBrigade.class})
public class SearchPathToolDebug implements LifeCycle {
    @Autowired
    AgentInfo agentInfo;
    @Autowired
    WorldInfo worldInfo;
    @Autowired
    ScenarioInfo scenarioInfo;
    @Autowired
    SearchPathTool searchPathTool;
    @Autowired
    MessageManageTool messageTool;
    @Autowired
    Logger logger;

    @Override
    @After({"MessageManageTool"})
    public void updateInfo(MessageManager messageManager) {
    }

    @Override
    public void afterCalc(CalcContext context) {
        logger.info("SearchPathTool" + "aftercalc");
        Path path = searchPathTool.setFromAndTo(agentInfo.getPositionArea(),
                (Area) worldInfo.getAllEntities()
                        .stream()
                        .filter(e -> e instanceof Refuge)
                        .toList().get(0))
                .calc()
                .getAreaPath();
        logger.info(path.getIDList());
        if(path !=null){
            Painter<?> painter =Debugger.getPainterOfAgent(agentInfo);
            for(Area area:path.getRoadList()){
                painter.addPaintables("SearchPathTool",false,
                        Paintable.shape(area.getShape(),Paintable.addAlpha(Color.GREEN,50),true));
            }
        }


    }
}
