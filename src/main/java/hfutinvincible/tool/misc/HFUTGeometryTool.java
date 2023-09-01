package hfutinvincible.tool.misc;

import adf.core.agent.info.AgentInfo;
import hfutinvincible.tool.area.ToJts;
import hfutinvincible.tool.edge.HFUTEdge;
import org.locationtech.jts.geom.*;
import rescuecore2.misc.geometry.GeometryTools2D;
import rescuecore2.misc.geometry.Line2D;
import rescuecore2.misc.geometry.Point2D;
import rescuecore2.misc.geometry.Vector2D;
import rescuecore2.standard.entities.Area;
import rescuecore2.standard.entities.Edge;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Function;

/**
 * 一些纯静态计算几何工具，{@link GeometryTools2D} 里的所有方法都搬到这里来了
 *
 * @author stardust
 */
public final class HFUTGeometryTool {
    public static final double THRESHOLD = 1.0E-13D;
    public static int MIN_X = 0;
    public static int MAX_X = +808000000;
    public static int MIN_Y = -800000000;
    public static int MAX_Y = 0;
    public static final GeometryFactory GF = new GeometryFactory();

    private HFUTGeometryTool() {} // 禁止构造

    /**
     * 检测角 angleToCheck 是否夹在 angle1 和 angle2 之间（角为弧度值，范围 [0, 2 * PI)）
     */
    public static boolean isBetween(double a1, double a2, double ac) {
        double diff = a2 - a1;
        if (diff < 0.)
            diff = diff + Math.PI * 2;
        if (diff > Math.PI) { // a2 --- a1
            double a3 = a1 - a2;
            double ac3 = ac - a2;
            if (a3 < 0)
                a3 += Math.PI * 2;
            if (ac3 < 0)
                ac3 += Math.PI * 2;
            return ac3 < a3;
        } else { // a1 --- a2
            double a3 = a2 - a1;
            double ac3 = ac - a1;
            if (a3 < 0)
                a3 += Math.PI * 2;
            if (ac3 < 0)
                ac3 += Math.PI * 2;
            return ac3 < a3;
        }
    }


    /**
     * 两直线交点
     */
    public static Point2D lineIntersection(Line2D l1, Line2D l2) {
        double t1 = l1.getIntersection(l2);
        double t2 = l2.getIntersection(l1);
        return !Double.isNaN(t1) && !Double.isNaN(t2) ? l1.getPoint(t1) : null;
    }

    /**
     * 两线段交点
     */
    public static Point2D lineSegmentIntersection(Line2D l1, Line2D l2) {
        double t1 = l1.getIntersection(l2);
        double t2 = l2.getIntersection(l1);
        return !Double.isNaN(t1) && !Double.isNaN(t2) && !(t1 < 0.0D) && !(t1 > 1.0D) && !(t2 < 0.0D) && !(t2 > 1.0D) ? l1.getPoint(t1) : null;
    }

    public static boolean parallel(Line2D l1, Line2D l2) {
        double d = l1.getDirection().getX() * l2.getDirection().getY() - l1.getDirection().getY() * l2.getDirection().getX();
        return nearlyZero(d);
    }

    public static boolean contains(Line2D line, Point2D point) {
        double tx;
        double ty;
        double d;
        double xMax;
        if (nearlyZero(line.getDirection().getX())) {
            tx = point.getX() - line.getOrigin().getX();
            ty = point.getY();
            d = Math.min(line.getOrigin().getY(), line.getEndPoint().getY());
            xMax = Math.max(line.getOrigin().getY(), line.getEndPoint().getY());
            return nearlyZero(tx) && ty >= d && ty <= xMax;
        } else if (!nearlyZero(line.getDirection().getY())) {
            tx = (point.getX() - line.getOrigin().getX()) / line.getDirection().getX();
            ty = (point.getY() - line.getOrigin().getY()) / line.getDirection().getY();
            if (!(tx < 0.0D) && !(tx > 1.0D) && !(ty < 0.0D) && !(ty > 1.0D)) {
                d = tx - ty;
                return nearlyZero(d);
            } else {
                return false;
            }
        } else {
            tx = point.getY() - line.getOrigin().getY();
            ty = point.getX();
            d = Math.min(line.getOrigin().getX(), line.getEndPoint().getX());
            xMax = Math.max(line.getOrigin().getX(), line.getEndPoint().getX());
            return nearlyZero(tx) && ty >= d && ty <= xMax;
        }
    }

