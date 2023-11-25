package hfutinvincible.tool.edge;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateXY;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import rescuecore2.misc.geometry.Line2D;
import rescuecore2.standard.entities.Edge;

import java.util.Objects;

public class HFUTEdge {

    private Edge edge;
    private int id;

    public HFUTEdge(Edge edge) {
        this.edge = edge;
        this.id = Objects.hash(edge.getStartX(), edge.getStartY())
                + Objects.hash(edge.getEndX(), edge.getEndY());
    }

    public Line2D getLine() {
        return edge.getLine();
    }

    public Edge getEdge() {
        return edge;
    }

    public int getId() {
        return id;
    }

    public int getStartX() { return edge.getStartX(); }

    public int getStartY() { return edge.getStartY(); }

    public int getEndX() { return edge.getEndX(); }

    public int getEndY() { return edge.getEndY(); }

    public LineString toLineString(GeometryFactory gf) {
        return gf.createLineString(new Coordinate[]{
                new CoordinateXY(getStartX(), getStartY()),
                new CoordinateXY(getEndX(), getEndY())
        });
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return id == ((HFUTEdge) o).getId();
    }

    @Override
    public int hashCode() {
        return id;
    }

    @Override
    public String toString() {
        return "HFUTEdge [id=" + id + ", from=(" + getStartX() + ", " + getStartY() + ") to=(" + getEndX() + ", " + getEndY() + ")]";
    }
}
