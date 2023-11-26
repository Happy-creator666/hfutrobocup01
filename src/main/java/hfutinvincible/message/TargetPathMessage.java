package hfutinvincible.message;

import adf.core.component.communication.util.BitStreamReader;
import hfutinvincible.anno.HfutMessage;
import hfutinvincible.anno.MessageField;
import hfutinvincible.anno.MessageFieldType;
import hfutinvincible.common.BitArray;
import hfutinvincible.message.property.PathIdsProperty;
import hfutinvincible.tool.comm.PropertyManageTool;
import hfutinvincible.tool.comm.DecodingException;
import hfutinvincible.tool.comm.EncodingException;
import rescuecore2.worldmodel.EntityID;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

/**
 * Voice only
 */
@HfutMessage
public class TargetPathMessage extends BaseMessage {

    @MessageField(type = MessageFieldType.AGENT_ID)
    private EntityID agentId;
    @MessageField(type = MessageFieldType.PathIdsProperty)
    private PathIdsProperty pathIdsProperty;

    private static final int TRUNCATE_LENGTH = 16;
    private static final int LENGTH_BIT_CNT = BitArray.upperBitLength(TRUNCATE_LENGTH);

    public TargetPathMessage(
            @Nonnull EntityID agentId,
            @Nonnull List<EntityID> pathIds
    ) {
        super(false);
        this.agentId = agentId;
        this.pathIdsProperty = new PathIdsProperty(pathIds);
    }

    public TargetPathMessage(boolean isRadio, int rawSenderID, int ttl, BitStreamReader bsr) {
        super(isRadio, rawSenderID, ttl, bsr);
    }

    public EntityID getAgentId() {
        return agentId;
    }

    public List<EntityID> getPathIds() {
        return pathIdsProperty.getPathIds();
    }

    @Nonnull
    @Override
    public String getCheckKey() {
        return "TPM" + getAgentId() + getPathIds();
    }
}