package hfutinvincible.debug.paintable;

import rescuecore2.misc.geometry.Point2D;
import rescuecore2.misc.gui.ScreenTransform;

import java.awt.*;
import java.awt.geom.GeneralPath;
import java.awt.geom.Line2D;
import java.awt.geom.PathIterator;

public class PaintableCross implements Paintable{
    private GeneralPath cross;
    private Color color;
    private Point2D p;
    private int size;
    private static final Stroke LINE_STROKE = new BasicStroke(2, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER);

    public PaintableCross(Point2D p, int size, Color color){
        this.p = p;
        this.size = size;
        this.color = color;
    }

    @Override
    public void paint(Graphics2D g, ScreenTransform t) {
        GeneralPath path = new GeneralPath();
        double [] xs = {
                p.getX() - size,
                p.getX() + size,
                p.getX(),
                p.getX(),
        };
        double [] ys = {
                p.getY(),
                p.getY(),
                p.getY() - size,
                p.getY() + size,
        };
        for (int i=0; i<4; i++) {
            xs[i] = t.xToScreen(xs[i]);
            ys[i] = t.yToScreen(ys[i]);
        }
        path.moveTo(xs[0],ys[0]);
        path.lineTo(xs[1],ys[1]);
        path.moveTo(xs[2],ys[2]);
        path.lineTo(xs[3],ys[3]);
        g.setColor(color);
        g.setStroke(LINE_STROKE);
        g.draw(path);
    }
}
