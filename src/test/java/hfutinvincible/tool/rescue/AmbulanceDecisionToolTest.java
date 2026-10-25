package hfutinvincible.tool.rescue;

import adf.core.agent.action.common.ActionMove;
import adf.core.agent.info.*;
import adf.core.agent.platoon.PlatoonAmbulance;
import hfutinvincible.tool.*;
import hfutinvincible.tool.building.*;
import hfutinvincible.tool.edge.AreaPassableTool;
import hfutinvincible.tool.human.*;
import hfutinvincible.tool.partition.*;
import org.junit.jupiter.api.Test;
import rescuecore2.config.Config;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.*;
import java.util.*;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;
import static hfutinvincible.tool.rescue.AmbulanceDecisionTool.RouteStatus.*;

class AmbulanceDecisionToolTest {
    private static EntityID id(int value) { return new EntityID(value); }

    private static class Fixture {
        final StandardWorldModel model = new StandardWorldModel();
        final WorldInfo world = new WorldInfo(model);
        final Road here = area(new Road(id(1)), 0);
        final AmbulanceTeam me = human(new AmbulanceTeam(id(100)), here);
        final Map<EntityID, Integer> lives = new HashMap<>();
        final Set<EntityID> sector = new HashSet<>();
        final Map<EntityID, Double> costs = new HashMap<>();
        final Set<EntityID> blocked = new HashSet<>(), deferred = new HashSet<>();
        final Map<EntityID, Integer> calls = new HashMap<>();
        long revision;
        int time = 20;
        final AgentInfo agent = new AgentInfo(new PlatoonAmbulance(null, "test", false, false, null, null) {
            @Override public EntityID getID() { return me.getID(); }
        }, model) {
            @Override public long getThinkTimeMillis() { return 0; }
        };
        final AmbulanceDecisionTool planner = new AmbulanceDecisionTool() {
            @Override protected long now() { return 1_000_000; }
            @Override protected Route search(Area from, Area target) {
                calls.merge(target.getID(), 1, Integer::sum);
                if (deferred.contains(target.getID())) return new Route(DEFERRED, List.of(), Double.POSITIVE_INFINITY);
                if (blocked.contains(target.getID())) return new Route(UNREACHABLE, List.of(), Double.POSITIVE_INFINITY);
                return new Route(FOUND, List.of(from.getID(), target.getID()), costs.getOrDefault(target.getID(), 1.0));
            }
        };

