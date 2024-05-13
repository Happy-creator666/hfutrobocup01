package hfutinvincible.debug.renderer;

import hfutinvincible.Tuple2;
import hfutinvincible.debug.DebugData;
import hfutinvincible.debug.DebugRenderer;
import hfutinvincible.debug.paintable.Paintable;
import hfutinvincible.tool.misc.OptionsTool;
import rescuecore2.standard.entities.Area;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class TargetPathRenderer implements DebugRenderer {

    @Nullable
    @Override
    public Tuple2<String, Collection<Paintable>> render(@Nonnull DebugData debugData) {
        List<Area> targetPathAreas = debugData.targetPath;
        if (targetPathAreas == null) {
            return null;
        }
        List<Paintable> paintables = new ArrayList<>();
        for (Area area : targetPathAreas) {
            Paintable p = Paintable.shape(area.getShape(), OptionsTool.T_RED, true);
            paintables.add(p);
        }
        return Tuple2.of("Pf targetPath", paintables);
    }
}
