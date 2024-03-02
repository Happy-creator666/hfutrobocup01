package hfutinvincible.tool.human;

import javax.annotation.Nonnull;

public class TimeToDeadRecord {

    private int timeToDead;
    private int lastUpdateTime;
    private InfoSource infoSource;

    public int getTimeToDead() {
        return timeToDead;
    }

    public void setTimeToDead(int timeToDead) {
        this.timeToDead = timeToDead;
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
        TimeToDeadRecord that = (TimeToDeadRecord) o;
        return timeToDead == that.timeToDead;
    }

    @Override
    public int hashCode() {
        return timeToDead;
    }

    @Override
    public String toString() {
        return "{" +
                "timeToDead=" + timeToDead +
                ", lastUpdateTime=" + lastUpdateTime +
                ", infoSource=" + infoSource +
                '}';
    }
}
