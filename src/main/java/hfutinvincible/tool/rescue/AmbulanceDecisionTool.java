package hfutinvincible.tool.rescue;

import adf.core.agent.info.*;
import hfutinvincible.Tuple2;
import hfutinvincible.anno.*;
import hfutinvincible.tool.*;
import hfutinvincible.tool.building.*;
import hfutinvincible.tool.edge.AreaPassableTool;
import hfutinvincible.tool.human.*;
import hfutinvincible.tool.misc.OptionsTool;
import hfutinvincible.tool.partition.*;
import hfutinvincible.tool.path.SearchPathTool;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;
import java.util.*;
import java.util.stream.Collectors;

/** Per-agent planning using only this agent's world model and received claim messages. */
@HfutTool
@AgentType({AmbulanceTeam.class})
public class AmbulanceDecisionTool implements LifeCycle {
    @Autowired AgentInfo agentInfo;
    @Autowired WorldInfo worldInfo;
    @Autowired ScenarioInfo scenarioInfo;
    @Autowired HumanPositionTool humanPositionTool;
    @Autowired TimeToDeadTool timeToDeadTool;
    @Autowired BuriednessTool buriednessTool;
    @Autowired RefugeConditionTool refugeConditionTool;
    @Autowired AgentTargetAreaTool agentTargetAreaTool;
    @Autowired StaticPartitionTool staticPartitionTool;
    @Autowired UnavailableTargetTool unavailableTargetTool;
    @Autowired AreaPassableTool areaPassableTool;
    @Autowired SearchPathTool searchPathTool;
    final Params params = new Params("AmbulanceDecisionTool");

    public enum RouteStatus { FOUND, UNREACHABLE, DEFERRED }
    public record Route(RouteStatus status, List<EntityID> path, double steps) {}
    private record Pair(EntityID from, EntityID to) {}
    private final Map<Pair, Route> routes = new HashMap<>();
    private final Set<Pair> reusedRoutes = new HashSet<>();
    private final Map<EntityID, Double> transportEstimates = new HashMap<>();
    private final Map<EntityID, Integer> deferredUntil = new HashMap<>();
    private final Map<EntityID, Integer> loadCooldown = new HashMap<>();
    private int cycle = Integer.MIN_VALUE, searches;
    private long deadline;
    private EntityID committed;
    private int committedAt;
    private List<EntityID> retainedPath = List.of();
    private long retainedRevision;
    private int retainedAt;
    private double retainedSteps;
    private int shortlistOffset;

    protected long now() { return System.nanoTime(); }

    private int positive(String name, int fallback) {
        int value = params.getInt(name, fallback);
        if (value <= 0) throw new IllegalArgumentException(name + " must be positive");
        return value;
    }

    private void beginCycle() {
        if (cycle == agentInfo.getTime()) return;
        cycle = agentInfo.getTime();
        searches = 0;
        routes.clear();
        reusedRoutes.clear();
        transportEstimates.clear();
        shortlistOffset += Math.max(1, positive("coarseCandidates", 64) / 2);
        deferredUntil.entrySet().removeIf(entry -> entry.getValue() <= cycle);
        loadCooldown.entrySet().removeIf(entry -> entry.getValue() <= cycle);
        int thinkLimit = scenarioInfo.getRawConfig().getIntValue("kernel.agents.think-time", 1000);
        long remaining = Math.max(0, thinkLimit - agentInfo.getThinkTimeMillis() - 5);
        long milliseconds = Math.min(positive("planningMillis", 60), remaining);
        deadline = now() + milliseconds * 1_000_000L;
    }

    public boolean exhausted() {
        beginCycle();
        return searches >= positive("maxSearchesPerCycle", 8) || now() >= deadline;
    }

    public int searchesThisCycle() { beginCycle(); return searches; }

