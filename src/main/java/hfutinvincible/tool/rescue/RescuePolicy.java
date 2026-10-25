package hfutinvincible.tool.rescue;

import java.util.Collection;

/** Pure, deterministic scoring rules. Times are estimated simulation steps, not wall-clock seconds. */
public final class RescuePolicy {
    private RescuePolicy() {}
    public record Estimate(boolean feasible, double completion, double slack, double score) {}

    public static Estimate evaluate(double life, double arrival, double digging, double transport,
            double waiting, double safety, double matchRemaining, double confidence) {
        double completion = Math.max(arrival, digging) + 1 + transport + waiting + 1;
        boolean feasible = Double.isFinite(completion) && arrival >= 0 && digging >= 0
                && transport >= 0 && waiting >= 0 && Double.isFinite(life) && life > 0
                && Double.isFinite(safety) && safety >= 0 && Double.isFinite(matchRemaining)
                && Double.isFinite(confidence) && confidence > 0
                && completion + safety <= life && completion <= matchRemaining;
        double slack = life - completion - safety;
        // Feasibility first; then urgency/effort. This is a heuristic, not a survival probability.
        double score = feasible ? completion / (1 + 4 / (1 + slack)) / Math.max(0.25, Math.min(1, confidence))
                : Double.POSITIVE_INFINITY;
        return new Estimate(feasible, completion, slack, score);
    }

    public static double refugeCost(double travel, Integer freeBeds, Integer queue, boolean fresh,
            double queueSteps, double unknownPenalty, double fullPenalty) {
        if (!Double.isFinite(travel) || travel < 0 || !Double.isFinite(queueSteps) || queueSteps < 0
                || !Double.isFinite(unknownPenalty) || unknownPenalty < 0
                || !Double.isFinite(fullPenalty) || fullPenalty < 0) return Double.POSITIVE_INFINITY;
        if (!fresh || freeBeds == null) return travel + unknownPenalty;
        if (freeBeds > 0) return travel;
        return travel + fullPenalty + Math.max(0, queue == null ? 0 : queue) * queueSteps;
    }

    public static boolean keepTarget(double previousScore, double challengerScore, int age,
            int maxHold, double requiredGain) {
        return Double.isFinite(previousScore) && previousScore >= 0 && !Double.isNaN(challengerScore)
                && requiredGain >= 0 && requiredGain < 1 && age >= 0 && age < maxHold
                && challengerScore >= previousScore * (1 - requiredGain);
    }

    /** Each claimed firefighter contributes one unit per step, only after its estimated arrival. */
    public static double diggingCompletion(int work, Collection<Double> arrivalTimes) {
        if (work <= 0) return 0;
        double[] arrivals = arrivalTimes.stream().filter(value -> Double.isFinite(value) && value >= 0)
                .mapToDouble(Double::doubleValue).sorted().toArray();
        if (arrivals.length == 0) return Double.POSITIVE_INFINITY;
        double remaining = work, time = arrivals[0];
        for (int workers = 1; workers < arrivals.length; workers++) {
            double available = (arrivals[workers] - time) * workers;
            if (remaining <= available) return Math.ceil(time + remaining / workers);
            remaining -= available;
            time = arrivals[workers];
        }
        return Math.ceil(time + remaining / arrivals.length);
    }
}
