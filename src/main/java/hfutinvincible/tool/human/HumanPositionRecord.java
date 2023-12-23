package hfutinvincible.tool.human;

import rescuecore2.worldmodel.EntityID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;


public class HumanPositionRecord {

    private EntityID humanPosId;
    private int lastUpdateTime;
    private InfoSource infoSource;

    @Nullable
    public EntityID getHumanPosId() {
        return humanPosId;
    }

    public void setHumanPosId(@Nullable EntityID humanPosId) {
        this.humanPosId = humanPosId;
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
        HumanPositionRecord record = (HumanPositionRecord) o;
        return Objects.equals(humanPosId, record.humanPosId);
    }

    @Override
    public int hashCode() {
        return humanPosId == null ? 0 : humanPosId.hashCode();
    }

    @Override
    public String toString() {
        return "{" +
                "humanPosId=" + humanPosId +
                ", lastUpdateTime=" + lastUpdateTime +
                ", infoSource=" + infoSource +
                '}';
    }
}