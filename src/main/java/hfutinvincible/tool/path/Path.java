package hfutinvincible.tool.path;

import hfutinvincible.tool.misc.OptionsTool;
import org.apache.log4j.Logger;
import rescuecore2.misc.geometry.Point2D;
import rescuecore2.standard.entities.Area;
import rescuecore2.standard.entities.Edge;
import rescuecore2.worldmodel.AbstractEntity;
import rescuecore2.worldmodel.EntityID;
import hfutinvincible.debug.TestLogger;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

public class Path {

    private final Logger logger;

    private LinkedList<Area> roadList;
    private double length;

    public Path(Area from) {
        this.logger = TestLogger.getLogger("Path");
        this.roadList = new LinkedList<>();
        this.roadList.add(from);
        this.length = 0.0;
    }

    public Path(Path copy) {
        this.logger = copy.logger;
        this.roadList = new LinkedList<>(copy.roadList);
        this.length = copy.length;
    }

    public Path(List<Area> roadList) {
        this.logger = TestLogger.getLogger("Path");
        this.roadList = new LinkedList<>(roadList);
        this.length = getExactLength(roadList);
    }

    /**
     * 判断一个 Area 是否在 Path 中
     */
    public boolean contains(Area area) {
        return this.roadList.contains(area);
    }

    /**
     * 判断一个 Path 是否为空
     */
    public boolean isEmpty() {
        return this.roadList.isEmpty();
    }

    /**
     * 返回一个 Path 由几个 Area 构成
     */
    public int size() {
        return this.roadList.size();
    }

    /**
     * 获得 Path 中一个 Area 的索引
     */
    public int indexOf(Area area) {
        return this.roadList.indexOf(area);
    }

    /**
     * 返回反向后的 Path，这一操作不是原地的
     */
    public Path reverse() {
        Path ret = new Path(this);
        Collections.reverse(ret.roadList);
        return ret;
    }

    /**
     * 返回起点 Area
     */
    public Area getStart() {
        return this.roadList.get(0);
    }

    /**
     * 返回终点 Area
     */
    public Area getEnd() {
        return this.roadList.get(this.roadList.size() - 1);
    }

    /**
     * 返回组成 Path 的 Road 列表
     */
    public LinkedList<Area> getRoadList() {
        return this.roadList;
    }

    /**
     * 返回 Path 的通行长度
     */
    public double getLength() {
        return this.length;
    }

    /**
     * 返回 Path 上两点间的通行长度
     */
    public double getLength(Point2D from, Point2D to) {
        if (this.roadList.size() >= 2) {
            Edge fromEdge = roadList.get(0).getEdgeTo(roadList.get(1).getID());
            Edge toEdge = roadList.get(roadList.size() - 2).getEdgeTo(roadList.get(roadList.size() - 1).getID());
            double fromEdgeMidX = (fromEdge.getStartX() + fromEdge.getEndX()) / 2.0;
            double fromEdgeMidY = (fromEdge.getStartY() + fromEdge.getEndY()) / 2.0;
            double toEdgeMidX = (toEdge.getStartX() + toEdge.getEndX()) / 2.0;
            double toEdgeMidY = (toEdge.getStartY() + toEdge.getEndY()) / 2.0;
            return this.length - getExactLength(roadList.get(0), fromEdge)
                    - getExactLength(roadList.get(roadList.size() - 1), toEdge)
                    + Math.hypot(from.getX() - fromEdgeMidX, from.getY() - fromEdgeMidY)
                    + Math.hypot(to.getX() - toEdgeMidX, to.getY() - toEdgeMidY);
        } else {
            return Math.hypot(from.getX() - to.getX(), from.getY() - to.getY());
        }
    }

    /**
     * 返回 Path 上两点间的通行长度
     */
    public double getLength(double fromX, double fromY, double toX, double toY) {
        return this.getLength(new Point2D(fromX, fromY), new Point2D(toX, toY));
    }

    /**
     * 返回组成 Path 的 Areas 的 ID 列表
     */
    public List<EntityID> getIDList() {
        return this.roadList.stream().map(AbstractEntity::getID)
                .collect(Collectors.toList());
    }

    /**
     * 删除 Path 中 [start, end] 中的 Area，start 和 end 都会被删掉. <br>
     * 一般用于删去绕路.
     */
    public void removeRange(int start, int end) {
        for (int i=0; i<=end-start; i++)
            roadList.remove(start);
        this.length = this.getExactLength(roadList);
    }

    /**
     * 截断 Path，返回以 area 为起点的子 Path. <br>
     * 若 area 不是 Path 的一部分，返回 {@code null}
     */
    public Path truncateStart(Area area) {
        int index = roadList.indexOf(area);
        if (index < 0) {
            logger.debug("Can't truncate path " + this + " to new start area " + area + ".");
            return null;
        }
        return new Path(roadList.subList(index, roadList.size()));
    }

    /**
     * 截断 Path，返回以 area 为终点的子 Path. <br>
     * 若 area 不是 Path 的一部分，返回 {@code null}
     */
    public Path truncateEnd(Area area) {
        int index = roadList.indexOf(area);
        if (index < 0) {
            logger.debug("Can't truncate path " + this + " to new end area " + area + ".");
            return null;
        }
        return new Path(roadList.subList(0, index + 1));
    }

