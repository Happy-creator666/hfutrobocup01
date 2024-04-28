package hfutinvincible.tool.targetprovider;

import adf.core.agent.info.AgentInfo;
import hfutinvincible.Tuple2;
import hfutinvincible.anno.AgentType;
import hfutinvincible.anno.Autowired;
import hfutinvincible.anno.HfutTool;
import hfutinvincible.debug.Debugger;
import hfutinvincible.tool.LifeCycle;
import hfutinvincible.tool.criterion.ColumnIndexedDoubleMatrix;
import hfutinvincible.tool.criterion.CriterionEnum;
import hfutinvincible.tool.criterion.ICriterion;
import org.apache.log4j.Logger;
import org.jblas.DoubleMatrix;
import rescuecore2.standard.entities.AmbulanceTeam;
import rescuecore2.standard.entities.Area;
import rescuecore2.standard.entities.FireBrigade;
import rescuecore2.standard.entities.PoliceForce;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
//由于这个类被设置为抽象类无法实例化不写成一个HFUTTool

public abstract class CriteriaBasedTargetProvider<T> implements TargetProvider<T> {

    @Autowired
    Logger logger;

    @Autowired
    AgentInfo agentInfo;

    /** 所有 Criterion，用于评估候选集中每个候选的好坏 */
    private List<ICriterion<T>> criterionList;

    ////////////////////////////////////////////////

    private List<T> candidates;
    private ColumnIndexedDoubleMatrix decisionMatrix;

    /**
     * 获得候选 candidate 指标名为 criterionName 的指标值
     */
    @Nullable
    public final Double getScore(String criterionName, T candidate) {
        if (decisionMatrix == null)
            return null;
        int index = candidates.indexOf(candidate);
        if (index == -1) {
            logger.error("cannot find " + candidate + " in " + candidates);
            return null;
        }
        double result = decisionMatrix.get(index, criterionName);
        logger.info("get " + criterionName + " of " + candidate + ":" + result);
        return result;
    }

    public final List<String> getCriterionNames() {
        if (decisionMatrix == null)
            return List.of();
        return List.copyOf(decisionMatrix.getColumnNames().stream().sorted().toList());
    }

    /**
     * 添加决策指标
     * @param criteria 要添加的所有决策指标
     */
    @SafeVarargs
    protected final void setCriteria(ICriterion<T>... criteria) {
        this.criterionList = List.of(criteria);
    }

    /**
     * 指定所有候选
     * @return 所有候选构成的列表
     */
    @Nonnull
    protected abstract List<T> generateCandidates();

    /**
     * 根据传入的 decisionMatrix 计算综合得分
     * @param m 一个 ColumnIndexedDoubleMatrix
     * @return 一个 n x 1 的列向量
     */
    @Nonnull
    protected abstract DoubleMatrix evaluate(@Nonnull ColumnIndexedDoubleMatrix m);

    @Nonnull
    public final List<Tuple2<T, Double>> getSortedCandidatesAndScores() {

        // 获得 filter 筛选后的所有候选
        candidates = generateCandidates();
        // 应用所有评价指标，计算得到决策矩阵 decisionMatrix 和每一列的名字 columnNames
        decisionMatrix = new ColumnIndexedDoubleMatrix(candidates.size());
        for (ICriterion<T> criterion : criterionList) {
            Map<CriterionEnum, DoubleMatrix> result = criterion.evaluate(candidates);
            decisionMatrix.addColumns(result);
        }

        // 将决策矩阵综合为评分矩阵，计算得到最后的评分
        DoubleMatrix scoreVector = evaluate(decisionMatrix);

        Debugger.withDebugData(agentInfo, debugData -> {
            debugData.candidates = (List<Area>) candidates;
            debugData.decisionMatrix = decisionMatrix;
        });

        // 根据评分矩阵，将候选排序
        List<Tuple2<T, Double>> sortedTargetsAndScores = IntStream.range(0, candidates.size())
                .mapToObj(i -> Tuple2.of(candidates.get(i), scoreVector.get(i)))
                .sorted(Comparator.comparingDouble(t -> - t.e1)) // 按得分从大到小排序
                .toList();

        return sortedTargetsAndScores;
    }
}
