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

public abstract class MiscLayer extends StandardViewLayer {

    private final List<Paintable> paintableList;

    private final Set<Paintable> persistSet;

    public MiscLayer() {
        paintableList = new ArrayList<>();
        persistSet = new HashSet<>();
    }

    /////////////////////////////////////////////

    @Override
    public Collection<RenderedObject> render(Graphics2D graphics2D, ScreenTransform screenTransform, int i, int i1) {
        for (Paintable p : paintableList) {
            System.out.println(p);
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
        persistSet.add(p);
    }

    /**
     * 删除所有不 persist 的 {@link Paintable}
     */
    public void clear() {
        paintableList.removeIf(p -> !persistSet.contains(p));
    }
}