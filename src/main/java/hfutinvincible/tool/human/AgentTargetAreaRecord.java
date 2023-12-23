package hfutinvincible.tool.human;

import rescuecore2.worldmodel.EntityID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class AgentTargetAreaRecord {

    private EntityID targetAreaId;
    private int lastUpdateTime;
    private InfoSource infoSource;

    @Nullable
    public EntityID getTargetAreaId() {
        return targetAreaId;
    }

    public void setTargetAreaId(@Nullable EntityID targetAreaId) {
        this.targetAreaId = targetAreaId;
    }

    public int getLastUpdateTime() {
        return lastUpdateTime;
    }

    public void setLastUpdateTime(int lastUpdateTime) {
        this.lastUpdateTime = lastUpdateTime;
    }

    @Nonnull
    public InfoSource getInfoSource() {
        return infoSource;
    }

    public void setInfoSource(@Nonnull InfoSource infoSource) {
        this.infoSource = infoSource;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AgentTargetAreaRecord that = (AgentTargetAreaRecord) o;
        return targetAreaId.equals(that.targetAreaId);
    }

    @Override
    public int hashCode() {
        return targetAreaId.hashCode();
    }

    @Override
    public String toString() {
        return "{" +
                "targetAreaId=" + targetAreaId +
                ", lastUpdateTime=" + lastUpdateTime +
                ", infoSource=" + infoSource +
                '}';
    }
}
