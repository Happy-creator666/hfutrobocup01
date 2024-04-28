package hfutinvincible.debug.renderer;

import hfutinvincible.Tuple2;
import hfutinvincible.debug.DebugData;
import hfutinvincible.debug.DebugRenderer;
import hfutinvincible.debug.paintable.Paintable;
import hfutinvincible.tool.misc.OptionsTool;
import rescuecore2.standard.entities.Area;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class IsCandidateRenderer implements DebugRenderer {

    @Nullable
    @Override
    public Tuple2<String, Collection<Paintable>> render(@Nonnull DebugData debugData) {
        var ss = debugData.sortedTargetsAndScores;
        if (ss == null) return null;

        List<Paintable> paintables = new ArrayList<>();
        Color color = OptionsTool.T_RED;
        for (int i = 0; i < ss.size(); i ++) {
            Area area = ss.get(i).e0;
            Paintable shape = Paintable.shape(area.getShape(), color, true);
            paintables.add(shape);
        }
        return Tuple2.of("Is Candidate", paintables);
    }
}