        Fixture() {
            world.setChanged(new ChangeSet());
            agent.setTime(time);
            planner.agentInfo = agent; planner.worldInfo = world;
            planner.scenarioInfo = new ScenarioInfo(new Config());
            planner.humanPositionTool = new HumanPositionTool() {
                @Override public Area getMyPositionArea() { return (Area) model.getEntity(me.getPosition()); }
                @Override public EntityID getMyPositionAreaId() { return me.getPosition(); }
                @Override public EntityID getPosition(EntityID human) {
                    Human h = (Human) model.getEntity(human);
                    return h.isPositionDefined() ? h.getPosition() : null;
                }
                @Override public EntityID getPositionAreaId(EntityID human) {
                    EntityID position = getPosition(human);
                    return model.getEntity(position) instanceof AmbulanceTeam at ? at.getPosition() : position;
                }
                @Override public Set<EntityID> getHumanIdsOnArea(EntityID area) {
                    return model.getAllEntities().stream().filter(Human.class::isInstance).map(Human.class::cast)
                            .filter(h -> h.isPositionDefined() && area.equals(getPositionAreaId(h.getID())))
                            .map(Human::getID).collect(Collectors.toSet());
                }
                @Override public int getLastVisitTime(EntityID area) { return 0; }
            };
            planner.timeToDeadTool = new TimeToDeadTool() {
                @Override public Integer getTimeToDeadNowEstimate(EntityID human) { return lives.get(human); }
                @Override public int getUpdateTime(EntityID human) { return time; }
            };
            planner.buriednessTool = new BuriednessTool() {
                @Override public Integer getBuriedness(EntityID human) {
                    Human h = (Human) model.getEntity(human);
                    return h.isBuriednessDefined() ? h.getBuriedness() : null;
                }
            };
            planner.refugeConditionTool = new RefugeConditionTool() {
                @Override public Integer getFreeBedsNum(EntityID refuge) {
                    RefugeRecord record = recordMap.get(refuge);
                    return record.isFull() ? 0 : ((Refuge) model.getEntity(refuge)).getBedCapacity() - record.getOccupiedNumOrWaitingNum();
                }
                @Override public Integer getWaitingListNum(EntityID refuge) {
                    RefugeRecord record = recordMap.get(refuge);
                    return record.isFull() ? record.getOccupiedNumOrWaitingNum() : 0;
                }
            };
            planner.agentTargetAreaTool = new AgentTargetAreaTool();
            planner.unavailableTargetTool = new UnavailableTargetTool();
            planner.areaPassableTool = new AreaPassableTool() {
                @Override public long getRevision() { return revision; }
            };
            planner.staticPartitionTool = new StaticPartitionTool() {
                @Override public Partition<EntityID> getCurrentPartition() { return getAssignedPartition(); }
                @Override public Partition<EntityID> getAssignedPartition() { return new SinglePartition<>(0, sector, here.getID()); }
                @Override public int getCurrentPartitionId() { return 0; }
                @Override public Set<Integer> getNeighborPartition(int partition) { return Set.of(); }
            };
        }

        <T extends Area> T area(T area, int x) {
            area.setX(x); area.setY(0); area.setEdges(List.of()); model.addEntity(area); return area;
        }
        <T extends Human> T human(T human, Area area) {
            human.setPosition(area.getID()); human.setHP(10000); human.setDamage(0); human.setBuriedness(0);
            model.addEntity(human); return human;
        }
        Building building(int value, int x) {
            Building building = area(new Building(id(value)), x); sector.add(building.getID()); return building;
        }
        Refuge refuge(int value, int x, boolean full, int count) {
            Refuge refuge = area(new Refuge(id(value)), x); refuge.setBedCapacity(10);
            RefugeRecord record = new RefugeRecord(); record.setFull(full); record.setOccupiedNumOrWaitingNum(count);
            record.setLastUpdateTime(time); planner.refugeConditionTool.recordMap.put(refuge.getID(), record); return refuge;
        }
        Civilian patient(int value, Area at, int life) {
            Civilian patient = human(new Civilian(id(value)), at); patient.setDamage(20); lives.put(patient.getID(), life); return patient;
        }
        void claim(Human human, Area target, int age) {
            AgentTargetAreaRecord claim = new AgentTargetAreaRecord(); claim.setTargetAreaId(target.getID());
            claim.setLastUpdateTime(time - age); planner.agentTargetAreaTool.recordMap.put(human.getID(), claim);
        }
        void next() { agent.setTime(++time); world.setChanged(new ChangeSet()); }
        void commit(Area target) {
            var route = planner.routeTo(target);
            CalcContext context = new CalcContext(); context.setResultTarget(target);
            context.setResultAction(new ActionMove(route.path())); context.setTargetPath(route.path());
            planner.afterCalc(context);
        }
    }

    @Test void ranksFeasibleUrgentPatientInsteadOfFirstOrHardTwentyStepCutoff() {
        Fixture f = new Fixture(); Refuge refuge = f.refuge(2, 40000, false, 0); f.costs.put(refuge.getID(), 6.0);
        Civilian stable = f.patient(201, f.here, 100), urgent = f.patient(202, f.here, 15), tooLate = f.patient(203, f.here, 7);
        assertEquals(List.of(urgent.getID(), stable.getID()),
                f.planner.rankLoadCandidates(List.of(stable.getID(), tooLate.getID(), urgent.getID())));
        f.planner.deferFailedLoad(urgent.getID());
        assertEquals(List.of(stable.getID()), f.planner.rankLoadCandidates(List.of(urgent.getID(), stable.getID())));
        f.next(); f.next();
        assertEquals(List.of(urgent.getID()), f.planner.rankLoadCandidates(List.of(urgent.getID())));
    }

