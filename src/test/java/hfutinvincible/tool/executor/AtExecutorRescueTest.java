package hfutinvincible.tool.executor;

import adf.core.agent.action.ambulance.*;
import adf.core.agent.action.common.*;
import adf.core.agent.info.*;
import adf.core.agent.platoon.PlatoonAmbulance;
import hfutinvincible.Tuple2;
import hfutinvincible.tool.*;
import hfutinvincible.tool.human.*;
import hfutinvincible.tool.mediator.AtMediator;
import hfutinvincible.tool.path.PathUtilTool;
import hfutinvincible.tool.rescue.AmbulanceDecisionTool;
import hfutinvincible.tool.targetprovider.AtTargetProvider;
import org.apache.log4j.Logger;
import org.junit.jupiter.api.Test;
import rescuecore2.config.Config;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static hfutinvincible.tool.rescue.AmbulanceDecisionTool.RouteStatus.*;

class AtExecutorRescueTest {
    private static class Fixture {
        final StandardWorldModel model = new StandardWorldModel();
        final Road road = new Road(new EntityID(1));
        final Refuge refuge = new Refuge(new EntityID(2));
        final AmbulanceTeam me = new AmbulanceTeam(new EntityID(100));
        final Civilian patient = new Civilian(new EntityID(200));
        final AtExecutor executor = new AtExecutor();
        final Set<EntityID> humans = new HashSet<>();
        final List<Tuple2<Area, Double>> targets = new ArrayList<>();
        final Map<EntityID, AmbulanceDecisionTool.RouteStatus> statuses = new HashMap<>();
        Fixture() {
            for (Area area : List.of(road, refuge)) {
                area.setX(area.getID().getValue() * 40000); area.setY(0);
                area.setEdges(List.of(new Edge(0,0,1000,0),new Edge(1000,0,0,1000),new Edge(0,1000,0,0)));
                model.addEntity(area);
            }
            me.setPosition(road.getID()); patient.setPosition(road.getID()); patient.setHP(500); patient.setDamage(30); patient.setBuriedness(0);
            model.addEntity(me); model.addEntity(patient);
            humans.add(me.getID()); humans.add(patient.getID());
            AgentInfo agent = new AgentInfo(new PlatoonAmbulance(null,"test",false,false,null,null) {
                @Override public EntityID getID() { return me.getID(); }
            }, model) {
                @Override public double getX() { return road.getX(); }
                @Override public double getY() { return road.getY(); }
            };
            agent.setTime(20); executor.agentInfo = agent;
            executor.logger = Logger.getLogger("rescue-regression-test");
            executor.worldInfo = new WorldInfo(model); executor.worldInfo.setChanged(new ChangeSet());
            Config config = new Config(); config.setIntValue("kernel.agents.ignoreuntil", 0);
            executor.scenarioInfo = new ScenarioInfo(config);
            executor.unavailableTargetTool = new UnavailableTargetTool();
            executor.humanPositionTool = new HumanPositionTool() {
                @Override public Area getMyPositionArea() { return (Area) model.getEntity(me.getPosition()); }
                @Override public EntityID getMyPositionAreaId() { return me.getPosition(); }
                @Override public Set<EntityID> getHumanIdsOnArea(EntityID area) { return humans; }
                @Override public EntityID getPosition(EntityID id) { return ((Human) model.getEntity(id)).getPosition(); }
            };
            executor.timeToDeadTool = new TimeToDeadTool() {
                @Override public Integer getTimeToDeadNowEstimate(EntityID id) { return 15; }
                @Override public InfoSource getInfoSource(EntityID id) { return InfoSource.WITNESS; }
            };
            executor.buriednessTool = new BuriednessTool() {
                @Override public Integer getBuriedness(EntityID id) { return 0; }
            };
            executor.decisionTool = new AmbulanceDecisionTool() {
                @Override public List<EntityID> rankLoadCandidates(Collection<EntityID> ids) {
                    assertTrue(ids.contains(patient.getID()), "executor must not discard patients below the old 20-step cutoff");
                    return List.of(patient.getID());
                }
                @Override public Route routeTo(Area target) {
                    return new Route(statuses.getOrDefault(target.getID(), FOUND), List.of(me.getPosition(), target.getID()), 1);
                }
                @Override public boolean exhausted() { return true; }
            };
            executor.pathUtilTool = new PathUtilTool() {
                @Override public EntityID getFirstImpassableAreaIdOfAreaIdPath(int x, int y, List<EntityID> path) { return null; }
            };
            executor.targetProvider = new AtTargetProvider() {
                @Override public List<Tuple2<Area,Double>> getSortedCandidatesAndScores() { return targets; }
            };
            executor.atMediator = new AtMediator() {
                @Override public boolean isAllowed(Area target) { return true; }
            };
        }
    }

    @Test void shortLifePatientReachesPlannerAndLoadingDoesNotMutatePositionIndex() {
        Fixture f = new Fixture(); CalcContext context = new CalcContext(); f.executor.calc(context);
        assertEquals(f.patient.getID(), assertInstanceOf(ActionLoad.class, context.getResultAction()).getTarget());
        assertTrue(f.humans.contains(f.me.getID()));
        assertEquals(f.road.getID(), f.patient.getPosition());
    }

    @Test void unconfirmedUnloadIsRetriedWithoutInventingSuccessfulPosition() {
        Fixture f = new Fixture(); f.me.setPosition(f.refuge.getID()); f.patient.setPosition(f.me.getID());
        f.executor.agentInfo.setExecutedAction(-1, new ActionUnload());
        CalcContext context = new CalcContext(); f.executor.calc(context);
        assertInstanceOf(ActionUnload.class, context.getResultAction());
        assertEquals(f.me.getID(), f.patient.getPosition());
    }

    @Test void deferredSearchDoesNotMarkBlockedButConfirmedUnreachableDoes() {
        Fixture f = new Fixture(); CalcContext context = new CalcContext();
        f.statuses.put(f.refuge.getID(), DEFERRED);
        f.executor.getAction(f.road, f.refuge, context, Set.of());
        assertNull(context.getResultAction());
        assertFalse(f.executor.unavailableTargetTool.isUnavailable(f.refuge.getID(), UnavailableType.block));
        f.statuses.put(f.refuge.getID(), UNREACHABLE);
        f.executor.getAction(f.road, f.refuge, context, Set.of());
        assertTrue(f.executor.unavailableTargetTool.isUnavailable(f.refuge.getID(), UnavailableType.block));
    }

    @Test void exhaustedSearchStillUsesLaterCachedRouteAndRestFallbackCannotOverrideTransport() {
        Fixture f = new Fixture(); f.patient.setPosition(f.me.getID());
        Refuge first = new Refuge(new EntityID(3)); first.setX(0); first.setY(0); first.setEdges(List.of()); f.model.addEntity(first);
        f.targets.add(Tuple2.of(first, 1.0)); f.targets.add(Tuple2.of(f.refuge, 2.0));
        f.statuses.put(first.getID(), DEFERRED);
        for (int i = 1; i <= 3; i++) f.executor.agentInfo.setExecutedAction(-i, new ActionRest());
        CalcContext context = new CalcContext(); f.executor.calc(context);
        assertInstanceOf(ActionMove.class, context.getResultAction()); assertEquals(f.refuge, context.getTarget());
    }
}
