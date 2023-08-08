package hfutinvincible.debug;

import hfutinvincible.debug.paintable.Paintable;

@FunctionalInterface
public interface Painter<T> {

    /**
     * 空 Painter, 其 addPaintables 可以被调用，但什么都不会做
     */
    Painter<?> EMPTY = (Painter<Object>) (layerName, persist, ps) -> Painter.EMPTY;

    T addPaintables(String layerName, boolean persist, Paintable... ps);
}
