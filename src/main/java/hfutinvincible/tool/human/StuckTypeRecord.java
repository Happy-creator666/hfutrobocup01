package hfutinvincible.tool.human;
import javax.annotation.Nonnull;

public class StuckTypeRecord {

    private StuckType stuckType;
    private int lastUpdateTime;
    private InfoSource infoSource;

    public boolean isStuck() {
        return this.stuckType == StuckType.STUCK_IN_BLOCKADE_OR_BUILDING
                || this.stuckType == StuckType.STUCK_WHEN_MOVING;
    }

    @Nonnull
    public StuckType getStuckType() {
        return stuckType;
    }

    public void setStuckType(@Nonnull StuckType stuckType) {
        this.stuckType = stuckType;
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
        StuckTypeRecord that = (StuckTypeRecord) o;
        return stuckType == that.stuckType;
    }

    @Override
    public int hashCode() {
        return stuckType.hashCode();
    }

    @Override
    public String toString() {
        return "{" +
                "stuckType=" + stuckType +
                ", lastUpdateTime=" + lastUpdateTime +
                ", infoSource=" + infoSource +
                '}';
    }
}