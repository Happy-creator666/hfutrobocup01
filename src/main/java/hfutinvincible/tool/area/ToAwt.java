package hfutinvincible.tool.area;

import org.jblas.DoubleMatrix;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.geom.Point;

import java.awt.*;
import java.awt.Polygon;
import java.awt.geom.Line2D;
import java.util.ArrayList;
import java.util.List;

public class ToAwt {

    @SuppressWarnings("unchecked")
    public static List<Shape> from(Object geometry) {
        if (geometry instanceof org.locationtech.jts.geom.Polygon)
            return List.of(fromJtsPolygon((org.locationtech.jts.geom.Polygon) geometry));
        if (geometry instanceof MultiPolygon)
            return fromJtsMultiPolygon((MultiPolygon) geometry);
        if (geometry instanceof LineString)
            return List.of(fromJtsLineString((LineString) geometry));
        if (geometry instanceof MultiLineString)
            return fromJtsMultiLineString((MultiLineString) geometry);
        if (geometry instanceof GeometryCollection)
            return fromJtsGeometryCollection((GeometryCollection) geometry);
        return null;
    }

    private static List<Shape> fromJtsMultiPolygon(MultiPolygon multiPolygon) {
        int numPolygons = multiPolygon.getNumGeometries();
        List<Shape> result = new ArrayList<>(numPolygons);
        for (int i = 0; i < numPolygons; i ++)
            result.add(fromJtsPolygon((org.locationtech.jts.geom.Polygon) multiPolygon.getGeometryN(i)));
        return result;
    }

    private static Shape fromJtsPolygon(org.locationtech.jts.geom.Polygon polygon) {
        Coordinate[] cs = polygon.getCoordinates();
        int numPoints = polygon.getNumPoints();
        int[] xpoints = new int[numPoints];
        int[] ypoints = new int[numPoints];
        for (int i = 0; i < numPoints; i++) {
            Coordinate c = cs[i];
            xpoints[i] = (int) c.getX();
            ypoints[i] = (int) c.getY();
        }
        return new Polygon(xpoints, ypoints, numPoints);
    }

    private static Line2D fromJtsLineString(LineString l) {
        org.locationtech.jts.geom.Point startPoint = l.getStartPoint();
        Point endPoint = l.getEndPoint();
        return new Line2D.Double(startPoint.getX(), startPoint.getY(), endPoint.getX(), endPoint.getY());
    }

    private static List<Shape> fromJtsMultiLineString(MultiLineString ls) {
        int numLines = ls.getNumGeometries();
        List<Shape> result = new ArrayList<>(numLines);
        for (int i = 0; i < numLines; i ++)
            result.add(fromJtsLineString((LineString) ls.getGeometryN(i)));
        return result;
    }

    private static List<Shape> fromJtsGeometryCollection(GeometryCollection gc) {
        int geometryNum = gc.getNumGeometries();
        List<Shape> result = new ArrayList<>(geometryNum);
        for (int i = 0; i < geometryNum; i ++) {
            List<Shape> ss = from(gc.getGeometryN(i));
            if (ss == null)
                System.out.println(gc.getGeometryN(i).getClass());
            else result.addAll(ss);
        }
//        for (int i = 0; i < geometryNum; i ++) {
//            Geometry g = gc.getGeometryN(i);
//            if (g instanceof org.locationtech.jts.geom.Polygon)
//                result.addAll(fromJtsGeometry(g));
//        }
        return result;
    }
}