    private double travelEstimate(Area from, Area to) {
        if (from.equals(to)) return 0;
        double step = Math.max(1, OptionsTool.MOVING_DISTANCE);
        return Math.max(1, Math.ceil(Math.hypot((double) from.getX() - to.getX(), (double) from.getY() - to.getY())
                / step * params.getDouble("travelTimeFactor", 1.3)));
    }

    public Route routeTo(Area target) { return route(humanPositionTool.getMyPositionArea(), target); }

    private Route route(Area from, Area target) {
        beginCycle();
        Pair key = new Pair(from.getID(), target.getID());
        Route previous = routes.get(key);
        if (previous != null) return previous;
        if (from.equals(target)) return new Route(RouteStatus.FOUND, List.of(from.getID()), 0);
        if (target.getID().equals(committed) && retainedRevision == areaPassableTool.getRevision()
                && cycle - retainedAt <= positive("routeReuseSteps", 3)) {
            int index = retainedPath.lastIndexOf(from.getID());
            Set<EntityID> changed = worldInfo.getChanged().getChangedEntities();
            if (index >= 0 && retainedPath.subList(index, retainedPath.size()).stream().noneMatch(changed::contains)) {
                List<EntityID> suffix = List.copyOf(retainedPath.subList(index, retainedPath.size()));
                // Keep a conservative cost bound until the original search expires.
                Route reused = new Route(RouteStatus.FOUND, suffix, retainedSteps);
                routes.put(key, reused);
                reusedRoutes.add(key);
                return reused;
            }
        }
        if (exhausted()) return new Route(RouteStatus.DEFERRED, List.of(), Double.POSITIVE_INFINITY);
        searches++;
        Route result = search(from, target);
        if (result.status == RouteStatus.DEFERRED && deferredUntil.size() < 128) deferredUntil.put(target.getID(), cycle + 2);
        if (result.status == RouteStatus.UNREACHABLE && from.equals(humanPositionTool.getMyPositionArea()))
            unavailableTargetTool.setUnavailableAndWait(target.getID(), UnavailableType.block,
                    Math.max(1, new Params("AtExecutor").getInt("waitForBlock", 10)), true);
        routes.put(key, result);
        return result;
    }

    protected Route search(Area from, Area target) {
        searchPathTool.setFromAndTo(from, target).ignoreImpassableAdjacentEdges()
                .calc(positive("maxExpansionsPerSearch", 6000), deadline);
        hfutinvincible.tool.path.Path path = searchPathTool.getAreaPath();
        Route result;
        if (path == null) {
            RouteStatus status = searchPathTool.isBudgetExceeded() ? RouteStatus.DEFERRED : RouteStatus.UNREACHABLE;
            result = new Route(status, List.of(), Double.POSITIVE_INFINITY);
        } else {
            Double cost = searchPathTool.getPassCost();
            double steps = cost != null && Double.isFinite(cost) && cost >= 0
                    ? Math.max(1, Math.ceil(cost / Math.max(1, OptionsTool.MOVING_DISTANCE)
                        * params.getDouble("travelTimeFactor", 1.3))) : travelEstimate(from, target);
            result = new Route(RouteStatus.FOUND, List.copyOf(path.getIDList()), steps);
        }
        return result;
    }

    private List<Refuge> refuges() {
        return worldInfo.getEntitiesOfType(StandardEntityURN.REFUGE).stream().map(Refuge.class::cast)
                .filter(refuge -> !refuge.isBedCapacityDefined() || refuge.getBedCapacity() > 0)
                .sorted(Comparator.comparingInt(refuge -> refuge.getID().getValue())).toList();
    }

