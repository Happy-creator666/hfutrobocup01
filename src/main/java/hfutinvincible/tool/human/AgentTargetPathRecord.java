package hfutinvincible.tool.human;

import rescuecore2.worldmodel.EntityID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

public class AgentTargetPathRecord {

    private List<EntityID> targetPath;
    private int lastUpdateTime;
    private InfoSource infoSource;

    @Nullable
    public List<EntityID> getTargetPath() {
        return targetPath;
    }

    public void setTargetPath(@Nullable List<EntityID> targetPath) {
        this.targetPath = targetPath;
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
        AgentTargetPathRecord that = (AgentTargetPathRecord) o;
        return targetPath.equals(that.targetPath);
    }

    @Override
    public String toString() {
        return "{" +
                "targetPath=" + targetPath +
                ", lastUpdateTime=" + lastUpdateTime +
                ", infoSource=" + infoSource +
                '}';
    }
}