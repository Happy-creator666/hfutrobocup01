package hfutinvincible.tool.path;


import org.apache.log4j.Logger;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;

/** Non-negative costs; callers must supply an admissible lower-bound heuristic. */
public class AStarSearch {

    public enum Status { FOUND, UNREACHABLE, BUDGET_EXCEEDED }
    public record Result<T>(Status status, List<T> path, double cost, int expanded) {}
    private record Entry<T>(T node, double cost, double priority, long sequence) {}

    public static <T> LinkedList<T> getPath(
            @Nonnull Set<T> fromNodes,
            @Nonnull Set<T> toNodes,
            int maxNodeNum,
            @Nonnull Function<T, Stream<T>> adjacentNodesProvider,
            @Nonnull BiFunction<T, T, Double> distanceMetric,
            @Nonnull Function<T, Double> heuristicEvaluator,
            Logger logger
    ) {
        Result<T> result = search(fromNodes, toNodes, adjacentNodesProvider, distanceMetric,
                heuristicEvaluator, Integer.MAX_VALUE, Long.MAX_VALUE);
        return result.status == Status.FOUND ? new LinkedList<>(result.path) : null;
    }

    public static <T> Result<T> search(Set<T> starts, Set<T> goals,
            Function<T, Stream<T>> adjacent, BiFunction<T, T, Double> edgeCost,
            Function<T, Double> heuristic, int maxExpansions, long deadlineNanos) {
        if (maxExpansions < 0) throw new IllegalArgumentException("Negative search budget");
        if (starts.isEmpty() || goals.isEmpty()) return new Result<>(Status.UNREACHABLE, List.of(), Double.POSITIVE_INFINITY, 0);
        Map<T, Double> costs = new HashMap<>();
        Map<T, T> previous = new HashMap<>();
        Map<T, Double> estimates = new HashMap<>();
        PriorityQueue<Entry<T>> queue = new PriorityQueue<>(Comparator
                .<Entry<T>>comparingDouble(Entry::priority).thenComparingLong(Entry::sequence));
        long sequence = 0;
        for (T start : starts) {
            costs.put(start, 0.0);
            queue.add(new Entry<>(start, 0, estimate(start, goals, heuristic, estimates), sequence++));
        }
        int expanded = 0;
        while (!queue.isEmpty()) {
            Entry<T> current = queue.poll();
            if (Double.compare(current.cost, costs.get(current.node)) != 0) continue;
            if (goals.contains(current.node)) {
                LinkedList<T> path = new LinkedList<>();
                for (T node = current.node; node != null; node = previous.get(node)) path.addFirst(node);
                return new Result<>(Status.FOUND, List.copyOf(path), current.cost, expanded);
            }
            if (expanded >= maxExpansions || System.nanoTime() >= deadlineNanos)
                return new Result<>(Status.BUDGET_EXCEEDED, List.of(), Double.POSITIVE_INFINITY, expanded);
            expanded++;
            try (Stream<T> neighbors = adjacent.apply(current.node)) {
                Iterator<T> iterator = neighbors.iterator();
                while (iterator.hasNext()) {
                    if (System.nanoTime() >= deadlineNanos)
                        return new Result<>(Status.BUDGET_EXCEEDED, List.of(), Double.POSITIVE_INFINITY, expanded);
                    T next = iterator.next();
                    double step = edgeCost.apply(current.node, next);
                    if (Double.isNaN(step) || step < 0) throw new IllegalArgumentException("Invalid edge cost: " + step);
                    double cost = current.cost + step;
                    if (Double.isFinite(cost) && cost < costs.getOrDefault(next, Double.POSITIVE_INFINITY)) {
                        costs.put(next, cost);
                        previous.put(next, current.node);
                        queue.add(new Entry<>(next, cost, cost + estimate(next, goals, heuristic, estimates), sequence++));
                    }
                }
            }
        }
        return new Result<>(Status.UNREACHABLE, List.of(), Double.POSITIVE_INFINITY, expanded);
    }

    private static <T> double estimate(T node, Set<T> goals, Function<T, Double> heuristic, Map<T, Double> cache) {
        if (goals.contains(node)) return 0;
        return cache.computeIfAbsent(node, key -> {
            double value = heuristic.apply(key);
            if (!Double.isFinite(value) || value < 0) throw new IllegalArgumentException("Invalid heuristic: " + value);
            return value;
        });
    }
}