    public static double positionOnLine(Line2D line, Point2D point) {
        double tx;
        if (nearlyZero(line.getDirection().getX())) {
            tx = (point.getY() - line.getOrigin().getY()) / line.getDirection().getY();
            return tx;
        } else if (nearlyZero(line.getDirection().getY())) {
            tx = (point.getX() - line.getOrigin().getX()) / line.getDirection().getX();
            return tx;
        } else {
            tx = (point.getX() - line.getOrigin().getX()) / line.getDirection().getX();
            double ty = (point.getY() - line.getOrigin().getY()) / line.getDirection().getY();
            double d = tx - ty;
            return nearlyZero(d) ? tx : 0.0D / 0.0;
        }
    }

    public static double getRightAngleBetweenLines(Line2D first, Line2D second) {
        return getRightAngleBetweenVectors(first.getDirection(), second.getDirection());
    }

    public static double getLeftAngleBetweenLines(Line2D first, Line2D second) {
        return getLeftAngleBetweenVectors(first.getDirection(), second.getDirection());
    }

    public static double getAngleBetweenVectors(Vector2D first, Vector2D second) {
        Vector2D v1 = first.normalised();
        Vector2D v2 = second.normalised();
        double cos = v1.dot(v2);
        if (cos > 1.0D) {
            cos = 1.0D;
        }

        double angle = Math.toDegrees(Math.acos(cos));
        return angle;
    }

    public static double getRightAngleBetweenVectors(Vector2D first, Vector2D second) {
        double angle = getAngleBetweenVectors(first, second);
        return isRightTurn(first, second) ? angle : 360.0D - angle;
    }

    public static double getLeftAngleBetweenVectors(Vector2D first, Vector2D second) {
        double angle = getAngleBetweenVectors(first, second);
        return isRightTurn(first, second) ? 360.0D - angle : angle;
    }

    public static boolean isRightTurn(Line2D first, Line2D second) {
        return isRightTurn(first.getDirection(), second.getDirection());
    }

    public static boolean isRightTurn(Vector2D first, Vector2D second) {
        double t = first.getX() * second.getY() - first.getY() * second.getX();
        return t < 0.0D;
    }

    public static boolean nearlyZero(double d) {
        return d > -1.0E-13D && d < 1.0E-13D;
    }

    public static double computeArea(List<Point2D> vertices) {
        return Math.abs(computeAreaUnsigned(vertices));
    }

    public static Point2D computeCentroid(List<Point2D> vertices) {
        double area = computeAreaUnsigned(vertices);
        Iterator<Point2D> it = vertices.iterator();
        Point2D last = (Point2D)it.next();
        Point2D first = last;
        double xSum = 0.0D;

        double ySum;
        Point2D next;
        for(ySum = 0.0D; it.hasNext(); last = next) {
            next = (Point2D)it.next();
            double lastX = last.getX();
            double lastY = last.getY();
            double nextX = next.getX();
            double nextY = next.getY();
            xSum += (lastX + nextX) * (lastX * nextY - nextX * lastY);
            ySum += (lastY + nextY) * (lastX * nextY - nextX * lastY);
        }

        double lastX = last.getX();
        double lastY = last.getY();
        double nextX = first.getX();
        double nextY = first.getY();
        xSum += (lastX + nextX) * (lastX * nextY - nextX * lastY);
        ySum += (lastY + nextY) * (lastX * nextY - nextX * lastY);
        xSum /= 6.0D * area;
        ySum /= 6.0D * area;
        return new Point2D(xSum, ySum);
    }

    public static List<Point2D> vertexArrayToPoints(int[] vertices) {
        List<Point2D> result = new ArrayList();

        for(int i = 0; i < vertices.length; i += 2) {
            result.add(new Point2D((double)vertices[i], (double)vertices[i + 1]));
        }

        return result;
    }

    public static List<Point2D> vertexArrayToPoints(double[] vertices) {
        List<Point2D> result = new ArrayList();

        for(int i = 0; i < vertices.length; i += 2) {
            result.add(new Point2D(vertices[i], vertices[i + 1]));
        }

        return result;
    }

