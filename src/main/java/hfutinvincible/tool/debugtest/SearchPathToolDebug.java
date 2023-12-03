package hfutinvincible.tool.debugtest;

import adf.core.agent.communication.MessageManager;
import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.ScenarioInfo;
import adf.core.agent.info.WorldInfo;
import hfutinvincible.anno.AgentType;
import hfutinvincible.anno.Autowired;
import hfutinvincible.anno.HfutTool;
import hfutinvincible.debug.Debugger;
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
import rescuecore2.standard.entities.AmbulanceTeam;
import rescuecore2.standard.entities.Area;
import rescuecore2.standard.entities.FireBrigade;
import rescuecore2.standard.entities.PoliceForce;

import java.awt.*;

@HfutTool
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
    public void updateInfo(MessageManager messageManager) {
        messageTool.updateInfo(messageManager);
        messageTool.getReceivedMessages(TargetBlockMessage.class)
                .forEach(message->{
                    Area fromArea=(Area) worldInfo.getEntity(message.getFromAreaId());
                    Area toArea =(Area) worldInfo.getEntity(message.getToAreaId());
                    if (fromArea ==null || toArea ==null)
                    {
                        logger.info("Error");
                        return;
                    }
                    Path areaPath=searchPathTool.setFromAndTo(fromArea,toArea)
                            .ignoreBlockades()
                            .calc()
                            .getAreaPath();
                    if (areaPath==null)
                    {
                        logger.info("areaPath"+areaPath);
                        return;
                    }
                    Debugger.getPainterOfAgent(agentInfo)
                            .addPaintables("SearchPath",false,
                                    Paintable.shape(areaPath.getStart().getShape(), Paintable.addAlpha(Color.RED,50),true),
                                    Paintable.shape(areaPath.getEnd().getShape(), Paintable.addAlpha(Color.BLUE,50),true));

                });



    }

    @Override
    public void afterCalc(CalcContext context) {


    }
}
