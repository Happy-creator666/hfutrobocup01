package hfutinvincible.tool.building;

import hfutinvincible.tool.human.InfoSource;

import javax.annotation.Nonnull;

public class BuildingBrokennessRecord {

    private int brokenness;
    private int lastUpdateTime;
    private InfoSource infoSource;

    public int getBrokenness() {
        return brokenness;
    }

    public void setBrokenness(int brokenness) {
        this.brokenness = brokenness;
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
        BuildingBrokennessRecord that = (BuildingBrokennessRecord) o;
        return brokenness == that.brokenness;
    }

    @Override
    public int hashCode() {
        return brokenness;
    }
}