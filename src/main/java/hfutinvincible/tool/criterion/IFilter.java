package hfutinvincible.tool.criterion;

import java.util.function.Predicate;

/**
 * 过滤器，用于筛去肯定不能做为目标的候选
 * @param <T>
 */
@FunctionalInterface
public interface IFilter<T> extends Predicate<T> {

    @Override
    boolean test(T candidate);
}