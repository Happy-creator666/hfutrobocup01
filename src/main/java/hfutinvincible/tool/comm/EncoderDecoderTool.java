package hfutinvincible.tool.comm;

import adf.core.agent.info.WorldInfo;
import adf.core.agent.precompute.PrecomputeData;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import hfutinvincible.anno.AgentType;
import hfutinvincible.anno.Autowired;
import hfutinvincible.anno.HfutTool;
import hfutinvincible.common.BitArray;
import hfutinvincible.tool.LifeCycle;
import org.apache.log4j.Logger;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;

import javax.annotation.Nonnull;
import java.util.Comparator;
import java.util.List;

@HfutTool
@AgentType({AmbulanceTeam.class, PoliceForce.class, FireBrigade.class})
public class EncoderDecoderTool implements LifeCycle {

    @Autowired private WorldInfo worldInfo;

    @Autowired private Logger logger;

    /**
     * 一个从 Agent 的 {@link EntityID} 到其 index 的双射，用于压缩编码
     */
    private BiMap<EntityID, Integer> agentIdToIndex;

    /**
     * 在压缩编码后，需要用多少 bits 编码一个 Agent 的 {@link EntityID}
     */
    private int agentIdBitLength = -1;

    /**
     * 一个从 {@link Area} 的 {@link EntityID} 到其 index 的双射，用于压缩编码
     */
    private BiMap<EntityID, Integer> areaIdToIndex;

    /**
     * 在压缩编码后，需要用多少 bits 编码一个 {@link Area} 的 {@link EntityID}
     */
    private int areaIdBitLength = -1;

    @Override
    public void resume(PrecomputeData precomputeData) {
        init();
    }

    @Override
    public void preparate() {
        init();
    }

    private void init() {
        // 获得所有的 agentIds，然后按 int 值的顺序排序
        List<EntityID> agentIds = worldInfo.getAllEntities().stream()
                .filter(e -> e instanceof AmbulanceTeam || e instanceof PoliceForce || e instanceof FireBrigade)
                .map(StandardEntity::getID)
                .sorted(Comparator.comparingInt(EntityID::getValue))
                .toList();
        agentIdBitLength = upperBitLength(agentIds.size() - 1);
        agentIdToIndex = HashBiMap.create();
        for (int i = 0; i < agentIds.size(); i ++) {
            EntityID agentId = agentIds.get(i);
            agentIdToIndex.put(agentId, i);
        }
        logger.info("agentIdToIndex=" + agentIdToIndex);

        // 获得所有的 areaIds，然后按 int 值的顺序排序
        List<EntityID> areaIds = worldInfo.getAllEntities().stream()
                .filter(e -> e instanceof Area)
                .map(StandardEntity::getID)
                .sorted(Comparator.comparingInt(EntityID::getValue))
                .toList();
        areaIdBitLength = upperBitLength(areaIds.size() - 1);
        areaIdToIndex = HashBiMap.create();
        for (int i = 0; i < areaIds.size(); i ++) {
            EntityID areaId = areaIds.get(i);
            areaIdToIndex.put(areaId, i);
        }
        logger.info("areaIdToIndex=" + areaIdToIndex);
    }

    /**
     * 返回编码一个 [0, val] 的数最多需要多少 bits
     */
    public static int upperBitLength(int val) {
        if (val < 0) {
            return 0;
        }
        if (val == 0)
            return 1;
        int length = 0;
        while (val > 0) {
            length++;
            val = val >> 1;
        }
        return length;
    }

