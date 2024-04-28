package hfutinvincible.debug.paintable;

import hfutinvincible.anno.Autowired;
import org.locationtech.jts.geom.Geometry;
import rescuecore2.misc.geometry.GeometryTools2D;
import rescuecore2.misc.geometry.Line2D;
import rescuecore2.misc.geometry.Vector2D;
import rescuecore2.misc.gui.ScreenTransform;
import rescuecore2.misc.geometry.Point2D;
import java.awt.*;
import java.awt.geom.GeneralPath;


/** 画箭头*/
public class PaintableArrowHead implements Paintable{

    private GeneralPath arrowhead;
    private Color color;
    private Point2D fromp;
    private Point2D top;
    private int arrowsize;
    private static final Stroke LINE_STROKE = new BasicStroke(2, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER);


    /** point1表示起始点 point2 表示直线与箭头之间的中心点 arrowsize 表示箭头的大小 即“>”两边的长度*/
    public PaintableArrowHead(Point2D fromp,Point2D top,int arrowsize,Color color){
        this.fromp = fromp;
        this.top = top;
        this.arrowsize = arrowsize;
        this.color = color;
    }
    @Override
    public void paint(Graphics2D g, ScreenTransform t) {

        GeneralPath linePath = new GeneralPath();
        GeneralPath arrowPath = new GeneralPath();
        double angle = Math.toRadians(30);
        Line2D line = new Line2D(fromp,top);
        Vector2D vector2D= line.getDirection().normalised();  //方向向量
        //获得反向向量
        Vector2D addvector2D1 = vector2D.getNormal().getNormal().scale(Math.cos(angle) * arrowsize);
        double x = top.getX() + addvector2D1.getX();
        double y = top.getY() + addvector2D1.getY();
        //得到垂直方向的向量(逆时针方向)
        Vector2D norvector2D1 = vector2D.getNormal();
        Vector2D addvector2D2 = norvector2D1.normalised().scale(Math.sin(angle) * arrowsize);
        //得到垂直方向的向量(顺时针)
        Vector2D norvector2D2 = vector2D.getNormal().getNormal().getNormal();
        Vector2D addvector2D3 = norvector2D2.normalised().scale(Math.sin(angle) * arrowsize);




        double []xs = {
                fromp.getX(),
                top.getX(),  //箭头的顶点
                x + addvector2D2.getX(),
                x + addvector2D3.getX()
        };
        double []ys = {
                fromp.getY(),
                top.getY(),  //箭头的顶点
                y + addvector2D2.getY(),
                y + addvector2D3.getY()
        };
        // 转换坐标
        for(int i=0;i<4;i++){
            xs[i] = t.xToScreen(xs[i]);
            ys[i] = t.yToScreen(ys[i]);
        }
        // 构建直线路径
        linePath.moveTo(xs[0],ys[0]);
        linePath.lineTo(xs[1],ys[1]);
        // 设置直线颜色并绘制
        g.setColor(color);
        g.setStroke(LINE_STROKE);
        g.draw(linePath);

        // 绘制箭头部分
        arrowPath.moveTo(xs[1],ys[1]);
        arrowPath.lineTo(xs[2],ys[2]);
        arrowPath.moveTo(xs[1],ys[1]);
        arrowPath.lineTo(xs[3],ys[3]);
        // 设置箭头颜色并绘制
        g.setColor(color);
        g.draw(arrowPath);
    }

}
