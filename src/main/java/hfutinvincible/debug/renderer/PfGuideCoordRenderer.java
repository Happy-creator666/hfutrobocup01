package hfutinvincible.debug.renderer;

import adf.core.agent.info.AgentInfo;
import hfutinvincible.Tuple2;
import hfutinvincible.debug.DebugData;
import hfutinvincible.debug.DebugRenderer;
import hfutinvincible.debug.paintable.Paintable;
import org.locationtech.jts.geom.Coordinate;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class PfGuideCoordRenderer implements DebugRenderer {
    @Nullable
    @Override
    public Tuple2<String, Collection<Paintable>> render(@Nonnull DebugData debugData) {
        AgentInfo agentInfo = debugData.agentInfo;
        Coordinate guidecoord = debugData.guideCoord;
        if(guidecoord == null) return null;
        // 绘制目标点
        List<Paintable> paintables = new ArrayList<>();
        Paintable cross = Paintable.pointcross(guidecoord.getX(), guidecoord.getY(), 500, Color.RED);
        paintables.add(cross);
        return Tuple2.of("Pf GuideCoord ",paintables);
    }
}
