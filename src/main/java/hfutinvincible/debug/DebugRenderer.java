package hfutinvincible.debug;

import hfutinvincible.debug.paintable.Paintable;

import javax.annotation.Nonnull;
import java.util.Collection;

public interface DebugRenderer {
    Collection<Paintable> render(@Nonnull DebugData debugData);
}
