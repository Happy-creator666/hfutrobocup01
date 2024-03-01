package hfutinvincible.tool.targetprovider;


import hfutinvincible.Tuple2;

import java.util.List;

@FunctionalInterface
public interface TargetProvider<T> {
    List<Tuple2<T, Double>> getSortedCandidatesAndScores();//获取排好序的候选以及分数
}