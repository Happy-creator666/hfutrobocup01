package hfutinvincible.message;

import adf.core.component.communication.util.BitStreamReader;
import hfutinvincible.anno.HfutMessage;
import hfutinvincible.anno.MessageField;
import hfutinvincible.anno.MessageFieldType;
import hfutinvincible.common.BitArray;
import hfutinvincible.tool.comm.PropertyManageTool;
import hfutinvincible.tool.comm.DecodingException;
import hfutinvincible.tool.comm.EncodingException;
import rescuecore2.worldmodel.EntityID;

import javax.annotation.Nonnull;

/**
 * AT 和 FB 在发现去目标 Area 的路径被堵住时，就会发送此消息通知 PF 清理
 */
@HfutMessage
public class TargetBlockMessage extends BaseMessage {

    @MessageField(type = MessageFieldType.AGENT_ID)
    private EntityID agentId;
    @MessageField(type = MessageFieldType.AREA_ID)
    private EntityID fromAreaId;
    @MessageField(type = MessageFieldType.AREA_ID)
    private EntityID toAreaId;

    public TargetBlockMessage(
            boolean isRadio,
            @Nonnull EntityID agentId,
            @Nonnull EntityID fromAreaId,
            @Nonnull EntityID toAreaId
    ) {
        super(isRadio);
        this.agentId = agentId;
        this.fromAreaId = fromAreaId;
        this.toAreaId = toAreaId;
    }

    public TargetBlockMessage(boolean isRadio, int rawSenderID, int ttl, BitStreamReader bsr) {
        super(isRadio, rawSenderID, ttl, bsr);
    }

    public EntityID getAgentId() {
        return agentId;
    }

    public EntityID getFromAreaId() {
        return fromAreaId;
    }

    public EntityID getToAreaId() {
        return toAreaId;
    }

    @Nonnull
    @Override
    public String getCheckKey() {
        return "TBM" + getAgentId() + getFromAreaId() + getToAreaId();
    }
}