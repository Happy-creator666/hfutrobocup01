package hfutinvincible.tool.building;
import hfutinvincible.tool.human.InfoSource;

import javax.annotation.Nonnull;

public class RefugeRecord {

    private boolean isFull;
    int occupiedNumOrWaitingNum;
    private int lastUpdateTime;
    private InfoSource infoSource;

    public boolean isFull() {
        return isFull;
    }

    public void setFull(boolean full) {
        isFull = full;
    }

    public int getOccupiedNumOrWaitingNum() {
        return occupiedNumOrWaitingNum;
    }

    public void setOccupiedNumOrWaitingNum(int occupiedNumOrWaitingNum) {
        this.occupiedNumOrWaitingNum = occupiedNumOrWaitingNum;
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
        RefugeRecord record = (RefugeRecord) o;
        if (isFull != record.isFull) return false;
        return occupiedNumOrWaitingNum == record.occupiedNumOrWaitingNum;
    }

    @Override
    public int hashCode() {
        int result = (isFull ? 1 : 0);
        result = 31 * result + occupiedNumOrWaitingNum;
        return result;
    }
}