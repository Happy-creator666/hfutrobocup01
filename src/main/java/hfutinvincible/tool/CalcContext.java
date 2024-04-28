package hfutinvincible.tool;

import adf.core.agent.action.Action;
import rescuecore2.worldmodel.EntityID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

public class CalcContext {

    private Object target;
    private Action resultAction;
    private List<EntityID> targetPath;
    private Double resultScore;

    public CalcContext() {}

    //将该对象的所有属性置空
    public void clear() {
        target = null;
        resultAction = null;
        targetPath = null;
        resultScore = null;
    }

    //将传入的ResultCollector对象替换自身
    public void replace(@Nonnull CalcContext other) {
        this.target = other.getTarget();
        this.resultAction = other.getResultAction();
        this.targetPath = other.getTargetPath();
        this.resultScore = other.getResultScore();
    }

    //分别获取属性值
    @Nullable
    @SuppressWarnings("unchecked")
    public <T> T getTarget() {
        return (T) target;
    }

    @Nullable
    public Action getResultAction() {
        return resultAction;
    }

    @Nullable
    public List<EntityID> getTargetPath() {
        return targetPath;
    }

    @Nullable
    public Double getResultScore() {
        return resultScore;
    }

    //分别设置属性值
    public void setResultTarget(@Nullable Object target) {
        this.target = target;
    }

    public void setResultAction(@Nullable Action resultAction) {
        this.resultAction = resultAction;
    }

    public void setTargetPath(@Nullable List<EntityID> targetPath) {
        this.targetPath = targetPath;
    }

    public void setResultScore(@Nullable Double resultScore) {
        this.resultScore = resultScore;
    }

    //输出自身信息
    @Override
    public String toString() {
        return "ResultCollector{" +
                "target=" + target +
                ", resultAction=" + resultAction +
                ", targetPath=" + targetPath +
                '}';
    }
}
