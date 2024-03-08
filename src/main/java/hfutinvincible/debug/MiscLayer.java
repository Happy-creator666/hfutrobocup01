package hfutinvincible.debug;

import hfutinvincible.common.OrderedTuple;
import hfutinvincible.debug.paintable.Paintable;
import rescuecore2.misc.gui.ScreenTransform;
import rescuecore2.standard.view.StandardViewLayer;
import rescuecore2.view.RenderedObject;

import javax.annotation.Nonnull;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.Line2D;
import java.awt.geom.PathIterator;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public abstract class MiscLayer extends StandardViewLayer {

    private final List<Paintable> paintableList;

    private final Map<Paintable, Paintable> persistSet;

    public MiscLayer() {
        paintableList = new Vector<>();
        persistSet = new ConcurrentHashMap<>();
    }

    /////////////////////////////////////////////

    @Override
    public Collection<RenderedObject> render(Graphics2D graphics2D, ScreenTransform screenTransform, int i, int i1) {
        for (Paintable p : paintableList) {
            p.paint(graphics2D, screenTransform);
        }
        return new ArrayList<>();
    }

    /**
     * 添加一个 {@link Paintable}
     */
    public void addPaintable(@Nonnull Paintable p) {
        paintableList.add(p);
    }

    /**
     * 设置一个 {@link Paintable} 为 persist
     */
    public void setPersist(@Nonnull Paintable p) {
        persistSet.put(p, p);
    }

    /**
     * 从图层中删除所有 non-persist 的 {@link Paintable}s
     */
    public void removeNonePersist() {
        paintableList.removeIf(p -> !persistSet.containsKey(p));
    }
}