    public static List<Line2D> pointsToLines(List<Point2D> points) {
        return pointsToLines(points, false);
    }

    public static List<Line2D> pointsToLines(List<Point2D> points, boolean close) {
        List<Line2D> result = new ArrayList();
        Iterator<Point2D> it = points.iterator();
        Point2D first = (Point2D)it.next();

        Point2D prev;
        Point2D next;
        for(prev = first; it.hasNext(); prev = next) {
            next = (Point2D)it.next();
            result.add(new Line2D(prev, next));
        }

        if (close && !prev.equals(first)) {
            result.add(new Line2D(prev, first));
        }

        return result;
    }

    public static Point2D getClosestPoint(Line2D line, Point2D point) {
        Point2D p1 = line.getOrigin();
        Point2D p2 = line.getEndPoint();
        double u = ((point.getX() - p1.getX()) * (p2.getX() - p1.getX()) + (point.getY() - p1.getY()) * (p2.getY() - p1.getY())) / (line.getDirection().getLength() * line.getDirection().getLength());
        return line.getPoint(u);
    }

    public static Point2D getClosestPointOnSegment(Line2D line, Point2D point) {
        Point2D p1 = line.getOrigin();
        Point2D p2 = line.getEndPoint();
        double u = ((point.getX() - p1.getX()) * (p2.getX() - p1.getX()) + (point.getY() - p1.getY()) * (p2.getY() - p1.getY())) / (line.getDirection().getLength() * line.getDirection().getLength());
        if (u <= 0.0D) {
            return p1;
        } else {
            return u >= 1.0D ? p2 : line.getPoint(u);
        }
    }

    /**
     * 计算两条 HFUTEdge 中点间距离
     */
    public static double getDistance(HFUTEdge e1, HFUTEdge e2) {
        return getDistance(e1.getEdge(), e2.getEdge());
    }

    public static double getDistance(double x1, double y1, double x2, double y2) {
        return Math.hypot(x1 - x2, y1 - y2);
    }

    /**
     * 计算两条边中点间距离
     */
    public static double getDistance(Edge e1, Edge e2) {
        return getDistance(getMidPoint(e1), getMidPoint(e2));
    }

    /**
     * 计算两点间距离
     */
    public static double getDistance(Point2D p1, Point2D p2) {
        return Math.hypot(p1.getX() - p2.getX(), p1.getY() - p2.getY());
    }

    public static Line2D clipToRectangle(Line2D line, double xMin, double yMin, double xMax, double yMax) {
        double x1 = line.getOrigin().getX();
        double y1 = line.getOrigin().getY();
        double x2 = line.getEndPoint().getX();
        double y2 = line.getEndPoint().getY();
        double tL = (xMin - x1) / (x2 - x1);
        double tR = (xMax - x1) / (x2 - x1);
        double tT = (yMax - y1) / (y2 - y1);
        double tB = (yMin - y1) / (y2 - y1);
        double tMin = 0.0D;
        double tMax = 1.0D;
        if (x1 < xMin && x2 < xMin || x1 > xMax && x2 > xMax || y1 < yMin && y2 < yMin || y1 > yMax && y2 > yMax) {
            return null;
        } else {
            if (tL > tMin && tL < tMax) {
                if (x1 < x2) {
                    tMin = tL;
                } else {
                    tMax = tL;
                }
            }

            if (tR > tMin && tR < tMax) {
                if (x1 > x2) {
                    tMin = tR;
                } else {
                    tMax = tR;
                }
            }

            if (tT > tMin && tT < tMax) {
                if (y1 > y2) {
                    tMin = tT;
                } else {
                    tMax = tT;
                }
            }

            if (tB > tMin && tB < tMax) {
                if (y1 < y2) {
                    tMin = tB;
                } else {
                    tMax = tB;
                }
            }

            return tMin > tMax ? null : new Line2D(line.getPoint(tMin), line.getPoint(tMax));
        }
    }

