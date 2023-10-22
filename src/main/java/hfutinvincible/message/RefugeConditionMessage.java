package hfutinvincible.message;

import adf.core.component.communication.util.BitStreamReader;
import hfutinvincible.anno.HfutMessage;
import hfutinvincible.anno.MessageField;
import hfutinvincible.anno.MessageFieldType;
import hfutinvincible.common.BitArray;
import hfutinvincible.tool.comm.PropertyManageTool;
import hfutinvincible.tool.comm.DecodingException;
import hfutinvincible.tool.comm.EncodingException;
import rescuecore2.standard.entities.Refuge;
import rescuecore2.worldmodel.EntityID;

import javax.annotation.Nonnull;

/**
 * 携带有一个 Refuge 的详细信息的 Message
 */
@HfutMessage
public class RefugeConditionMessage extends BaseMessage {

    @MessageField(type = MessageFieldType.RefugeId)
    private EntityID refugeId;

    /** 是否已经满了 */
    @MessageField(type = MessageFieldType.BOOLEAN)
    private boolean isFull;

    /** 如果已经满了，则为排队的人的数量，否则为已占用的床位数，用 5 bits 编码 */
    @MessageField(type = MessageFieldType.INTEGER, bitLength = 5)
    private int occupiedNumOrWaitingNum;

    public RefugeConditionMessage(boolean isRadio, Refuge refuge) {
        super(isRadio);
        this.refugeId = refuge.getID();
        this.isFull = refuge.getBedCapacity() == refuge.getOccupiedBeds();
        this.occupiedNumOrWaitingNum = isFull ? refuge.getWaitingListSize() : refuge.getOccupiedBeds();
    }

    public RefugeConditionMessage(boolean isRadio, int rawSenderID, int ttl, BitStreamReader bsr) {
        super(isRadio, rawSenderID, ttl, bsr);
    }

    public EntityID getRefugeId() {
        return refugeId;
    }

    public boolean isFull() {
        return isFull;
    }

    /**
     * 注意：使用前需要先用 {@link RefugeConditionMessage#isFull()} 检查是否已经占满，只有在没有占满的情况下
     * 返回的才是占用的床位数，否则将返回排队的人的数量
     */
    public int getOccupiedNum() {
        return occupiedNumOrWaitingNum;
    }

    /**
     * 注意：使用前需要先用 {@link RefugeConditionMessage#isFull()} 检查是否已经占满，只有在占满的情况下
     * 返回的才是返回排队的人的数量，否则将返回占用的床位数
     */
    public int getWaitingNum() {
        return occupiedNumOrWaitingNum;
    }

    @Nonnull
    @Override
    public String getCheckKey() {
        return "RCM" + getRefugeId();
    }
}