    private double refugeScore(Area from, Refuge refuge, double travel) {
        RefugeRecord record = refugeConditionTool.recordMap.get(refuge.getID());
        boolean fresh = refuge.isBedCapacityDefined() && record != null && cycle - record.getLastUpdateTime() >= 0
                && cycle - record.getLastUpdateTime() <= positive("informationTtl", 10);
        return RescuePolicy.refugeCost(travel, fresh ? refugeConditionTool.getFreeBedsNum(refuge.getID()) : null,
                fresh ? refugeConditionTool.getWaitingListNum(refuge.getID()) : null, fresh,
                params.getDouble("queueStepsPerPerson", 3), params.getDouble("unknownRefugePenalty", 4),
                params.getDouble("fullRefugePenalty", 15));
    }

    private double transportEstimate(Area from, boolean precise) {
        if (!precise && transportEstimates.containsKey(from.getID())) return transportEstimates.get(from.getID());
        List<Refuge> options = new ArrayList<>(refuges());
        options.sort(Comparator.comparingDouble(refuge -> refugeScore(from, refuge, travelEstimate(from, refuge))));
        if (precise && options.size() > positive("refugesToRefine", 3)) {
            // If preferred refuges are blocked, try a rotating tail so a farther viable refuge is not starved.
            Collections.rotate(options.subList(positive("refugesToRefine", 3), options.size()), shortlistOffset);
        }
        double best = Double.POSITIVE_INFINITY;
        int checked = 0;
        for (Refuge refuge : options) {
            if (!precise) best = Math.min(best, refugeScore(from, refuge, travelEstimate(from, refuge)));
            else {
                Route route = route(from, refuge);
                if (route.status == RouteStatus.FOUND) best = Math.min(best, refugeScore(from, refuge, route.steps));
                if (++checked >= positive("refugesToRefine", 3) && Double.isFinite(best) || exhausted()) break;
            }
        }
        if (!precise) transportEstimates.put(from.getID(), best);
        return best;
    }

    private double confidence(EntityID human) {
        int age = Math.max(0, cycle - timeToDeadTool.getUpdateTime(human));
        return 1.0 / (1 + age / (double) positive("informationTtl", 10));
    }

    private boolean injured(Civilian human) {
        EntityID position = humanPositionTool.getPosition(human.getID());
        return (position == null || !(worldInfo.getEntity(position) instanceof AmbulanceTeam))
                && (!human.isHPDefined() || human.getHP() > 0)
                && (timeToDeadTool.isHurt(human.getID()) || (human.isDamageDefined() && human.getDamage() > 0));
    }

    private RescuePolicy.Estimate estimate(Civilian human, double arrival, double digging, double transport) {
        Integer life = timeToDeadTool.getTimeToDeadNowEstimate(human.getID());
        double remaining = OptionsTool.KERNEL_TIMESTEPS - cycle;
        return RescuePolicy.evaluate(life == null || life == OptionsTool.KERNEL_TIMESTEPS ? remaining : life,
                arrival, digging, transport, 0, params.getDouble("safetySteps", 3), remaining,
                life == null || life == OptionsTool.KERNEL_TIMESTEPS ? 0.5 : confidence(human.getID()));
    }

    public List<EntityID> rankLoadCandidates(Collection<EntityID> ids) {
        beginCycle();
        Area here = humanPositionTool.getMyPositionArea();
        double transport = transportEstimate(here, true);
        return ids.stream().filter(id -> !loadCooldown.containsKey(id))
                .filter(id -> worldInfo.getEntity(id) instanceof Civilian human && injured(human))
                .filter(id -> Integer.valueOf(0).equals(buriednessTool.getBuriedness(id)))
                .map(id -> Tuple2.of(id, estimate((Civilian) worldInfo.getEntity(id), 0, 0, transport)))
                .filter(pair -> pair.e1.feasible())
                .sorted(Comparator.<Tuple2<EntityID, RescuePolicy.Estimate>>comparingDouble(pair -> pair.e1.score())
                        .thenComparingInt(pair -> pair.e0.getValue()))
                .map(pair -> pair.e0).toList();
    }

    public void deferFailedLoad(EntityID humanId) {
        beginCycle();
        loadCooldown.put(humanId, cycle + positive("loadRetrySteps", 2));
    }