    private static double computeAreaUnsigned(List<Point2D> vertices) {
        Iterator<Point2D> it = vertices.iterator();
        Point2D last = (Point2D)it.next();
        Point2D first = last;

        double sum;
        Point2D next;
        for(sum = 0.0D; it.hasNext(); last = next) {
            next = (Point2D)it.next();
            double lastX = last.getX();
            double lastY = last.getY();
            double nextX = next.getX();
            double nextY = next.getY();
            sum += lastX * nextY - nextX * lastY;
        }

        double lastX = last.getX();
        double lastY = last.getY();
        double nextX = first.getX();
        double nextY = first.getY();
        sum += lastX * nextY - nextX * lastY;
        sum /= 2.0D;
        return sum;
    }

    /**
     * 将 {@code List<LineSegment>} 转换为 {@link LineString}
     */
    @Nonnull
    public static LineString toLineString(@Nonnull List<LineSegment> lineSegments) {

        List<Coordinate> coordinates = new ArrayList<>();
        coordinates.add(lineSegments.get(0).p0);
        for (LineSegment ls : lineSegments)
            coordinates.add(ls.p1);

        return GF.createLineString(coordinates.toArray(new Coordinate[0]));
    }

    /**
     * 返回一个 Line2D 的中点
     */
    public static Point2D getMidPoint(Line2D line) {
        return line.getPoint(0.5);
    }

    /**
     * 返回一条 Edge 的中点
     */
    public static Point2D getMidPoint(Edge edge) {
        return edge.getLine().getPoint(0.5);
    }

    /**
     * 返回一条 HFUTEdge 的中点
     */
    public static Point2D getMidPoint(HFUTEdge edge) {
        return getMidPoint(edge.getEdge());
    }

    /**
     * 返回一条 HFUTEdge 的中点 Coordinate
     */
    public static Coordinate getMidPointCoordinate(HFUTEdge edge) {
        return new CoordinateXY(
                ((double) (edge.getStartX() + edge.getEndX())) / 2,
                ((double) (edge.getStartY() + edge.getEndY())) / 2
        );
    }

    /**
     * 返回一条 HFUTEdge 的中点 Coordinate
     */
    public static Coordinate getMidPointCoordinate(Edge edge) {
        return new CoordinateXY(
                ((double) (edge.getStartX() + edge.getEndX())) / 2,
                ((double) (edge.getStartY() + edge.getEndY())) / 2
        );
    }

    /**
     * 将 line 垂直左移 dist 单位
     */
    public static Line2D moveLeftVertically(Line2D line, double dist) {
        Vector2D vector = line.getDirection().getNormal().normalised();
        Vector2D leftVector = vector.scale(-dist);
        return new Line2D(line.getOrigin().plus(leftVector), line.getEndPoint().plus(leftVector));
    }

    /**
     * 将 line 垂直左移 dist 单位
     */
    public static LineSegment moveLeftVertically(LineSegment line, double dist) {
        org.locationtech.jts.math.Vector2D vector = new org.locationtech.jts.math.Vector2D(line.p0, line.p1)
                .rotateByQuarterCircle(1)
                .normalize()
                .multiply(dist);
        return new LineSegment(vector.translate(line.p0), vector.translate(line.p1));
    }

    /**
     * 将 line 垂直右移 dist 单位
     */
    public static LineSegment moveRightVertically(LineSegment line, double dist) {
        org.locationtech.jts.math.Vector2D vector = new org.locationtech.jts.math.Vector2D(line.p0, line.p1)
                .rotateByQuarterCircle(-1)
                .normalize()
                .multiply(dist);
        return new LineSegment(vector.translate(line.p0), vector.translate(line.p1));
    }

    /**
     * 将 line 垂直右移 dist 单位
     */
    public static Line2D moveRightVertically(Line2D line, double dist) {
        Vector2D vector = line.getDirection().getNormal().normalised();
        Vector2D rightVector = vector.scale(dist);
        return new Line2D(line.getOrigin().plus(rightVector), line.getEndPoint().plus(rightVector));
    }

    /**
     * 两向量叉积
     */
    static public double crossProduct(Vector2D vec1, Vector2D vec2) {
        return vec1.getX() * vec2.getY() - vec1.getY() * vec2.getX();
    }

    /**
     * 两向量叉积
     */
    static public double crossProduct(double x1, double y1, double x2, double y2) {
        return x1 * y2 - x2 * y1;
    }

