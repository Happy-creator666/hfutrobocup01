package hfutinvincible.debug;

import adf.core.agent.action.Action;
import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.WorldInfo;
import hfutinvincible.Tuple2;
import hfutinvincible.anno.DataType;
import hfutinvincible.anno.DebugProp;
import hfutinvincible.tool.criterion.ColumnIndexedDoubleMatrix;
import org.locationtech.jts.geom.Coordinate;
import rescuecore2.misc.geometry.Vector2D;
import rescuecore2.standard.entities.Area;

import java.lang.reflect.Field;
import java.util.List;

public class DebugData {


    @DebugProp(DataType.PERSISTENT)
    public AgentInfo agentInfo;

    @DebugProp(DataType.PERSISTENT)
    public WorldInfo worldInfo;

    /**
     * 用于记录本周期是否调用 targetProvider 计算了 targets，
     * 如果没有的话，配置文件发生更改触发的回调里面也不应该调用 targetProvider 计算 targets。
     * <br>
     * 注意这个属性是 PERSISTENT 的！
     */
    @DebugProp(DataType.PERSISTENT)
    public boolean calcTargetOrNot;

    /**
     * 所有的目标 Area 以及分数
     */
    @DebugProp(DataType.EPHEMERAL)
    public List<Tuple2<Area, Double>> sortedTargetsAndScores;

    /**
     * 所有的目标，顺序和 DecisionMatrix 一致
     */
    @DebugProp(DataType.PERSISTENT)
    public List<Area> candidates;

    /**
     * 决策矩阵，目标的顺序和 candidates 一致
     */
    @DebugProp(DataType.PERSISTENT)
    public ColumnIndexedDoubleMatrix decisionMatrix;

    /**
     * 引导点
     */
    @DebugProp(DataType.EPHEMERAL)
    public Coordinate guideCoord;

    /**
     * 引导向量
     */
    @DebugProp(DataType.EPHEMERAL)
    public Vector2D guideVector;

    /**
     * 本周期的动作
     */
    @DebugProp(DataType.EPHEMERAL)
    public Action action;

    public void clear() {
        // 置空所有标记为 EPHEMERAL 的属性
        Field[] fields = this.getClass().getDeclaredFields();
        for (Field field : fields) {
            if (field.isAnnotationPresent(DebugProp.class)) {
                DataType dataType = field.getAnnotation(DebugProp.class).value();
                if (dataType == DataType.EPHEMERAL) {
                    try {
                        field.set(this, null);
                    } catch (IllegalAccessException e) {
                        // TODO ignored
                    }
                }
            }
        }
    }
}
