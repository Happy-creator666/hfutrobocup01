package hfutinvincible.debug.renderer;

import hfutinvincible.Tuple2;
import hfutinvincible.debug.DebugData;
import hfutinvincible.debug.DebugRenderer;
import hfutinvincible.debug.paintable.Paintable;
import rescuecore2.standard.entities.Area;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class TargetScoreRenderer implements DebugRenderer {

    @Nullable
    @Override
    public Tuple2<String, Collection<Paintable>> render(@Nonnull DebugData debugData) {
        var ss = debugData.sortedTargetsAndScores;
        if (ss == null) return null;

        List<Paintable> paintables = new ArrayList<>();
        for (int i = 0; i < ss.size(); i ++) {
            Area area = ss.get(i).e0;
            double score = ss.get(i).e1;
            Color color = i <= 5 ? Color.RED : Color.ORANGE;
            // 保留后两位， 四舍五入
            Paintable text = Paintable.text(area.getX() +50, area.getY() + 150, String.valueOf(String.format("%.2f", score)), color);
            paintables.add(text);
        }

        return Tuple2.of("Target Score", paintables);
    }
}