    /**
     * 返回一个 agent 所在的位置点
     */
    public static Point2D getAgentPosPoint(AgentInfo agentInfo) {
        return new Point2D(agentInfo.getX(), agentInfo.getY());
    }

    /**
     * 返回一个 area 的中心点
     */
    public static Point2D getAreaCenterPoint(Area area) {
        return new Point2D(area.getX(), area.getY());
    }

    /**
     * 获得一个多边形的中心
     */
    public static Point2D getPolygonCenterPoint(List<Point2D> polygon) {
        int x = 0;
        int y = 0;
        for (Point2D point : polygon) {
            x += point.getX();
            y += point.getY();
        }
        x /= polygon.size();
        y /= polygon.size();
        return new Point2D(x, y);
    }

    /**
     * 判断一个点是否在多边形内部
     */
    static public boolean inPolygonEx(List<Point2D> polygon, Point2D p) {
        Point2D left_end = new Point2D(MIN_X, p.getY());
        Line2D l1 = new Line2D(p, left_end);
        int intersect_cnt = 0;
        int n = polygon.size();
        Point2D pre = polygon.get(n - 1);
        for (int i = 0; i < n; i++) {
            Point2D cur = polygon.get(i);
            Line2D l2 = new Line2D(pre, cur);
            int is_int = isIntersect(l1, l2);
            if (is_int > 0) intersect_cnt++;
            else if (is_int < 0) return inPolygonExDeep(polygon, p);
            pre = cur;
        }
        return intersect_cnt % 2 == 1;
    }

    public static boolean inPolygonExDeep(List<Point2D> polygon, Point2D p) {

        Point2D left_end = new Point2D(MIN_X, p.getY());
        Point2D right_end = new Point2D(MAX_X, p.getY());
        Point2D down_end = new Point2D(p.getX(), MIN_Y);
        Point2D up_end = new Point2D(p.getX(), MAX_Y);
        Set<Point2D> point_set = new HashSet<>();

        point_set.add(left_end);
        point_set.add(right_end);
        point_set.add(down_end);
        point_set.add(up_end);

        for (Point2D endp : point_set) {
            Line2D l1 = new Line2D(p, endp);
            boolean is_intercted = false;
            int n = polygon.size();
            Point2D pre = polygon.get(n - 1);
            for (Point2D cur : polygon) {
                Line2D l2 = new Line2D(pre, cur);
                int is_int = isIntersect(l1, l2);
                if (is_int != 0) {
                    is_intercted = true;
                    break;
                }
                pre = cur;
            }
            if (!is_intercted)
                return false;
        }
        return true;
    }

    /**
     * 判断两线段是否相交
     * @return 0: 不相交. <br>
     *         -1: 线段相交. <br>
     *         1: 所在直线相交. <br>
     */
    static public int isIntersect(Line2D l1, Line2D l2) {
        Point2D p1 = l1.getOrigin();
        Point2D p2 = l1.getEndPoint();
        Point2D q1 = l2.getOrigin();
        Point2D q2 = l2.getEndPoint();

        double d1 = crossProduct(p2.minus(p1), q1.minus(p1));
        double d2 = crossProduct(p2.minus(p1), q2.minus(p1));
        double d3 = crossProduct(q2.minus(q1), p1.minus(q1));
        double d4 = crossProduct(q2.minus(q1), p2.minus(q1));

        if ((d1 > 0 && d2 > 0) || (d1 < 0 && d2 < 0))
            return 0;
        if ((d3 > 0 && d4 > 0) || (d3 < 0 && d4 < 0))
            return 0;
        if ((d1 != 0 && d2 == 0) || (d3 != 0 && d4 == 0))
            return 0;
        if (d1 == 0 || d3 == 0)
            return -1;
        return 1;
    }

    /**
     * 获得两向量间夹角 (弧度制)
     */
    private static double getAngleBetweenTwoVectors(Vector2D vec1, Vector2D vec2) {
        double sin = (vec1.getX() * vec2.getY() - vec1.getY() * vec2.getX())
                / (vec1.getLength() * vec2.getLength());
        double cos = (vec1.getX() * vec2.getX() + vec1.getY() * vec2.getY())
                / (vec1.getLength() * vec2.getLength());

        double result = Math.atan2(sin, cos);
        if (result < 0)
            result += 2 * Math.PI;
        return result;
    }