    private Area position(EntityID id) {
        EntityID areaId = humanPositionTool.getPositionAreaId(id);
        StandardEntity entity = areaId == null ? null : worldInfo.getEntity(areaId);
        return entity instanceof Area area ? area : null;
    }

    private List<EntityID> freshClaims(Area target, Class<? extends Human> type) {
        return agentTargetAreaTool.recordMap.entrySet().stream()
                .filter(entry -> !entry.getKey().equals(agentInfo.getID()))
                .filter(entry -> target.getID().equals(entry.getValue().getTargetAreaId()))
                .filter(entry -> cycle >= entry.getValue().getLastUpdateTime()
                        && cycle - entry.getValue().getLastUpdateTime() <= positive("claimTtl", 5))
                .map(Map.Entry::getKey).filter(id -> type.isInstance(worldInfo.getEntity(id)))
                .filter(id -> {
                    Human human = (Human) worldInfo.getEntity(id);
                    return (!human.isHPDefined() || human.getHP() > 0)
                            && (!human.isBuriednessDefined() || human.getBuriedness() == 0);
                }).toList();
    }

    private double diggingDelay(Area target, int buriedness, double arrival) {
        if (buriedness == 0) return 0;
        List<EntityID> firefighters = freshClaims(target, FireBrigade.class);
        if (firefighters.isEmpty()) return Double.POSITIVE_INFINITY;
        List<Double> arrivals = firefighters.stream().map(this::position).filter(Objects::nonNull)
                .map(area -> travelEstimate(area, target)).toList();
        double release = RescuePolicy.diggingCompletion(buriedness, arrivals);
        return release <= arrival + positive("rendezvousLeadSteps", 5) ? release : Double.POSITIVE_INFINITY;
    }

    public boolean isAllowed(Area target) {
        beginCycle();
        if (target instanceof Refuge) return true;
        int demand = 0;
        double transport = transportEstimate(target, false);
        for (EntityID id : humanPositionTool.getHumanIdsOnArea(target.getID())) {
            if (!(worldInfo.getEntity(id) instanceof Civilian human) || !injured(human)) continue;
            Integer buried = buriednessTool.getBuriedness(id);
            if (buried != null && estimate(human, travelEstimate(humanPositionTool.getMyPositionArea(), target),
                    diggingDelay(target, buried, travelEstimate(humanPositionTool.getMyPositionArea(), target)), transport).feasible()) demand++;
        }
        demand = Math.max(1, Math.min(demand, positive("maxResponders", 8)));
        List<EntityID> responders = new ArrayList<>(freshClaims(target, AmbulanceTeam.class));
        // A known loaded teammate is transporting, not available for another pickup.
        Set<EntityID> loaded = worldInfo.getEntitiesOfType(StandardEntityURN.CIVILIAN).stream()
                .map(entity -> humanPositionTool.getPosition(entity.getID()))
                .filter(Objects::nonNull).collect(Collectors.toSet());
        responders.removeIf(loaded::contains);
        responders.add(agentInfo.getID());
        responders.sort(Comparator.<EntityID>comparingDouble(id -> {
            Area area = id.equals(agentInfo.getID()) ? humanPositionTool.getMyPositionArea() : position(id);
            return area == null ? Double.POSITIVE_INFINITY : travelEstimate(area, target);
        }).thenComparingInt(EntityID::getValue));
        return responders.indexOf(agentInfo.getID()) < demand;
    }

