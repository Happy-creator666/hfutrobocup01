package hfutinvincible.tool.targetprovider;

import hfutinvincible.Tuple2;
import hfutinvincible.anno.AgentType;
import hfutinvincible.anno.Autowired;
import hfutinvincible.anno.HfutTool;
import hfutinvincible.tool.LifeCycle;
import hfutinvincible.tool.rescue.AmbulanceDecisionTool;
import rescuecore2.standard.entities.AmbulanceTeam;
import rescuecore2.standard.entities.Area;
import java.util.List;

@HfutTool
@AgentType({AmbulanceTeam.class})
public class AtTargetProvider implements LifeCycle, TargetProvider<Area> {
    @Autowired AmbulanceDecisionTool decisionTool;

    @Override
    public List<Tuple2<Area, Double>> getSortedCandidatesAndScores() {
        return decisionTool.candidates();
    }
}