    /**
     * 计算与同一 Cross 邻接的两个 Avenue 所成的夹角
     */
//    public static double angleOfTwoAvenues(Cross org, Avenue src, Avenue tag) {
//
//        Point2D c_org = org.getCenterPoint();
//
//        Area src_area = src.getAdjoinedArea(org);
//        Point2D c_src = new Point2D(src_area.getX(), src_area.getY());
//        Area tag_area = tag.getAdjoinedArea(org);
//        Point2D c_tag = new Point2D(tag_area.getX(), tag_area.getY());
//
//        Vector2D vec1 = c_src.minus(c_org);
//        Vector2D vec2 = c_tag.minus(c_org);
//
//        return getAngleBetweenTwoVectors(vec1, vec2);
//    }

    /**
     * 判断 ba -> ca 是否为逆时针转动（外积小于 0）
     */
    public static boolean isCounterClockwise(Point2D a, Point2D b, Point2D c) {
        return ((b.getX() - a.getX()) * (c.getY() - a.getY()) ) > ((b.getY() - a.getY()) * (c.getX() - a.getX()));
    }

    /**
     * 将 polygon 从顶点列表 {@literal double[]} 转换为边列表 {@literal List<Line2D>} 表示
     */
    public static List<Line2D> polygonApexesToEdges(List<Point2D> polygonApexes){
        List<Line2D> polygonEdges = new ArrayList<Line2D>();
        Iterator<Point2D> iter1 = polygonApexes.iterator();
        Iterator<Point2D> iter2 = polygonApexes.iterator();
        iter2.next();
        while (iter1.hasNext() && iter2.hasNext()) {
            Line2D edge = new Line2D(iter1.next(), iter2.next());
            polygonEdges.add(edge);
        }
        polygonEdges.add(new Line2D(polygonApexes.get(polygonApexes.size() - 1), polygonApexes.get(0)));
        return polygonEdges;
    }

    /**
     * 将 polygon 从顶点列表 {@literal int[]} 转换为边列表 {@literal List<Line2D>} 表示
     */
    public static List<Line2D> polygonApexesToEdges(double[] vertexArray){
        List<Point2D> polygonApexes = vertexArrayToPoints(vertexArray);
        return polygonApexesToEdges(polygonApexes);
    }

    /**
     * 将 polygon 从顶点列表 {@literal List<Point2D>} 转换为边列表 {@literal List<Line2D>} 表示
     */
    public static List<Line2D> polygonApexesToEdges(int[] vertexArray){
        List<Point2D> polygonApexes = vertexArrayToPoints(vertexArray);
        return polygonApexesToEdges(polygonApexes);
    }

    /**
     * 多边形与线段交点
     */
    public static List<Point2D> lineSegmentPolygonIntersection(List<Line2D> polygon, Line2D line) {
        List<Point2D> result = new ArrayList<>();
        for (Line2D edge : polygon) {
            Point2D point = lineSegmentIntersection(edge, line);
            if (point != null)
                result.add(point);
        }
        return result;
    }

    /**
     * 计算 vec1 在 vec2 上的投影
     */
    public static double vectorProject(Vector2D vec1, Vector2D vec2) {
        return vec1.dot(vec2) / vec2.getLength();
    }

    /**
     * 计算点到直线的距离
     */
    public static double pointLineDistance(Point2D point, Line2D line) {
        Point2D from = line.getOrigin();
        Vector2D lineVec = line.getDirection();
        Vector2D pointVec = new Vector2D(point.getX() - from.getX(), point.getY() - from.getY());
        return Math.abs(crossProduct(lineVec, pointVec) / lineVec.getLength());
    }

    /**
     * 找出 edges 中距离 point 最近的 edge
     */
    public static HFUTEdge getNearestEdge(Point2D point, List<HFUTEdge> edges) {
        double minDist = Double.MIN_VALUE;
        HFUTEdge minDistEdge = null;
        for (HFUTEdge edge : edges) {
            double dist = pointLineDistance(point, edge.getEdge().getLine());
            if (dist < minDist) {
                minDist = dist;
                minDistEdge = edge;
            }
        }
        return minDistEdge;
    }



