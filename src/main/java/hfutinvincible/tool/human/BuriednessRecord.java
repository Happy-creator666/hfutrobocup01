package hfutinvincible.tool.human;

import javax.annotation.Nonnull;

/**
 * 关于一个人的掩埋深度记录
 */
public class BuriednessRecord {

    private int buriedness;
    private int lastUpdateTime;
    private InfoSource infoSource;

    @Nonnull
    public InfoSource getInfoSource() {
        return infoSource;
    }

    public int getBuriedness() {
        return buriedness;
    }

    public int getLastUpdateTime() {
        return lastUpdateTime;
    }

    public void setBuriedness(int buriedness) {
        this.buriedness = buriedness;
    }

    public void setLastUpdateTime(int lastUpdateTime) {
        this.lastUpdateTime = lastUpdateTime;
    }

    public void setInfoSource(@Nonnull InfoSource infoSource) {
        this.infoSource = infoSource;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BuriednessRecord record = (BuriednessRecord) o;
        return buriedness == record.buriedness;
    }

    @Override
    public int hashCode() {
        return buriedness;
    }

    @Override
    public String toString() {
        return "{" +
                "buriedness=" + buriedness +
                ", lastUpdateTime=" + lastUpdateTime +
                ", infoSource=" + infoSource +
                '}';
    }
}