    private double buildingScore(Area target, double arrival) {
        int possible = 0;
        boolean knownInjured = false;
        double best = Double.POSITIVE_INFINITY;
        double transport = Double.NaN;
        for (EntityID id : humanPositionTool.getHumanIdsOnArea(target.getID())) {
            if (!(worldInfo.getEntity(id) instanceof Civilian human) || !injured(human)) continue;
            knownInjured = true;
            if (Double.isNaN(transport)) transport = transportEstimate(target, false);
            Integer buried = buriednessTool.getBuriedness(id);
            if (buried == null) continue;
            RescuePolicy.Estimate estimate = estimate(human, arrival, diggingDelay(target, buried, arrival), transport);
            if (estimate.feasible()) { possible++; best = Math.min(best, estimate.score()); }
        }
        if (knownInjured) return possible == 0 ? Double.POSITIVE_INFINITY : best / Math.sqrt(possible);
        // Exploration is a separate, lower-priority objective, with a revisit preference.
        int age = Math.max(0, cycle - humanPositionTool.getLastVisitTime(target.getID()));
        return 200 + arrival - Math.min(40, age);
    }

    private boolean knownInjured(Area area) {
        return humanPositionTool.getHumanIdsOnArea(area.getID()).stream()
                .anyMatch(id -> worldInfo.getEntity(id) instanceof Civilian human && injured(human));
    }

    private boolean eligible(Area area) {
        if (deferredUntil.containsKey(area.getID())) return false;
        if (unavailableTargetTool.isUnavailable(area.getID(), UnavailableType.block, UnavailableType.competeFail)) return false;
        return knownInjured(area) || !unavailableTargetTool.isUnavailable(area.getID(),
                UnavailableType.noHurtDefinitely, UnavailableType.noHurtNotSure, UnavailableType.buried);
    }

    public List<Tuple2<Area, Double>> candidates() {
        beginCycle();
        Area here = humanPositionTool.getMyPositionArea();
        boolean loaded = agentInfo.someoneOnBoard() != null;
        Map<EntityID, Area> pool = new LinkedHashMap<>();
        if (loaded) refuges().forEach(refuge -> pool.put(refuge.getID(), refuge));
        else {
            Partition<EntityID> current = staticPartitionTool.getCurrentPartition();
            Partition<EntityID> assigned = staticPartitionTool.getAssignedPartition();
            current.getElements().forEach(id -> addBuilding(pool, id));
            assigned.getElements().forEach(id -> addBuilding(pool, id));
            for (int neighbor : staticPartitionTool.getNeighborPartition(staticPartitionTool.getCurrentPartitionId()))
                staticPartitionTool.getPartition(neighbor).getElements().forEach(id -> addBuilding(pool, id));
            // Do not hide an urgent received rescue report behind a partition boundary.
            for (StandardEntity entity : worldInfo.getEntitiesOfType(StandardEntityURN.CIVILIAN)) {
                Civilian human = (Civilian) entity;
                if (injured(human)) {
                    Area area = position(human.getID());
                    if (area != null) addBuilding(pool, area.getID());
                }
            }
        }
        List<Tuple2<Area, Double>> ranked = score(pool.values(), here, loaded);
        if (!loaded && ranked.isEmpty()) {
            worldInfo.getAllEntities().forEach(entity -> addBuilding(pool, entity.getID()));
            ranked = score(pool.values(), here, false);
        }
        // Coarse shortlist first; exact searches are shared with the executor and bounded per cycle.
        List<Integer> refine = new ArrayList<>();
        for (int i = 0; i < ranked.size(); i++)
            if (ranked.get(i).e0.getID().equals(committed)) refine.add(i);
        for (int i = 0; i < ranked.size() && refine.size() < positive("candidatesToRefine", 4); i++)
            if (!refine.contains(i)) refine.add(i);
        for (int i : refine) {
            Area target = ranked.get(i).e0;
            if (!isAllowed(target)) continue;
            Route route = routeTo(target);
            double value = route.status == RouteStatus.FOUND
                    ? (loaded ? refugeScore(here, (Refuge) target, route.steps)
                        : buildingScore(target, route.steps) + sectorPenalty(target))
                    : Double.POSITIVE_INFINITY;
            ranked.set(i, Tuple2.of(target, value));
            if (exhausted()) break;
        }
        ranked.removeIf(pair -> !Double.isFinite(pair.e1));
        ranked.sort(order());
        if (!ranked.isEmpty() && committed != null) {
            for (int i = 0; i < ranked.size(); i++) {
                Tuple2<Area, Double> previous = ranked.get(i);
                if (previous.e0.getID().equals(committed) && isAllowed(previous.e0)
                        && RescuePolicy.keepTarget(previous.e1, ranked.get(0).e1, cycle - committedAt,
                            positive("targetHoldSteps", 10), params.getDouble("switchImprovement", 0.2))) {
                    ranked.remove(i);
                    ranked.add(0, previous);
                    break;
                }
            }
        }
        return ranked;
    }

