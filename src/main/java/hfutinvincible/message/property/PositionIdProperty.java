package hfutinvincible.message.property;

import adf.core.component.communication.util.BitStreamReader;
import hfutinvincible.common.BitArray;
import hfutinvincible.tool.comm.PropertyManageTool;
import hfutinvincible.tool.comm.DecodingException;
import hfutinvincible.tool.comm.EncodingException;
import rescuecore2.worldmodel.EntityID;

import javax.annotation.Nonnull;

/**
 * 记录一个 Human 的 Position
 * <p>
 * 注意，Position 可能是 Area，也可能是 AT（如果这个人被装在 AT 上）
 */
public class PositionIdProperty{

    private boolean isArea;
    private EntityID posId;

    public PositionIdProperty(boolean isArea, @Nonnull EntityID posId) {
        this.isArea = isArea;
        this.posId = posId;
    }

    public boolean isArea() {
        return isArea;
    }

    public EntityID getPosId() {
        return posId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PositionIdProperty that = (PositionIdProperty) o;
        return posId.getValue() == that.posId.getValue();
    }

    @Override
    public int hashCode() {
        return ((Integer) posId.getValue()).hashCode();
    }

    @Override
    public String toString() {
        if(isArea){
            return "[ isArea=" + isArea() + "," + "posAreaId=" + getPosId() + " ]";
        }else{
            return "[ isArea=" + isArea() + "," + "posAtId=" + getPosId() + " ]";
        }

    }
}
