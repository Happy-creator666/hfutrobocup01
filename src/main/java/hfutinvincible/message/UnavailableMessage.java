package hfutinvincible.message;

import adf.core.component.communication.util.BitStreamReader;
import hfutinvincible.anno.HfutMessage;
import hfutinvincible.anno.MessageField;
import hfutinvincible.anno.MessageFieldType;
import hfutinvincible.common.BitArray;
import hfutinvincible.tool.UnavailableType;
import hfutinvincible.tool.comm.PropertyManageTool;
import rescuecore2.worldmodel.EntityID;

import javax.annotation.Nonnull;

@HfutMessage
public class UnavailableMessage extends BaseMessage {

    @MessageField(type = MessageFieldType.AREA_ID)
    private EntityID unavailableTarget;
    @MessageField(type = MessageFieldType.UNAVAILABLE_TYPE)
    private UnavailableType unavailableType;

    public UnavailableMessage(
            boolean isRadio,
            @Nonnull EntityID unavailableTarget,
            @Nonnull UnavailableType unavailableType
    ) {
        super(isRadio);
        this.unavailableTarget = unavailableTarget;
        this.unavailableType = unavailableType;
    }

    public UnavailableMessage(boolean isRadio, int rawSenderID, int ttl, BitStreamReader bsr) {
        super(isRadio, rawSenderID, ttl, bsr);
    }

    public EntityID getUnavailableTarget() {
        return unavailableTarget;
    }

    public UnavailableType getUnavailableType() {
        return unavailableType;
    }

    @Nonnull
    @Override
    public String getCheckKey() {
        return "UTM" + getUnavailableTarget() + getUnavailableType();
    }
}
