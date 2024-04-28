package hfutinvincible.debug;

import hfutinvincible.debug.paintable.Paintable;
import rescuecore2.misc.gui.ScreenTransform;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.view.RenderedObject;
import rescuecore2.view.ViewComponent;
import rescuecore2.view.ViewLayer;
import rescuecore2.view.ViewListener;
import rescuecore2.worldmodel.EntityID;

import javax.annotation.Nonnull;
import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class AnimatedWorldViewer extends StandardWorldModelViewer implements Painter<AnimatedWorldViewer> {
    private static final int FRAME_COUNT = 10;
    private static final int ANIMATION_TIME = 750;
    private static final int FRAME_DELAY = ANIMATION_TIME / FRAME_COUNT;

    public Map<String, Object> layers;

    private Timer timer;
    private final Object lock = new Object();
    private boolean done;

    public AnimatedWorldViewer(Consumer<EntityID> onClick) {
        this.addViewListener(new ViewListener() {
            @Override
            public void objectsClicked(ViewComponent viewComponent, java.util.List<RenderedObject> list) {
                for (RenderedObject ro : list) {
                    Object o = ro.getObject();
                    if (o instanceof StandardEntity e) {
                        onClick.accept(e.getID());
                        break;
                    }
                }
            }

            @Override
            public void objectsRollover(ViewComponent viewComponent, java.util.List<RenderedObject> list) {

            }
        });
        layers = new HashMap<>();
        layers.put("Animated Human Layer", new AnimatedHumanLayer());
        timer = new Timer(FRAME_DELAY, e -> {
            synchronized (lock) {
                if (done) {
                    return;
                }
                done = true;
                AnimatedHumanLayer layer = getLayer("Animated Human Layer");
                if (layer != null && layer.nextFrame()) {
                    done = false;
                    repaint();
                }
            }
        });
        timer.setRepeats(true);
        timer.start();
    }

    @Override
    public String getViewerName() {
        return "Animated world model viewer";
    }

    @SuppressWarnings("unchecked")
    private <T> T getLayer(String layerName) {
        return (T) this.layers.get(layerName);
    }

    @Override
    public synchronized void view(Object... objects) {
        super.view(objects);
        synchronized (lock) {
            this.done = false;
            AnimatedHumanLayer layer = getLayer("Animated Human Layer");
            if (layer != null)
                layer.computeAnimation(FRAME_COUNT);
        }
    }

    private MiscLayer getOrCreateMiscLayer(@Nonnull String layerName) {
        Object layer = layers.get(layerName);
        if (layer == null) {
//            System.out.println("create new layer " + layerName);
            // 不存在这个名字的 layer，创建新的
            MiscLayer miscLayer = new MiscLayer() {
                @Override
                public String getName() {
                    return layerName;
                }
            };
            layers.put(layerName, miscLayer);
            addLayer(miscLayer);
            miscLayer.setVisible(true);
            return miscLayer;
        } else if (layer instanceof MiscLayer) { // 已经存在
            return (MiscLayer) layer;
        } else {
            // 只允许获得 MiscLayer
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public synchronized AnimatedWorldViewer addPaintables(String layerName, boolean persist, Paintable... ps) {
        MiscLayer miscLayer = getOrCreateMiscLayer(layerName);
        for (Paintable p : ps) {
            miscLayer.addPaintable(p);
            if (persist)
                miscLayer.setPersist(p);
        }
        return this;
    }

    @Override
    public AnimatedWorldViewer addPaintables(String layerName, boolean persist, Collection<Paintable> ps) {
        MiscLayer miscLayer = getOrCreateMiscLayer(layerName);
        for (Paintable p : ps) {
            miscLayer.addPaintable(p);
            if (persist)
                miscLayer.setPersist(p);
        }
        return this;
    }

    public synchronized AnimatedWorldViewer removeAllNonPersistPaintables() {
        for (Object layer : layers.values()) {
            if (layer instanceof MiscLayer miscLayer) {
                miscLayer.removeNonePersist();
            }
        }
        return this;
    }

    public AnimatedWorldViewer removeNonPersistPaintables(String layerName) {
        Object layer = layers.get(layerName);
        if (layer instanceof MiscLayer miscLayer) {
            miscLayer.removeNonePersist();
        }
        return this;
    }
}