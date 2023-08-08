package hfutinvincible.debug.paintable;

import rescuecore2.misc.gui.ScreenTransform;

import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;

@FunctionalInterface
public interface Paintable {
    void paint(Graphics2D g, ScreenTransform t);

    static Paintable circle(double cx, double cy, double r, Color color) {
        Shape pie = new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r);
        return new PaintableShape(pie, color, false);
    }

    static Paintable pie(double cx, double cy, double r, Color color) {
        Shape pie = new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r);
        return new PaintableShape(pie, color, true);
    }

    static Paintable line(double fromX, double fromY, double toX, double toY, Color color) {
        Shape line = new Line2D.Double(fromX, fromY, toX, toY);
        return new PaintableShape(line, color, false);
    }
}
