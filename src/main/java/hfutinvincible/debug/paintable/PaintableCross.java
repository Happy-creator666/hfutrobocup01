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
    private boolean fill;
    private static final Stroke LINE_STROKE = new BasicStroke(2, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER);
    public PaintableCross(Point2D p, int size, Color color, boolean fill){
        cross = new GeneralPath();
        cross.append(new Line2D.Double(p.getX() - size,p.getY(),size*2,0),false);
        cross.append(new Line2D.Double(p.getX(),p.getY() - size,0,size*2),false);
        this.color = color;
        this.fill = fill;
    }

    @Override
    public void paint(Graphics2D g, ScreenTransform t) {
        GeneralPath path = new GeneralPath();
        PathIterator iter = cross.getPathIterator(null);
        double[] data = new double[6];
        while (!iter.isDone()) {
            int type = iter.currentSegment(data);
            for (int i = 0; i < data.length; i += 2) {
                data[i] = t.xToScreen(data[i]);
                data[i + 1] = t.yToScreen(data[i + 1]);
            }
            switch (type) {
                case PathIterator.SEG_MOVETO -> path.moveTo(data[0], data[1]);
                case PathIterator.SEG_LINETO -> path.lineTo(data[0], data[1]);
                case PathIterator.SEG_QUADTO -> path.quadTo(data[0], data[1], data[2], data[3]);
                case PathIterator.SEG_CUBICTO -> path.curveTo(data[0], data[1], data[2], data[3], data[4], data[5]);
                case PathIterator.SEG_CLOSE -> path.closePath();
            }
            iter.next();
        }
        g.setColor(color);
        if (fill)
            g.fill(path);
        else {
            g.setStroke(LINE_STROKE);
            g.draw(path);
        }
    }
}