    /**
     * 计算一堆 points 的中心
     */
    public static <T> Point2D getCentroid(Collection<T> elements, Function<T, Point2D> toPoint) {
        double sumX = 0.0;
        double sumY = 0.0;
        int num = elements.size();
        for (T e : elements) {
            Point2D p = toPoint.apply(e);
            sumX += p.getX();
            sumY += p.getY();
        }
        return new Point2D(sumX / num, sumY / num);
    }

    /**
     * 判断两个点是否为同一点（允许的两点间距离的误差为 threshold）
     */
    public static boolean isSamePoint(Point2D point1, Point2D point2, double threshold) {
        double dist = getDistance(point1, point2);
        if (dist < threshold) return true;
        else return false;
    }

    /**
     * 判断两个点是否为同一点（允许的两点间距离的误差为 OptionsTool.SAME_POINT_THRESHOLD）
     */
    public static boolean isSamePoint(Point2D point1, Point2D point2) {
        return isSamePoint(point1, point2, OptionsTool.SAME_POINT_THRESHOLD);
    }

    public static double getLength(Edge e) {
        return Math.hypot(e.getStartX() - e.getEndX(), e.getStartY() - e.getEndY());
    }

    /**
     * 获得一个 Area 与 edge 对面的可通行边，考虑边的可通过性. <br>
     * 判据: 查询边和 Area 中点的射线和哪条边相交，就认为这条边为查询边的对边
     */
//    public static Edge getOppositeEdge(Area road, Edge original, boolean considerPassability) {
//        List<Edge> edges = new ArrayList<>();
//        for(Edge edge : road.getEdges()){
//            if(!considerPassability || edge.isPassable() && !HFUTGeometryTool.getMidPoint(edge).equals(HFUTGeometryTool.getMidPoint(original))){
//                edges.add(edge);
//            }
//        }
//        if(edges.size() > 0) {
//            Point2D roadCenter = new Point2D(road.getX(), road.getY());
//            Point2D originalMid = HFUTGeometryTool.getMidPoint(original);
//            Vector2D standardDirection = new Vector2D(roadCenter.getX() - originalMid.getX(), roadCenter.getY() - originalMid.getY());
//            Edge answerEdge = null;
//            double minAngle = Double.MAX_VALUE;
//            for (int i = 0; i < edges.size(); ++i) {
//                Point2D mid = HFUTGeometryTool.getMidPoint(edges.get(i));
//                Vector2D testDirection = new Vector2D(mid.getX() - originalMid.getX(), mid.getY() - originalMid.getY());
//                double angle = HFUTGeometryTool.getAngleBetweenVectors(standardDirection, testDirection);
//                if (angle < minAngle) {
//                    minAngle = angle;
//                    answerEdge = edges.get(i);
//                }
//            }
//            return answerEdge;
//        }
//        return null;
//    }

    @Nullable
    public static Edge getOppositeEdge(Area road, Edge original, boolean considerPassability, boolean considerDistance) {
        List<Edge> edges = new ArrayList<>();
        // 获得所有满足通行性要求的且与查询边不同的边
        for(Edge edge : road.getEdges()){
            if(!considerPassability || edge.isPassable()){
                if (!isEqualEdges(original, edge) && (!considerDistance || ToJts.fromRcrsEdge(original).distance(ToJts.fromRcrsEdge(edge)) > 300)) { // TODO
                    edges.add(edge);
                }
            }
        }
        if(edges.size() > 0) {
            Coordinate centerPoint = new CoordinateXY(road.getX(), road.getY());
            Coordinate originalMidPoint = new CoordinateXY(
                    ((double) (original.getStartX() + original.getEndX())) / 2,
                    ((double) (original.getStartY() + original.getEndY())) / 2
            );
            LineSegment checkLine = new LineSegment(originalMidPoint, centerPoint);
            for (Edge edge : edges) {
                LineSegment edgeLine =
                        new LineSegment(edge.getStartX(), edge.getStartY(), edge.getEndX(), edge.getEndY());
                Coordinate intersection = checkLine.lineIntersection(edgeLine);
                if (intersection != null) {
                    double factor = edgeLine.projectionFactor(intersection);
                    if (factor >= 0.0 && factor <= 1.0)
                        return edge;
                }
            }
        }
        return null;
    }

