package hfutinvincible.message;

import adf.core.component.communication.util.BitStreamReader;
import hfutinvincible.anno.HfutMessage;
import hfutinvincible.anno.MessageField;
import hfutinvincible.anno.MessageFieldType;
import hfutinvincible.common.BitArray;
import hfutinvincible.tool.comm.PropertyManageTool;
import hfutinvincible.tool.comm.DecodingException;
import hfutinvincible.tool.comm.EncodingException;
import hfutinvincible.message.property.*;
import hfutinvincible.tool.human.StuckType;
import rescuecore2.standard.entities.Area;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.worldmodel.EntityID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

@HfutMessage
public class AgentConditionMessage extends BaseMessage {

    @MessageField(type = MessageFieldType.AGENT_ID)
    private EntityID agentId;
    @MessageField(type = MessageFieldType.PositionIdProperty)
    private PositionIdProperty posIdProperty;
    @MessageField(type = MessageFieldType.ExactTimeToDead)
    private int timeToDead;
    @MessageField(type = MessageFieldType.Buriedness)
    private int buriedness;
    @MessageField(type = MessageFieldType.StuckType)
    private StuckType stuckType;
    @MessageField(type = MessageFieldType.AREA_ID)
    private EntityID targetAreaId;

    public AgentConditionMessage(
            @Nonnull EntityID agentId,
            boolean isRadio,
            @Nonnull StandardEntity atPos,
            int timeToDead,
            int buriedness,
            @Nonnull StuckType stuckType,
            @Nullable EntityID targetAreaId
    ) {
        super(isRadio);
        // TODO 参数检查
        this.agentId = agentId;
        this.posIdProperty = new PositionIdProperty(atPos instanceof Area, atPos.getID());
        this.timeToDead = timeToDead;
        this.buriedness = buriedness;
        this.stuckType = stuckType;
        this.targetAreaId = targetAreaId;
    }

    public AgentConditionMessage(boolean isRadio, int rawSenderID, int ttl, BitStreamReader bsr) {
        super(isRadio, rawSenderID, ttl, bsr);
    }

    @Nonnull
    public EntityID getAgentId() {
        return this.agentId;
    }

    @Nonnull
    public EntityID getPosId() {
        return posIdProperty.getPosId();
    }

    @Nonnull
    public Integer getTimeToDead() {
        return this.timeToDead;
    }

    @Nonnull
    public Integer getBuriedness() {
        return this.buriedness;
    }

    @Nonnull
    public StuckType getStuckType() {
        return this.stuckType;
    }

    @Nullable
    public EntityID getTargetAreaId() {
        return this.targetAreaId;
    }
}