    @Test void noReachableRefugePreventsHopelessLoadingAndFourthRefugeIsTried() {
        Fixture f = new Fixture(); Civilian p = f.patient(201, f.here, 100);
        assertTrue(f.planner.rankLoadCandidates(List.of(p.getID())).isEmpty());
        for (int i = 2; i <= 5; i++) {
            Refuge r = f.refuge(i, i * 40000, false, 0);
            if (i < 5) f.blocked.add(r.getID());
        }
        f.next();
        assertEquals(List.of(p.getID()), f.planner.rankLoadCandidates(List.of(p.getID())));
        assertEquals(4, f.planner.searchesThisCycle());
    }

    @Test void loadedVehiclePrefersFreeBedsOverNearbyQueueAndIgnoresZeroCapacity() {
        Fixture f = new Fixture(); Civilian p = f.patient(201, f.here, 100); p.setPosition(f.me.getID());
        Refuge full = f.refuge(2, 40000, true, 8), free = f.refuge(3, 80000, false, 0);
        Refuge disabled = f.refuge(4, 100, false, 0); disabled.setBedCapacity(0);
        f.costs.put(full.getID(), 1.0); f.costs.put(free.getID(), 3.0);
        var ranked = f.planner.candidates();
        assertEquals(free, ranked.get(0).e0);
        assertFalse(ranked.stream().anyMatch(pair -> pair.e0.equals(disabled)));
    }

    @Test void urgentKnownPatientOutsideSectorOverridesOldNoHurtMarker() {
        Fixture f = new Fixture(); f.refuge(2, 40000, false, 0);
        Building explore = f.building(3, 40000), urgent = f.building(4, 80000); f.sector.remove(urgent.getID());
        f.patient(201, urgent, 25);
        f.planner.unavailableTargetTool.setUnavailableAndWait(urgent.getID(), UnavailableType.noHurtDefinitely, 1000, false);
        var ranked = f.planner.candidates();
        assertEquals(urgent, ranked.get(0).e0);
        assertEquals(1, ranked.stream().filter(pair -> pair.e0.equals(urgent)).count());
        assertTrue(ranked.stream().anyMatch(pair -> pair.e0.equals(explore)));
    }

    @Test void freshClaimsMatchDemandButExpiredDeadAndLoadedPeersDoNotCompete() {
        Fixture f = new Fixture(); f.refuge(2, 40000, false, 0); Building target = f.building(3, 80000);
        f.patient(201, target, 100);
        AmbulanceTeam peer = f.human(new AmbulanceTeam(id(90)), target); f.claim(peer, target, 0);
        assertFalse(f.planner.isAllowed(target));
        f.patient(202, target, 100); assertTrue(f.planner.isAllowed(target));
        f.model.removeEntity(id(202)); f.claim(peer, target, 6); assertTrue(f.planner.isAllowed(target));
        f.claim(peer, target, 0); peer.setHP(0); assertTrue(f.planner.isAllowed(target));
        peer.setHP(10000); Civilian loaded = f.patient(203, f.here, 100); loaded.setPosition(peer.getID());
        assertTrue(f.planner.isAllowed(target));
    }

    @Test void buriedTargetRequiresFreshFirefighterWorkThatFinishesNearArrival() {
        Fixture f = new Fixture(); f.refuge(2, 40000, false, 0); Building target = f.building(3, 40000);
        Civilian p = f.patient(201, target, 100); p.setBuriedness(4);
        assertTrue(f.planner.candidates().isEmpty());
        FireBrigade fb = f.human(new FireBrigade(id(300)), target); f.claim(fb, target, 0);
        assertEquals(target, f.planner.candidates().get(0).e0);
        f.claim(fb, target, 6); assertTrue(f.planner.candidates().isEmpty());
    }

