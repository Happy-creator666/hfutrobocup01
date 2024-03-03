package hfutinvincible.tool.criterion;

import org.jblas.DoubleMatrix;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;

@FunctionalInterface
public interface ICriterion<T> {
    /**
     * 返回基于此指标的评分矩阵
     * @param candidates 所有候选
     * @return 一个 unmodifiableMap，key 为列名，value 为列
     */
    @Nonnull
    Map<CriterionEnum, DoubleMatrix> evaluate(@Nonnull List<T> candidates);
}