package hfutinvincible.debug;

import rescuecore2.standard.view.StandardWorldModelViewer;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

public class AnimatedWorldViewer extends StandardWorldModelViewer {
    private static final int FRAME_COUNT = 10;
    private static final int ANIMATION_TIME = 750;
    private static final int FRAME_DELAY = ANIMATION_TIME / FRAME_COUNT;

    public Map<Class<?>, Object> layers;

    private Timer timer;
    private final Object lock = new Object();
    private boolean done;

    public AnimatedWorldViewer() {
        this.layers = Map.of(
                AnimatedHumanLayer.class, new AnimatedHumanLayer()
        );
        timer = new Timer(FRAME_DELAY, e -> {
            synchronized (lock) {
                if (done) {
                    return;
                }
                done = true;
                AnimatedHumanLayer layer = getLayer(AnimatedHumanLayer.class);
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
    private <T> T getLayer(Class<T> layerClass) {
        return (T) this.layers.get(layerClass);
    }

    @Override
    public synchronized void view(Object... objects) {
        super.view(objects);
        synchronized (lock) {
            this.done = false;
            AnimatedHumanLayer layer = getLayer(AnimatedHumanLayer.class);
            if (layer != null)
                layer.computeAnimation(FRAME_COUNT);
        }
    }
}