    @Nullable
    public static Edge getOppositeEdge(Area road, Edge original) {
        Edge result = getOppositeEdge(road, original, true, true);
        if (result ==  null)
            result = getOppositeEdge(road, original, false, true);
        if (result == null)
            result = getOppositeEdge(road, original, false, false);
        return result;
    }

    /**
     * 判断两条边是否相等（不考虑方向）
     */
    public static boolean isEqualEdges(Edge e1, Edge e2) {
        return (e1.getStart().equals(e2.getStart()) && e1.getEnd().equals(e2.getEnd())) ||
                (e1.getStart().equals(e2.getEnd()) && e1.getEnd().equals(e2.getStart()));
    }

    /**
     * 判断点是否在圆上
     */
    private static boolean onCircle(Point2D point, Point2D center, double r) {
        return dcmp(getDistance(point, center) - r) == 0;
    }

    /**
     * 判断点是否在圆上
     */
    private static boolean onCircle(Coordinate point, Coordinate center, double r) {
        return dcmp(point.distance(center) - r) == 0;
    }

    private static final double eps = 1e-10;

    private static int dcmp(double x) {
        if (Math.abs(x) < eps)
            return 0;
        return x < 0 ? -1 : 1;
    }
    /**
     * 计算两圆交点
     */
    public static List<Coordinate> circleIntersection(Coordinate center1, Coordinate center2, double r1, double r2) {
        double distCenter = center1.distance(center2);
        // 两圆内含
        if (dcmp(Math.abs(r1 - r2) - distCenter) > 0)
            return List.of();
        // 两圆外离
        if (dcmp(r1 + r2 - distCenter) < 0)
            return List.of();

        double x1 = center1.getX();
        double x2 = center2.getX();
        double y1 = center1.getY();
        double y2 = center2.getY();

        double a = 2.0 * r1 * (x1 - x2);
        double b = 2.0 * r1 * (y1 - y2);
        double c = r2 * r2 - r1 * r1 - (x1 - x2) * (x1 - x2) - (y1 - y2) * (y1 - y2);
        double p = a * a + b * b;
        double q = - a * c * 2.0;
        double r = c * c - b * b;

        // 相切
        if (dcmp(distCenter - (r1 + r2)) == 0 || dcmp(distCenter - Math.abs(r1 - r2)) == 0) {
            double cosa = -q / p / 2.0;
            double sina = Math.sqrt(1 - cosa * cosa);
            CoordinateXY intersection = new CoordinateXY(x1 + r1 * cosa, y1 + r1 * sina);
            if (!onCircle(intersection, center2, r2))
                intersection = new CoordinateXY(intersection.getX(), y1 - r1 * sina);
            return List.of(intersection);
        }

        // 相交
        double delta = Math.sqrt(q * q - p * r * 4.0);
        double cosa = (delta - q) / p / 2.0;
        double cosb = (- delta - q) / p / 2.0;
        double sina = Math.sqrt(1.0 - cosa * cosa);
        double sinb = Math.sqrt(1.0 - cosb * cosb);
        CoordinateXY intersrction1 = new CoordinateXY(x1 + r1 * cosa, y1 + r1 * sina);
        CoordinateXY intersection2 = new CoordinateXY(x1 + r1 * cosb, y1 + r1 * sinb);
        if (!onCircle(intersrction1, center2, r2))
            intersrction1 = new CoordinateXY(intersrction1.getX(), y1 - r1 * sina);
        if (!onCircle(intersection2, center2, r2))
            intersection2 = new CoordinateXY(intersection2.getX(), y1 - r1 * sinb);
        if (intersrction1.equals(intersection2))
            intersrction1 = new CoordinateXY(intersrction1.getX(), y1 - r1 * sina);
        return List.of(intersrction1, intersection2);
    }

    public static List<Coordinate> circleIntersection(double c1x, double c1y, double c2x, double c2y, double r1, double r2) {
        return circleIntersection(new CoordinateXY(c1x, c1y), new CoordinateXY(c2x, c2y), r1, r2);
    }
}
