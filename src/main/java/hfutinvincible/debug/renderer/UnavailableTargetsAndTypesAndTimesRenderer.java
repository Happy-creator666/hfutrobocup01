package hfutinvincible.debug.renderer;

import adf.core.agent.info.WorldInfo;
import hfutinvincible.Tuple2;
import hfutinvincible.debug.DebugData;
import hfutinvincible.debug.DebugRenderer;
import hfutinvincible.debug.paintable.Paintable;
import hfutinvincible.tool.UnavailableType;
import rescuecore2.standard.entities.Area;
import rescuecore2.worldmodel.EntityID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class UnavailableTargetsAndTypesAndTimesRenderer implements DebugRenderer {

    @Nullable
    @Override
    public Tuple2<String, Collection<Paintable>> render(@Nonnull DebugData debugData) {
        var data = debugData.unavailableTargetsAndTypesAndTimes;
        if (data == null) return null;

        WorldInfo worldInfo = debugData.worldInfo;

        List<Paintable> paintables = new ArrayList<>();
        data.forEach(t -> {
            EntityID id = t.e0;
            UnavailableType type = t.e1;
            int waitTime = t.e2;
            Area area = (Area) worldInfo.getEntity(id);
            assert area != null;
            String text = type.name() + "(" + waitTime + ")";
            Paintable cross = Paintable.pointcross(area.getX(), area.getY(), 200, Color.BLACK);
            Paintable hint = Paintable.text(area.getX() + 200, area.getY() + 200, text, Color.BLACK);
            paintables.add(cross);
            paintables.add(hint);
        });

        return Tuple2.of("Unavailable Targets", paintables);
    }
}