    private List<Tuple2<Area, Double>> score(Collection<Area> pool, Area here, boolean loaded) {
        List<Tuple2<Area, Double>> result = new ArrayList<>();
        List<Area> sorted = pool.stream().filter(this::eligible)
                .sorted(Comparator.<Area>comparingDouble(area -> travelEstimate(here, area)
                        + (!loaded && !knownInjured(area) ? 1000 : 0))
                        .thenComparingInt(area -> area.getID().getValue())).toList();
        int limit = loaded ? sorted.size() : positive("coarseCandidates", 64);
        Map<EntityID, Area> shortlist = new LinkedHashMap<>();
        for (int i = 0; i < Math.min(sorted.size(), limit); i++) {
            // Preserve nearest urgent entries and rotate the rest to avoid permanent starvation.
            int index = i < limit / 2 || sorted.size() <= limit ? i
                    : limit / 2 + Math.floorMod(shortlistOffset + i - limit / 2, sorted.size() - limit / 2);
            Area area = sorted.get(index);
            shortlist.put(area.getID(), area);
        }
        if (committed != null && pool.stream().anyMatch(area -> area.getID().equals(committed))) {
            Area area = (Area) worldInfo.getEntity(committed);
            if (area != null && eligible(area)) shortlist.put(committed, area);
        }
        for (Area target : shortlist.values()) {
            double travel = travelEstimate(here, target);
            double value = loaded ? refugeScore(here, (Refuge) target, travel) : buildingScore(target, travel);
            if (!loaded) value += sectorPenalty(target);
            if (Double.isFinite(value)) result.add(Tuple2.of(target, value));
        }
        result.sort(order());
        return result;
    }

    private double sectorPenalty(Area target) {
        return staticPartitionTool.getAssignedPartition().contains(target.getID()) ? 0
                : params.getDouble("outsidePartitionPenalty", 2);
    }

    private Comparator<Tuple2<Area, Double>> order() {
        return Comparator.<Tuple2<Area, Double>>comparingDouble(pair -> pair.e1)
                .thenComparingInt(pair -> pair.e0.getID().getValue());
    }

    private void addBuilding(Map<EntityID, Area> pool, EntityID id) {
        if (worldInfo.getEntity(id) instanceof Building building && !(building instanceof Refuge)) pool.put(id, building);
    }

    @Override
    @After({"AgentTargetAreaTool"})
    public void afterCalc(CalcContext context) {
        Area target = context.getTarget();
        EntityID next = target == null || context.getResultAction() == null ? null : target.getID();
        if (!Objects.equals(next, committed)) committedAt = agentInfo.getTime();
        committed = next;
        Pair key = target == null ? null : new Pair(humanPositionTool.getMyPositionAreaId(), target.getID());
        Route selected = routes.get(key);
        retainedPath = selected != null && selected.status == RouteStatus.FOUND && context.getTargetPath() != null
                ? List.copyOf(context.getTargetPath()) : List.of();
        retainedRevision = areaPassableTool.getRevision();
        if (selected != null) {
            retainedSteps = selected.steps;
            if (!reusedRoutes.contains(key)) retainedAt = agentInfo.getTime();
        }
    }
}
