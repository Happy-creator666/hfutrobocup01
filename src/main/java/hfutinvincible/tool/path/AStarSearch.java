package hfutinvincible.tool.path;

import org.apache.log4j.Logger;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;

public class AStarSearch {

    public static <T> LinkedList<T> getPath(
            @Nonnull Set<T> fromNodes,
            @Nonnull Set<T> toNodes,
            int maxNodeNum,
            @Nonnull Function<T, Stream<T>> adjacentNodesProvider,
            @Nonnull BiFunction<T, T, Double> distanceMetric,
            @Nonnull Function<T, Double> heuristicEvaluator,
            Logger logger
    ) {
        Map<T, Double> costMap = new HashMap<>();
        PriorityQueue<T> searchQueue = new PriorityQueue<>(Comparator.comparingDouble(
                node -> costMap.getOrDefault(node, Double.MAX_VALUE) + heuristicEvaluator.apply(node)));
        Set<T> determined = new HashSet<>();
        Map<T, T> prevMap = new HashMap<>();
        LinkedList<T> foundPath = null;

        fromNodes.forEach(fromNode -> {
            searchQueue.offer(fromNode);
            costMap.put(fromNode, 0.0);
        });

        while (determined.size() < maxNodeNum && !searchQueue.isEmpty()) {
            T curNode = searchQueue.poll();
            determined.add(curNode);
            if (toNodes.contains(curNode)) {
                foundPath = new LinkedList<>();
                T iter = curNode;
                do {
                    foundPath.addFirst(iter);
                    iter = prevMap.get(iter);
                } while (iter != null);
//                logger.info("foundPath=" + foundPath);
                return foundPath;
            }

            adjacentNodesProvider.apply(curNode).forEach(nextNode -> {
//                if (determined.contains(nextNode)) continue;
                double newCost = costMap.getOrDefault(curNode, Double.MAX_VALUE) +
                        distanceMetric.apply(curNode, nextNode);
                if (newCost < costMap.getOrDefault(nextNode, Double.MAX_VALUE)) {
                    costMap.put(nextNode, newCost);
                    prevMap.put(nextNode, curNode);
                    searchQueue.offer(nextNode);
                }
            });
        }
        return null;
    }
}
