package hfutinvincible.debug.renderer;

import adf.core.agent.action.Action;
import adf.core.agent.action.police.ActionClear;
import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.WorldInfo;
import hfutinvincible.Tuple2;
import hfutinvincible.debug.DebugData;
import hfutinvincible.debug.DebugRenderer;
import hfutinvincible.debug.paintable.Paintable;
import rescuecore2.standard.entities.Blockade;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.worldmodel.EntityID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ActionRenderer implements DebugRenderer {

    @Nullable
    @Override
    public Tuple2<String, Collection<Paintable>> render(@Nonnull DebugData debugData) {
        AgentInfo agentInfo = debugData.agentInfo;
        WorldInfo worldInfo = debugData.worldInfo;
        Action action = debugData.action;
        if (action == null) return null;

        List<Paintable> paintables = new ArrayList<>();
        if (action instanceof ActionClear actionClear) {
            if (actionClear.getUseOldFunction()) {
                // 接触清理，将清理的障碍加一个蓝色边
                EntityID blockadeId = actionClear.getTarget();
                assert blockadeId != null;
                StandardEntity entity = worldInfo.getEntity(blockadeId);
                if (entity instanceof Blockade blockade) {
                    Paintable shape = Paintable.shape(blockade.getShape(), Color.BLUE, false);
                    paintables.add(shape);
                }
            } else {
                // 区域清理，蓝色箭头标出清理方向
                int fromX = (int) agentInfo.getX();
                int fromY = (int) agentInfo.getY();
                int dx = actionClear.getPosX();
                int dy = actionClear.getPosY();
                Paintable arrowhead = Paintable.arrowhead(fromX, fromY, dx, dy, 200, Color.BLUE);
                paintables.add(arrowhead);
            }
        }

        return Tuple2.of("Action Ext", paintables);
    }
}