    /**
     * 连接两条 Path，重叠 Area 将被删去. <br>
     * <p>
     *     几个例子：
     *     <ul>
     *        <li> this: 1, 2, 3; other: 3, 4, 5 => ret: 1, 2, 3, 4, 5. </li>
     *        <li> this: 1, 2, 3; other: 3, 2, 1, 0 => ret: 1, 0. </li>
     *        <li> this: 0, 1, 2, 3; other: 3, 2, 1 => ret: 0, 1. </li>
     *      </ul>
     * </p>
     */
    public Path link(Path other) {
        if (!this.getEnd().equals(other.getStart())) {
            logger.debug("Can't link path " + this + " with " + other + ".");
            return null;
        }
        Path ret = new Path(this);
        ret.addRoadLast(other.roadList.subList(1, other.roadList.size()));
        // 若存在重叠 Area，则删去
        boolean hasRepeat = false;
        int index = ret.roadList.lastIndexOf(ret.getStart());
        if (index > 0) {
            ret.roadList.subList(0, index).clear();
            hasRepeat = true;
        }
        Area connect = null;
        index = ret.roadList.indexOf(this.getEnd());
        while (true) {
            if (index <= 0 || index >= ret.roadList.size() - 1)
                break;
            Area front = ret.roadList.get(index - 1);
            Area behind = ret.roadList.get(index + 1);
            if (front.equals(behind)) {
                connect = front;
                ret.roadList.remove(index + 1);
                ret.roadList.remove(index - 1);
                index--;
                hasRepeat = true;
            } else break;
        }
        if (connect != null)
            ret.roadList.set(index, connect);
        if (hasRepeat)
            ret.length = getExactLength(ret.roadList);
        return ret;
    }

    /**
     * 向 Path 中添加一个 Road（开头）
     */
    public void addRoadFirst(Area road) {
        Area start = this.getStart();
        this.roadList.addFirst(road);
        this.length += this.getExactLength(road, start);
    }

    /**
     * 向 Path 中添加一个 Road
     */
    public void addRoadLast(Area road) {
        Area end = this.getEnd();
        this.roadList.add(road);
        this.length += this.getExactLength(end, road);
    }

    /**
     * 向 Path 中添加一些 Roads
     */
    public void addRoadLast(List<Area> roads) {
        if (roads.isEmpty())
            return;
        Area end = this.getEnd();
        this.roadList.addAll(roads);
        this.length += this.getExactLength(end, roads.get(0)) +
                this.getExactLength(roads);
    }

    /**
     * 计算 Area 中心到一条 Edge 中点的距离
     */
    public static double getExactLength(Area area, Edge edge) {
        double edgeMidX = (edge.getStartX() + edge.getEndX()) / 2.0;
        double edgeMidY = (edge.getStartY() + edge.getEndY()) / 2.0;
        return Math.hypot(area.getX() - edgeMidX, area.getY() - edgeMidY);
    }

    /**
     * 计算两个相邻 Area 组成的通路的通行距离. <br>
     * 注意: 直接计算两个 Area 中点间的欧式距离是不精确的.
     */
    public static double getExactLength(Area from, Area to) {
        Logger logger =  TestLogger.getLogger(Path.class.getName());
        if (from.equals(to))
            return 0;
        Edge edge = from.getEdgeTo(to.getID());
        if (edge == null) {
            logger.debug("Area " + from + " and " + to + " are not neighbours.");
            return Double.MAX_VALUE;
        }
        return getExactLength(from, edge) + getExactLength(to, edge);
    }

    /**
     * 计算若干 Areas 组成的通路的通行距离.
     */
    public static double getExactLength(List<Area> areas) {
        double length = 0.0;
        Iterator<Area> i = areas.iterator();
        Iterator<Area> j = areas.iterator();
        j.next();
        // 如果 jigsaw 里只有一个 Area
        if (!j.hasNext()) {
            return OptionsTool.AGENT_RADIUS; // TODO 权宜之计
        }
        while (j.hasNext())
            length += getExactLength(i.next(), j.next());
        return length;
    }

//    /**
//     * 计算两个相邻 Area 组成的通路的通行代价.
//     */
//    public static double getPassCost(Area from, Area to, AccessibleTool accessibleTool) {
//        Logger logger =  TestLogger.getLogger(Path.class.getName());
//        if (from.equals(to))
//            return 0;
//        Edge edge = from.getEdgeTo(to.getID());
//        if (edge == null) {
//            logger.debug("Area " + from + " and " + to + " are not neighbours.");
//            return Double.MAX_VALUE;
//        }
//        double ret = getExactLength(from, edge) + getExactLength(to, edge);
//        double impassableRate = accessibleTool.getImpassableRate(from, to);
//        return ret + ret * impassableRate;
//    }
//
//    /**
//     * 计算若干 Areas 组成的通路的通行代价.
//     */
//    public static double getPassCost(List<Area> areas, AccessibleTool accessibleTool) {
//        double length = 0.0;
//        Iterator<Area> i = areas.iterator();
//        Iterator<Area> j = areas.iterator();
//        j.next();
//        while (j.hasNext())
//            length += getPassCost(i.next(), j.next(), accessibleTool);
//        return length;
//    }
//
//    /**
//     * 计算道路的通行代价
//     */
//    public double getPassCost(AccessibleTool accessibleTool) {
//        double pathLength = getLength();
//        List<Area> pathRoads = getRoadList();
//        Iterator<Area> iter1 = pathRoads.iterator();
//        Iterator<Area> iter2 = pathRoads.iterator();
//        iter2.next();
//        double impassableRateSum = 0.0;
//        while (iter2.hasNext()) {
//            impassableRateSum += accessibleTool.getImpassableRate(iter1.next(), iter2.next());
//        }
//        return pathLength + pathLength * (impassableRateSum / pathRoads.size());
//    }

    @Override
    public String toString() {
        return "Path from " + this.getStart() + " to " + this.getEnd() + " : ";
    }
}