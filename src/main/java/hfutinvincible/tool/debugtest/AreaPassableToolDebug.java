package hfutinvincible.tool.debugtest;

import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.ScenarioInfo;
import adf.core.agent.info.WorldInfo;
import hfutinvincible.anno.AgentType;
import hfutinvincible.anno.Autowired;
import hfutinvincible.anno.HfutTool;
import hfutinvincible.debug.Debugger;
import hfutinvincible.debug.paintable.Paintable;
import hfutinvincible.tool.edge.AreaPassableTool;
import hfutinvincible.tool.edge.HFUTEdge;
import hfutinvincible.tool.misc.HFUTGeometryTool;
import org.apache.log4j.Logger;
import rescuecore2.standard.entities.*;
import rescuecore2.misc.geometry.Point2D;
import hfutinvincible.tool.*;


import rescuecore2.standard.entities.Area;
import java.awt.*;
import java.util.Collection;
import java.util.List;
import java.util.Map;
@HfutTool
@AgentType({FireBrigade.class, AmbulanceTeam.class, PoliceForce.class})
public class AreaPassableToolDebug implements LifeCycle {
    @Autowired
    AgentInfo agentInfo;
    @Autowired
    WorldInfo worldInfo;
    @Autowired
    ScenarioInfo scenarioInfo;
    @Autowired
    Logger logger;
    @Autowired
    AreaPassableTool areaPassableTool;

    private final Color PASSABLE_COLOR = Color.green;
    private final Color IMPASSABLE_COLOR = Color.red;
    private final Color UNKNOWN_COLOR = Color.darkGray;
    private final Color CENTER_BLOCK_COLOR = Color.ORANGE;



    public AreaPassableToolDebug(Map<String, AreaPassableTool> tools) throws NullPointerException {
        this.areaPassableTool = (AreaPassableTool) tools.get("AreaPassableTool");
        if (areaPassableTool == null) throw new NullPointerException();
    }

    public AreaPassableToolDebug() {

    }

    public String getName() {
        return "Area Passable Layer";
    }

    @Override
    public void afterCalc(CalcContext context) {
        logger.info("AreaPassableDebugTool");
        for (StandardEntity standardEntity:worldInfo.getAllEntities()) {
            if(standardEntity instanceof Area area){
                Color colorset;
                //TODD 高亮我视线范围内的area

                //显示 area 各边对间的 passiblity

                List<HFUTEdge> edges = areaPassableTool.getHfutEdgesOf(area.getID());
                if (edges == null) {
                    System.err.println("Cannot get edges of" + area);
                    return;
                }
                for (int i = 0; i < edges.size(); i++) {
                    HFUTEdge ei = edges.get(i);
                    Point2D midEi = HFUTGeometryTool.getMidPoint(ei);
                        HFUTEdge ej = edges.get(i == edges.size() - 1 ? 0 : i + 1);
                        Point2D midEj = HFUTGeometryTool.getMidPoint(ej);
                        switch (areaPassableTool.getPassability(ei, ej)) {
                            case -1:
                                colorset = (UNKNOWN_COLOR);
                                break;
                            case 0:
                                colorset = (IMPASSABLE_COLOR);
                                break;
                            case 1:
                                colorset = (PASSABLE_COLOR);
                                break;
                            case 2:
                                colorset = (CENTER_BLOCK_COLOR);
                                break;
                            default:
                                throw new RuntimeException("Unknown passability.");
                        }

                        Debugger.getPainterOfAgent(agentInfo)
                                .addPaintables("AreaPassable", false,
                                        Paintable.line(midEi.getX(), midEi.getY(), midEj.getX(), midEj.getY(), colorset));
                        logger.info(midEi);
                }

            }
        }

//        Area area = agentInfo.getPositionArea();
    }
}
