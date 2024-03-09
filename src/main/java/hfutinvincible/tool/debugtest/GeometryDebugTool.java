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
import hfutinvincible.tool.edge.AreaPassableTool;
import hfutinvincible.tool.human.HumanPositionTool;
import org.apache.log4j.Logger;
import org.checkerframework.checker.units.qual.A;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import rescuecore2.standard.entities.AmbulanceTeam;
import rescuecore2.standard.entities.Area;
import rescuecore2.standard.entities.Edge;
import rescuecore2.standard.entities.FireBrigade;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

@HfutTool
@AgentType({AmbulanceTeam.class, FireBrigade.class})
public class GeometryDebugTool implements LifeCycle {
    @Autowired
    AgentInfo agentInfo;
    @Autowired
    WorldInfo worldInfo;
    @Autowired
    Logger logger;
    @Autowired
    AreaPassableTool areaPassableTool;
    @Autowired
    HumanPositionTool humanPositionTool;

    @Override
    public void calc(CalcContext context) {
        Geometry cfreePolygon = areaPassableTool.getCfreePolygonOf(humanPositionTool.getMyPositionAreaId());
        Area area = humanPositionTool.getPositionArea(agentInfo.getID());
        Painter<?> painter1 = Debugger.getPainterOfAgent(agentInfo);
        assert area != null;
        List<Edge> edges=area.getEdges();
        for (int i = 0; i + 1<edges.size()  ; i++) {
            Edge edge1 = edges.get(i);
            Edge edge2 = edges.get(i);
            painter1.addPaintables("EdgeDebug",false,
                    Paintable.line(edge1.getStartX(),edge1.getStartY(), edge1.getEndX(),edge1.getEndY(),Color.BLUE),
                    Paintable.line(edge2.getStartX(),edge2.getStartY(), edge2.getEndX(),edge2.getEndY(),Color.BLUE));
        }
        if(cfreePolygon == null)
        {
            logger.error("No cfreePolygon to escape");
            return;
        }
        // 得出多边形的所有顶点以及边
        List<Coordinate> vertexes = new ArrayList<>();
        for (int i = 0; i < cfreePolygon.getNumPoints(); i++) {
            vertexes.add(cfreePolygon.getCoordinates()[i]);
        }
        Painter<?> painter = Debugger.getPainterOfAgent(agentInfo);
        for (int i = 0; i + 1<vertexes.size() ; i++) {
            painter.addPaintables("GeometryLayer",false,
                    Paintable.line(vertexes.get(i).getX(),vertexes.get(i).getY(),vertexes.get(i + 1).getX(),vertexes.get(i + 1).getY(), Color.RED));
        }
        logger.info("Debug Test is done");
    }
}
