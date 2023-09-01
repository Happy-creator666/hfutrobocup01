package hfutinvincible.tool.area;

import org.locationtech.jts.geom.*;
import rescuecore2.standard.entities.Area;
import rescuecore2.standard.entities.Blockade;
import rescuecore2.standard.entities.Edge;

/**
 * 将 rescuecore2 中的 Area、Blockade 等类型转换为 JTS 中的 Polygon
 */
public class ToJts {
    public static Polygon fromApexList(int[] apexList) {
        Coordinate[] cs = new Coordinate[apexList.length / 2 + 1];
        int i = 0;
        for (; i < apexList.length / 2; i ++)
            cs[i] = new Coordinate(apexList[2 * i], apexList[2 * i + 1]);
        cs[i] = new Coordinate(apexList[0], apexList[1]);
        return new GeometryFactory(new PrecisionModel(100000000)).createPolygon(cs);
    }

    public static Polygon fromRcrsArea(Area area) {
        return fromApexList(area.getApexList());
    }

    public static Polygon fromRcrsBlockade(Blockade blockade) {
        return fromApexList(blockade.getApexes());
    }

    public static LineSegment fromRcrsEdge(Edge edge) {
        return new LineSegment(edge.getStartX(), edge.getStartY(), edge.getEndX(), edge.getEndY());
    }
}
