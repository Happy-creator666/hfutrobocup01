package hfutinvincible.tool.path;

import adf.core.agent.info.AgentInfo;
import adf.core.agent.platoon.PlatoonAmbulance;
import hfutinvincible.tool.edge.*;
import org.apache.log4j.Logger;
import org.junit.jupiter.api.Test;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;
import java.util.*;
import java.util.function.BiFunction;
import static org.junit.jupiter.api.Assertions.*;

class SearchPathToolTest {
    private HFUTEdge edge(int x, int y) { return new HFUTEdge(new Edge(x,y-1000,x,y+1000)); }

    @Test void defaultHeuristicTargetsNearestGoalInsteadOfGoalCentroid() {
        HFUTEdge start = edge(-30000,0), detour = edge(0,0), near = edge(10000,0), far = edge(1000000,0);
        Map<HFUTEdge,List<HFUTEdge>> adjacency = Map.of(start,List.of(detour,near),detour,List.of(near));
        BiFunction<HFUTEdge,HFUTEdge,Double> costs = (a,b) -> {
            if (a.equals(start) && b.equals(near)) return 50000.0;
            return a.equals(start) ? 24000.0 : 8000.0;
        };
        SearchPathTool tool = new SearchPathTool(); tool.logger = Logger.getLogger("path-regression-test");
        AmbulanceTeam me = new AmbulanceTeam(new EntityID(1));
        tool.agentInfo = new AgentInfo(new PlatoonAmbulance(null,"test",false,false,null,null),new StandardWorldModel()) {
            @Override public StandardEntity me() { return me; }
        };
        tool.areaPassableTool = new AreaPassableTool() {
            @Override public Set<HFUTEdge> getAdjacentEdges(HFUTEdge edge) { return new HashSet<>(adjacency.getOrDefault(edge,List.of())); }
            @Override public BiFunction<HFUTEdge,HFUTEdge,Double> getEdgePairPassCostEvaluatorByType(Class<? extends StandardEntity> type) { return costs; }
        };
        tool.setFromAndTo(start, Set.of(near,far)).calc();
        assertEquals(List.of(start,detour,near),tool.getEdgePath());
        tool.setFromAndTo(start, Set.of(near,far))
                .setOneStepCostEvaluator((a,b) -> a.equals(start) && b.equals(near) ? 3.0 : 1.0).calc();
        assertEquals(List.of(start,detour,near),tool.getEdgePath(), "custom costs must reset the heuristic lower bound");
        tool.setFromAndTo(start, Set.of(near,far)).calc(0,Long.MAX_VALUE);
        assertTrue(tool.isBudgetExceeded()); assertNull(tool.getEdgePath());
        tool.setFromAndTo(start, Set.of(near,far)).calc();
        assertFalse(tool.isBudgetExceeded());
    }
}