    /**
     * 编码一个非负的 int，并将编码得到的 bits 加入目标 {@link BitArray}
     * @param value 待编码的 int，要求非负
     * @param bitLength 编码到几个 bits 里
     * @param target 编码得到的 bits 加入的目标 {@link BitArray}
     * @return 返回参数 {@code target}，但被修改过（加入了编码得到的 bits）
     */
    @Nonnull
    public BitArray encodeInt(int value, int bitLength, @Nonnull BitArray target) throws EncodingException {
        if (value < 0) {
            throw new EncodingException("negative value " + value + " is not supported");
        }
        int requiredBitLength = upperBitLength(value);
        if (requiredBitLength > bitLength) {
            throw new EncodingException("value " + value + " cannot be encoded to " + bitLength + " bits");
        }
        for (int i = 0; i < bitLength; i++) {
            target.addFirst((value & 0x1) != 0);
            value = value >> 1;
        }
        return target;
    }

    /**
     * 从 {@code target} 中取出 {@code bitLength} 个 bits，并解释为一个非负 int
     * @param bitLength 取出多少个 bits
     * @param target 从哪个 {@link BitArray} 中取出 bits
     * @return 解码得到的非负 int
     */
    public int decodeInt(int bitLength, @Nonnull BitArray target) throws DecodingException {
        return target.takeNAndDecode(bitLength);
    }

    /**
     * 编码一个智能体的 {@link EntityID}，并将编码得到的 bits 加入目标 {@link BitArray}
     * @param agentId 待编码的智能体的 {@link EntityID}
     * @param target 编码得到的 bits 加入的目标 {@link BitArray}
     * @return 返回参数 {@code target}，但被修改过（加入了编码得到的 bits）
     */
    @Nonnull
    public BitArray encodeAgentId(@Nonnull EntityID agentId, @Nonnull BitArray target) throws EncodingException {
        Integer index = agentIdToIndex.get(agentId);
        if (index == null) {
            throw new EncodingException("IMPOSSIBLE! agent " + agentId + " does NOT exists.");
        }
        return encodeInt(index, agentIdBitLength, target);
    }

    /**
     * 从 {@code target} 中取出 {@code agentIdBitLength} 个 bits，并解释为一个 int，
     * 然后将这个 int 作为下标找出对应 Agent 的 {@link EntityID}
     * @param target 从哪个 {@link BitArray} 中取出 bits
     * @return 解码得到的 Agent 的 {@link EntityID}
     */
    @Nonnull
    public EntityID decodeAgentId(@Nonnull BitArray target) throws DecodingException {
        int index = decodeInt(agentIdBitLength, target);
        EntityID agentId = agentIdToIndex.inverse().get(index);
        if (agentId == null) {
            throw new DecodingException("Unknown agentId whose index=" + index);
        }
        return agentId;
    }

    /**
     * 编码一个 {@link Area} 的 {@link EntityID}，并将编码得到的 bits 加入目标 {@link BitArray}
     * @param areaId 待编码的 {@link Area} 的 {@link EntityID}
     * @param target 编码得到的 bits 加入的目标 {@link BitArray}
     * @return 返回参数 {@code target}，但被修改过（加入了编码得到的 bits）
     */
    @Nonnull
    public BitArray encodeAreaId(@Nonnull EntityID areaId, @Nonnull BitArray target) throws EncodingException {
        Integer index = areaIdToIndex.get(areaId);
        if (index == null) {
            throw new EncodingException("IMPOSSIBLE! area " + areaId + " does NOT exists.");
        }
        return encodeInt(index, areaIdBitLength, target);
    }

    /**
     * 从 {@code target} 中取出 {@code areaIdBitLength} 个 bits，并解释为一个 int，
     * 然后将这个 int 作为下标找出对应 {@link Area} 的 {@link EntityID}
     * @param target 从哪个 {@link BitArray} 中取出 bits
     * @return 解码得到的 {@link Area} 的 {@link EntityID}
     */
    @Nonnull
    public EntityID decodeAreaId(@Nonnull BitArray target) throws DecodingException {
        int index = decodeInt(areaIdBitLength, target);
        EntityID areaId = areaIdToIndex.inverse().get(index);
        if (areaId == null) {
            throw new DecodingException("Unknown area whose index= " + index);
        }
        return areaId;
    }
}
