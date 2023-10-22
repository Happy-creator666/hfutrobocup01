package hfutinvincible.tool.partition;

import hfutinvincible.tool.misc.HFUTGeometryTool;
import rescuecore2.misc.geometry.Point2D;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * 凸包计算，使用 Graham Scan 算法
 *
 * @see <a href="http://www.rosettacode.org/wiki/Convex_hull#Java">Rosetta Code | Convex Hull</a>
 * @author stardust
 */
public abstract class ConvexHullTool<E> {

    private List<E> elements;
    private List<E> convexHull;

    public ConvexHullTool(Collection<E> elements) {
        this.elements = new ArrayList<>(elements);
        this.convexHull = new ArrayList<>(elements.size());
    }

    public ConvexHullTool<E> calc() {
        if (this.elements.isEmpty()) return this;

        this.elements.sort(Comparator.comparingInt(o -> (int) getCenter(o).getX()));

        // lower hull
        for (E e : this.elements) {
            int N = this.convexHull.size();
            while (N >= 2 &&
                    !HFUTGeometryTool.isCounterClockwise(
                            getCenter(this.convexHull.get(N - 2)),
                            getCenter(this.convexHull.get(N - 1)),
                            getCenter(e))) {
                this.convexHull.remove(N - 1);
                N = this.convexHull.size();
            }
            this.convexHull.add(e);
        }

        // upper hull
        int T = this.convexHull.size() + 1;
        for (int i = this.elements.size() - 1; i >= 0; i--) {
            E e = this.elements.get(i);
            int N = this.convexHull.size();
            while (N >= T && !HFUTGeometryTool.isCounterClockwise(
                    getCenter(this.convexHull.get(N - 2)),
                    getCenter(this.convexHull.get(N - 1)),
                    getCenter(e))) {
                this.convexHull.remove(N - 1);
                N = this.convexHull.size();
            }
            this.convexHull.add(e);
        }

        this.convexHull.remove(this.convexHull.size() - 1);
        return this;
    }

    public List<E> getConvexHull() {
        if (this.convexHull.isEmpty())
            this.calc();
        return this.convexHull;
    }

    protected abstract Point2D getCenter(E e);
}