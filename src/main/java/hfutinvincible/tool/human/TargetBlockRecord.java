package hfutinvincible.tool.human;

import rescuecore2.worldmodel.EntityID;

import javax.annotation.Nonnull;

public class TargetBlockRecord {

    private EntityID fromAreaId;
    private EntityID toAreaId;
    private int lastUpdateTime;
    private InfoSource infoSource;

    @Nonnull
    public EntityID getFromAreaId() {
        return fromAreaId;
    }

    public void setFromAreaId(@Nonnull EntityID fromAreaId) {
        this.fromAreaId = fromAreaId;
    }

    @Nonnull
    public EntityID getToAreaId() {
        return toAreaId;
    }

    public void setToAreaId(@Nonnull EntityID toAreaId) {
        this.toAreaId = toAreaId;
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
        TargetBlockRecord that = (TargetBlockRecord) o;
        if (!fromAreaId.equals(that.fromAreaId)) return false;
        return toAreaId.equals(that.toAreaId);
    }

    @Override
    public int hashCode() {
        int result = fromAreaId.hashCode();
        result = 31 * result + toAreaId.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "{" +
                "fromAreaId=" + fromAreaId +
                ", toAreaId=" + toAreaId +
                ", lastUpdateTime=" + lastUpdateTime +
                ", infoSource=" + infoSource +
                '}';
    }
}