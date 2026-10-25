package hfutinvincible.tool.path;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.function.Function;
import static org.junit.jupiter.api.Assertions.*;

class AStarSearchTest {
    private AStarSearch.Result<Integer> search(double[][] edges, Set<Integer> starts, Set<Integer> goals,
                                               Function<Integer, Double> heuristic, int budget) {
        return AStarSearch.search(starts, goals,
                node -> java.util.stream.IntStream.range(0, edges.length)
                        .filter(next -> Double.isFinite(edges[node][next])).boxed(),
                (from, to) -> edges[from][to], heuristic, budget, Long.MAX_VALUE);
    }

    private double[][] graph(int n) {
        double[][] result = new double[n][n];
        for (double[] row : result) Arrays.fill(row, Double.POSITIVE_INFINITY);
        return result;
    }

    @Test void improvedCostsReorderQueueAndReopenNodes() {
        double[][] edges = graph(4);
        edges[0][1] = 3; edges[0][2] = 1; edges[2][1] = 1; edges[1][3] = 3;
        // Admissible but inconsistent: node 1 is expanded before node 2, then must reopen.
        double[] h = {0, 0, 4, 0};
        var result = search(edges, Set.of(0), Set.of(3), node -> h[node], 20);
        assertEquals(AStarSearch.Status.FOUND, result.status());
        assertEquals(List.of(0, 2, 1, 3), result.path());
        assertEquals(5, result.cost());
    }

    @Test void multipleStartsAndGoalsChooseCheapestReachableGoal() {
        double[][] edges = graph(6);
        edges[0][4] = 8; edges[1][2] = 0; edges[2][5] = 2;
        var result = search(edges, Set.of(0, 1), Set.of(4, 5), node -> 0.0, 20);
        assertEquals(List.of(1, 2, 5), result.path());
        assertEquals(2, result.cost());
        assertEquals(AStarSearch.Status.UNREACHABLE,
                search(edges, Set.of(3), Set.of(4), node -> 0.0, 20).status());
    }

    @Test void exhaustionIsDistinctFromUnreachableAndStartGoalNeedsNoExpansion() {
        double[][] edges = graph(3); edges[0][1] = 1; edges[1][2] = 1;
        assertEquals(AStarSearch.Status.BUDGET_EXCEEDED,
                search(edges, Set.of(0), Set.of(2), node -> 0.0, 1).status());
        assertEquals(AStarSearch.Status.FOUND,
                search(edges, Set.of(0), Set.of(0), node -> 0.0, 0).status());
        assertEquals(AStarSearch.Status.BUDGET_EXCEEDED, AStarSearch.search(Set.of(0), Set.of(2),
                node -> java.util.stream.Stream.of(1), (a,b) -> 1.0, node -> 0.0,
                10, System.nanoTime() - 1).status());
    }

    @Test void rejectsNegativeAndNanCostsAndInvalidHeuristics() {
        for (double bad : new double[]{-1, Double.NaN}) {
            assertThrows(IllegalArgumentException.class, () -> AStarSearch.search(Set.of(0), Set.of(1),
                    node -> java.util.stream.Stream.of(1), (a,b) -> bad, node -> 0.0, 10, Long.MAX_VALUE));
        }
        assertThrows(IllegalArgumentException.class, () -> AStarSearch.search(Set.of(0), Set.of(1),
                node -> java.util.stream.Stream.of(1), (a,b) -> 1.0, node -> Double.NaN, 10, Long.MAX_VALUE));
    }

    @Test void seededRandomGraphsMatchIndependentBellmanFord() {
        Random random = new Random(20261025);
        for (int sample = 0; sample < 150; sample++) {
            int n = 14;
            double[][] edges = graph(n);
            for (int a = 0; a < n; a++) for (int b = 0; b < n; b++)
                if (a != b && random.nextDouble() < .22) edges[a][b] = random.nextInt(20);
            Set<Integer> starts = Set.of(0, 1), goals = Set.of(n - 2, n - 1);
            double[] distance = new double[n]; Arrays.fill(distance, Double.POSITIVE_INFINITY);
            starts.forEach(start -> distance[start] = 0);
            for (int pass = 0; pass < n - 1; pass++)
                for (int a = 0; a < n; a++) for (int b = 0; b < n; b++)
                    distance[b] = Math.min(distance[b], distance[a] + edges[a][b]);
            double expected = Math.min(distance[n - 2], distance[n - 1]);
            var result = search(edges, starts, goals, node -> 0.0, 500);
            assertEquals(expected, result.cost(), "sample " + sample);
            if (Double.isFinite(expected)) {
                assertTrue(starts.contains(result.path().get(0)));
                assertTrue(goals.contains(result.path().get(result.path().size() - 1)));
                double actual = 0;
                for (int i = 1; i < result.path().size(); i++) actual += edges[result.path().get(i - 1)][result.path().get(i)];
                assertEquals(expected, actual);
            } else assertEquals(AStarSearch.Status.UNREACHABLE, result.status());
        }
    }
}
