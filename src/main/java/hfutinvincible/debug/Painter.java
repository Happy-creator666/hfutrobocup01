package hfutinvincible.debug;

import hfutinvincible.debug.paintable.Paintable;

import java.util.Collection;

public interface Painter<T> {

    /**
     * 空 Painter, 其 addPaintables 可以被调用，但什么都不会做
     */
    Painter<?> EMPTY = new Painter<Object>() {
        @Override
        public Object addPaintables(String layerName, boolean persist, Paintable... ps) {
            return EMPTY;
        }

        @Override
        public Object addPaintables(String layerName, boolean persist, Collection<Paintable> ps) {
            return EMPTY;
        }
    };

    T addPaintables(String layerName, boolean persist, Paintable... ps);

    T addPaintables(String layerName, boolean persist, Collection<Paintable> ps);
}