    @Test void cachedRoutesExpireFromOriginalSearchAndInvalidateOnPassabilityChange() {
        Fixture f = new Fixture(); Building target = f.building(3, 40000);
        f.commit(target); assertEquals(1, f.calls.get(target.getID()));
        f.planner.routeTo(target); assertEquals(1, f.calls.get(target.getID()));
        for (int i = 0; i < 3; i++) { f.next(); f.commit(target); }
        assertEquals(1, f.calls.get(target.getID()));
        f.next(); f.commit(target); assertEquals(2, f.calls.get(target.getID()));
        f.next(); f.revision++; f.commit(target); assertEquals(3, f.calls.get(target.getID()));
    }

    @Test void changedPathAreaAndLoadedVictimsInvalidateOldAssumptions() {
        Fixture f = new Fixture(); Building target = f.building(3, 40000);
        f.commit(target); f.next();
        ChangeSet changed = new ChangeSet(); changed.addChange(target, target.getXProperty());
        f.world.setChanged(changed); f.planner.routeTo(target);
        assertEquals(2, f.calls.get(target.getID()));
        f.refuge(2, 80000, false, 0); Civilian p = f.patient(201, target, 100);
        AmbulanceTeam peer = f.human(new AmbulanceTeam(id(90)), target); p.setPosition(peer.getID());
        assertTrue(f.planner.rankLoadCandidates(List.of(p.getID())).isEmpty());
    }

    @Test void sameArrivalUsesIdTieBreakAndUnreachableSearchPublishesFirstBlockObservation() {
        Fixture f = new Fixture(); f.refuge(2, 40000, false, 0); Building target = f.building(3, 80000);
        f.patient(201, target, 100);
        AmbulanceTeam peer = f.human(new AmbulanceTeam(id(90)), f.here); f.claim(peer, target, 0);
        assertFalse(f.planner.isAllowed(target));
        f.blocked.add(target.getID()); assertEquals(UNREACHABLE, f.planner.routeTo(target).status());
        assertTrue(f.planner.unavailableTargetTool.isUnavailableTargetISee(target.getID()));
    }

    @Test void budgetIsSharedAcrossCandidatesAndCachedRoutesStillWorkAfterExhaustion() {
        Fixture f = new Fixture();
        for (int i = 2; i < 12; i++) f.building(i, i * 40000);
        for (int i = 2; i < 10; i++) assertEquals(FOUND, f.planner.routeTo((Area) f.model.getEntity(id(i))).status());
        assertEquals(8, f.planner.searchesThisCycle());
        assertEquals(DEFERRED, f.planner.routeTo((Area) f.model.getEntity(id(10))).status());
        assertEquals(FOUND, f.planner.routeTo((Area) f.model.getEntity(id(2))).status());
        assertFalse(f.planner.unavailableTargetTool.isUnavailable(id(10), UnavailableType.block));
        f.next(); assertEquals(FOUND, f.planner.routeTo((Area) f.model.getEntity(id(10))).status());
    }

    @Test void commitmentKeepsSmallGainButSwitchesForMaterialGainAndInvalidity() {
        Fixture f = new Fixture(); Civilian p = f.patient(201, f.here, 100); p.setPosition(f.me.getID());
        Refuge old = f.refuge(2, 40000, false, 0), other = f.refuge(3, 80000, false, 0);
        f.costs.put(old.getID(), 10.0); f.costs.put(other.getID(), 9.0); f.commit(old);
        f.next(); assertEquals(old, f.planner.candidates().get(0).e0);
        f.costs.put(other.getID(), 4.0); f.next(); assertEquals(other, f.planner.candidates().get(0).e0);
        f.planner.unavailableTargetTool.setUnavailableAndWait(old.getID(), UnavailableType.block, 10, false);
        f.next(); assertEquals(other, f.planner.candidates().get(0).e0);
    }
}
