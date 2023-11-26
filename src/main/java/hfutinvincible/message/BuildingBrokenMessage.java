package hfutinvincible.message;

import adf.core.component.communication.util.BitStreamReader;
import hfutinvincible.anno.HfutMessage;
import hfutinvincible.anno.MessageField;
import hfutinvincible.anno.MessageFieldType;
import rescuecore2.worldmodel.EntityID;

import javax.annotation.Nonnull;

@HfutMessage
public class BuildingBrokenMessage extends BaseMessage {

    /** 受损的 Building 的 EntityID */
    @MessageField(type = MessageFieldType.BUILDING_ID)
    private EntityID buildingId;
    /** 损伤程度*/
    @MessageField(type = MessageFieldType.BROKENNESS)
    private int brokenness;

    public BuildingBrokenMessage(
            boolean isRadio,
            @Nonnull EntityID buildingId,
            int brokenness
    ) {
        super(isRadio);
        this.buildingId = buildingId;
        this.brokenness = brokenness;
    }

    public BuildingBrokenMessage(boolean isRadio, int rawSenderID, int ttl, BitStreamReader bsr) {
        super(isRadio, rawSenderID, ttl, bsr);
    }

    @Nonnull
    public EntityID getBuildingId() {
        return this.buildingId;
    }

    public int getBuildingBrokenness() {
        return this.brokenness;
    }

    @Nonnull
    @Override
    public String getCheckKey() {
        return "BBM" + getBuildingId() + getBuildingBrokenness();
    }
}