package hfutinvincible.message.property;

import adf.core.component.communication.util.BitStreamReader;
import hfutinvincible.common.BitArray;
import hfutinvincible.tool.comm.PropertyManageTool;
import hfutinvincible.tool.comm.DecodingException;
import hfutinvincible.tool.comm.EncodingException;
import rescuecore2.worldmodel.EntityID;

import java.util.Map;

public class AtIdProperty{

    private final EntityID atId;

    public AtIdProperty(EntityID atId) {
        this.atId = atId;
    }

    public EntityID getAtId() {
        return atId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AtIdProperty that = (AtIdProperty) o;
        return atId.getValue() == that.atId.getValue();
    }

    @Override
    public int hashCode() {
        return ((Integer) atId.getValue()).hashCode();
    }
}
