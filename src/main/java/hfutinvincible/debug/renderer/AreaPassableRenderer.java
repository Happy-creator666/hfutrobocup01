package hfutinvincible.debug.renderer;

import hfutinvincible.Tuple2;
import hfutinvincible.debug.DebugData;
import hfutinvincible.debug.DebugRenderer;
import hfutinvincible.debug.paintable.Paintable;
import hfutinvincible.tool.edge.HFUTEdge;
import rescuecore2.misc.Pair;
import rescuecore2.misc.geometry.Point2D;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.*;
import java.util.*;
import java.util.List;

public class AreaPassableRenderer implements DebugRenderer {
    @Nullable
    @Override
    public Tuple2<String, Collection<Paintable>> render(@Nonnull DebugData debugData) {
        Map<Pair<Point2D,Point2D>, Color> allEdgePassability = debugData.allEdgePassability;
        if (allEdgePassability == null) return null;
        List<Paintable> paintables = new ArrayList<>();
        Set<Pair<Point2D, Point2D>> passablitypairs = allEdgePassability.keySet();
        for (Pair<Point2D, Point2D> passablitypair : passablitypairs) {
            Paintable line = Paintable.line(passablitypair.first(),passablitypair.second(),allEdgePassability.get(passablitypair));
            paintables.add(line);
        }
        return Tuple2.of("AreaPassableRenderer",paintables);
    }
}
