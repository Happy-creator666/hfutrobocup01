package hfutinvincible.tool.mediator;

import hfutinvincible.anno.AgentType;
import hfutinvincible.anno.Autowired;
import hfutinvincible.anno.HfutTool;
import hfutinvincible.tool.LifeCycle;
import hfutinvincible.tool.rescue.AmbulanceDecisionTool;
import rescuecore2.standard.entities.AmbulanceTeam;
import rescuecore2.standard.entities.Area;
import javax.annotation.Nonnull;

@HfutTool
@AgentType({AmbulanceTeam.class})
public class AtMediator implements LifeCycle, Mediator<Area> {
    @Autowired AmbulanceDecisionTool decisionTool;

    @Override
    public boolean isAllowed(@Nonnull Area target) {
        return decisionTool.isAllowed(target);
    }
}
