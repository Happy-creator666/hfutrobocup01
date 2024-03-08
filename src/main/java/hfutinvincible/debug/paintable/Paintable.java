package hfutinvincible.debug.paintable;

import rescuecore2.misc.geometry.Point2D;
import rescuecore2.misc.gui.ScreenTransform;

import javax.annotation.Nonnull;
import java.awt.*;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;

@FunctionalInterface
public interface Paintable {
    void paint(Graphics2D g, ScreenTransform t);

    //////////////// Factory Functions ////////////////

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

    //重载
    static Paintable line(Point2D startP,Point2D endP,Color color) {
        double fromX = startP.getX();
        double fromY = startP.getY();
        double toX = endP.getX();
        double toY = endP.getY();
        Shape line = new Line2D.Double(fromX, fromY, toX, toY);
        return new PaintableShape(line, color, false);
    }


    Font DEFAULT_FONT = new Font("TimesRoman", Font.BOLD, 16);

    static Paintable text(double x, double y, String text, Color color) {
        return new PaintableText(x, y, text, color, Paintable.DEFAULT_FONT);
    }

    /**
     * @param fill 如果 {@code fill == true}，则会用 {@code color} 填充 {@code shape}，
     *             否则不会填充，但会用 {@code color} 勾勒 {@code shape} 的边框。
     */
    static Paintable shape(Shape shape, Color color, boolean fill) {
        return new PaintableShape(shape, color, fill);
    }


    /*
    * 画一个十字
    * */
    static Paintable pointcross(int x, int y, int size, Color color){
        return new PaintableCross(new Point2D(x, y),size,color);
    }

    /*
     * 画一个十字
     * */
    static Paintable pointcross(double x, double y, int size, Color color){
        return new PaintableCross(new Point2D(x, y),size,color);
    }

    /*
     * 画一个横向线性箭头
     * */
    static Paintable arrowhead(int fromx,int fromy,int tox,int toy,int arrowsize,Color color){
        return new PaintableArrowHead(new Point2D(fromx,fromy),new Point2D(tox,toy),arrowsize,color);
    }

    static Paintable arrowhead(double fromx,double fromy,double tox,double toy,int arrowsize,Color color){
        return new PaintableArrowHead(new Point2D(fromx,fromy),new Point2D(tox,toy),arrowsize,color);
    }


    ////////////////////////////////////////

    /**
     * 将一个颜色转换为透明色（添加 alpha）
     * @param origin 原来不透明的颜色
     * @param alpha 透明度
     */
    static Color addAlpha(Color origin, int alpha) {
        return new Color(
                origin.getRed(),
                origin.getGreen(),
                origin.getBlue(),
                alpha);
    }
}
