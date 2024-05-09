package hfutinvincible.debug.renderer;

import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.WorldInfo;
import hfutinvincible.Tuple2;
import hfutinvincible.debug.DebugData;
import hfutinvincible.debug.DebugRenderer;
import hfutinvincible.debug.paintable.Paintable;
import rescuecore2.standard.entities.FireBrigade;
import rescuecore2.standard.entities.PoliceForce;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class MyTureSizeRenderer implements DebugRenderer {
    @Nullable
    @Override
    public Tuple2<String, Collection<Paintable>> render(@Nonnull DebugData debugData) {
        AgentInfo agentInfo = debugData.agentInfo;

        if (agentInfo == null) return null;

        // 绘制我方agent的半径
        List<Paintable> paintables = new ArrayList<>();
        if (agentInfo.me() instanceof PoliceForce){
            Paintable pie = Paintable.pie(agentInfo.getX(),agentInfo.getY(),600,Paintable.addAlpha(Color.blue,50));
            paintables.add(pie);
        } else if (agentInfo.me() instanceof FireBrigade) {
            Paintable pie = Paintable.pie(agentInfo.getX(),agentInfo.getY(),600,Paintable.addAlpha(Color.RED,50));
            paintables.add(pie);
        }
        else {
            Paintable pie = Paintable.pie(agentInfo.getX(),agentInfo.getY(),600,Paintable.addAlpha(Color.WHITE,50));
            paintables.add(pie);
        }
        Paintable text = Paintable.text(agentInfo.getX(),agentInfo.getY(),"It's my debug",Color.pink);
        paintables.add(text);
        return Tuple2.of("My True Size ",paintables);
    }
}
