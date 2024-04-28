package hfutinvincible.message;

import adf.core.component.communication.util.BitStreamReader;
import hfutinvincible.anno.HfutMessage;
import hfutinvincible.anno.MessageField;
import hfutinvincible.anno.MessageFieldType;
import hfutinvincible.message.property.*;
import hfutinvincible.tool.human.StuckType;
import org.checkerframework.checker.nullness.qual.NonNull;
import rescuecore2.standard.entities.Area;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.worldmodel.EntityID;

import javax.annotation.Nonnull;

@HfutMessage
public class CvConditionMessage extends BaseMessage {

    @MessageField(type = MessageFieldType.CIVILIAN_ID)
    private EntityID cvId;
    @MessageField(type = MessageFieldType.POSITION_ID_PROPERTY)
    private PositionIdProperty posIdProperty;
    // TODO 看到的 civilian 的 timeToDead 有误差，因此使用 ApproxTimeToDeadProperty 以压缩编码
    @MessageField(type = MessageFieldType.EXACT_TIME_TO_DEAD)
    private int timeToDead;
    @MessageField(type = MessageFieldType.BURIEDNESS)
    private int buriedness;
    @MessageField(type = MessageFieldType.STUCK_TYPE)
    private StuckType stuckType;

    public CvConditionMessage(
            boolean isRadio,
            @Nonnull EntityID cvId,
            @Nonnull StandardEntity cvPos,
            int timeToDead,
            int buriedness,
            @Nonnull StuckType stuckType
    ) {
        super(isRadio);
        this.cvId = cvId;
        this.posIdProperty = new PositionIdProperty(cvPos instanceof Area, cvPos.getID());
        this.timeToDead = timeToDead;
        this.buriedness = buriedness;
        this.stuckType = stuckType;
    }

    public CvConditionMessage(boolean isRadio, int rawSenderID, int ttl, BitStreamReader bsr) {
        super(isRadio, rawSenderID, ttl, bsr);
    }

    @Nonnull
    public EntityID getCvId() {
        assert cvId != null;
        return this.cvId;
    }

    @Nonnull
    public EntityID getPosId() {
        return posIdProperty.getPosId();
    }

    public void setPosId(@Nonnull StandardEntity cvPos) {
        this.posIdProperty = new PositionIdProperty(cvPos instanceof Area, cvPos.getID());
    }

    @NonNull
    public Integer getTimeToDead() {
        return this.timeToDead;
    }

    public void setTimeToDead(@Nonnull Integer timeToDead) {
        this.timeToDead = timeToDead;
    }

    @Nonnull
    public Integer getBuriedness() {
        return this.buriedness;
    }

    public void setBuriedness(@Nonnull Integer buriedness) {
        this.buriedness = buriedness;
    }

    @Nonnull
    public StuckType getStuckType() {
        return this.stuckType;
    }

    public void setStuckType(@Nonnull StuckType stuckType) {
        this.stuckType = stuckType;
    }
}
