package hfutinvincible.tool.comm;

import adf.core.agent.info.WorldInfo;
import adf.core.agent.precompute.PrecomputeData;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import hfutinvincible.anno.AgentType;
import hfutinvincible.anno.Autowired;
import hfutinvincible.anno.HfutTool;
import hfutinvincible.common.BitArray;
import hfutinvincible.message.property.AreaEdgePairPassableProperty;
import hfutinvincible.message.property.PathIdsProperty;
import hfutinvincible.message.property.PositionIdProperty;
import hfutinvincible.tool.LifeCycle;
import hfutinvincible.tool.edge.HFUTEdge;
import hfutinvincible.tool.human.StuckType;
import hfutinvincible.tool.misc.OptionsTool;
import org.apache.log4j.Logger;
import rescuecore2.misc.Pair;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@HfutTool
@AgentType({AmbulanceTeam.class, PoliceForce.class, FireBrigade.class})
public class EncoderDecoderTool implements LifeCycle {

    @Autowired private WorldInfo worldInfo;

    @Autowired private Logger logger;

    @Autowired
    PropertyManageTool propertyManageTool;

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

    // MyTest

    /**
     * 各边对的是否互相可达.
     */
    @Nonnull
    public BitArray encodeAreaEdgePairPassableProperty(@Nonnull AreaEdgePairPassableProperty areaEdgePairPassableProperty, @Nonnull BitArray target) throws EncodingException {
        encodeAreaId(areaEdgePairPassableProperty.getArea().getID(), target);
        areaEdgePairPassableProperty.getEdgePairsPassability().forEach(target::addLast);
        return target;
    }

    /**
     * 各边对的是否互相可达.
     */
    @Nonnull
    public AreaEdgePairPassableProperty decodeAreaEdgePairPassableProperty(@Nonnull BitArray target) throws DecodingException {
        Area area = (Area) propertyManageTool.getWorldInfo().getEntity(decodeAreaId(target));
        List<Pair<HFUTEdge, HFUTEdge>> edgePairs = propertyManageTool.getAreaEdgePairsMap().get(area.getID());
        List<Boolean> edgePairsPassability = new ArrayList<>(edgePairs.size());
        for (int i = 0; i < edgePairs.size(); i++) {
            int x = target.takeNAndDecode(1);
            edgePairsPassability.add(x == 1);
        }
        List<Pair<HFUTEdge, HFUTEdge>> passableEdgePairs = new ArrayList<>();
        List<Pair<HFUTEdge, HFUTEdge>> impassableEdgePairs = new ArrayList<>();
        for (int i = 0; i < edgePairsPassability.size(); i++) {
            if (edgePairsPassability.get(i))
                passableEdgePairs.add(edgePairs.get(i));
            else impassableEdgePairs.add(edgePairs.get(i));
        }

        return new AreaEdgePairPassableProperty(area, edgePairsPassability, passableEdgePairs, impassableEdgePairs);
    }

    /**
     * encodeAtId
     */
    @Nonnull
    public BitArray encodeAtId(@Nonnull EntityID atId, @Nonnull BitArray target) throws EncodingException {
        Map<EntityID, Integer> atIdToIndex = propertyManageTool.getAtIdToIndex();
        if (!atIdToIndex.containsKey(atId))
            throw new EncodingException("AtId property not found: " + atId);
        int index = atIdToIndex.get(atId);
        return encodeInt(index, propertyManageTool.getAtIdListBitLength(), target);
    }

    /**
     * decodeAtId
     */
    @Nonnull
    public EntityID decodeAtId(@Nonnull BitArray target) throws DecodingException {
        int index = decodeInt(propertyManageTool.getAtIdListBitLength(), target);
        EntityID atId = propertyManageTool.getAtIdList().get(index);
        if (atId == null) {
            throw new DecodingException("Unknown atId whose index=" + index);
        }
        return atId;
    }
    /**
     * 记录一个 Human 的 Position
     * <p>
     * 注意，Position 可能是 Area，也可能是 AT（如果这个人被装在 AT 上）
     */
    @Nonnull
    public BitArray encodePositionIdProperty(@Nonnull PositionIdProperty positionIdProperty, @Nonnull BitArray target) throws EncodingException {
        boolean isArea = positionIdProperty.isArea();
        if (isArea) {
            target.addLast(isArea);
            encodeAreaId(positionIdProperty.getPosId(),target);
        } else { // is at id
            target.addLast(isArea);
            encodeAtId(positionIdProperty.getPosId(),target);
        }
        return target;
    }

    /**
     * 记录一个 Human 的 Position
     * <p>
     * 注意，Position 可能是 Area，也可能是 AT（如果这个人被装在 AT 上）
     */
    @Nonnull
    public PositionIdProperty decodePositionIdProperty(@Nonnull BitArray target) throws DecodingException {
        boolean isArea = target.takeNAndDecode(1) == 1;
        EntityID posId;
        if (isArea) {
            posId = decodeAreaId(target);
        } else {
            posId = decodeAtId(target);
        }
        return new PositionIdProperty(isArea, posId);
    }


    /**
     * 虽然掩埋深度 Buriedness 也是 Integer，但由于其精度不需要太高，
     * 因此单独采用损失一定精度的压缩编码.
     * 根据 2021 世界赛规则，{@literal buriedness.<type>.severity.value}
     * 的范围是 [0, 200].
     */
    @Nonnull
    public BitArray encodeBuriedness(int buriedness, @Nonnull BitArray target) throws EncodingException {
        int BURIEDNESS_BIT_LENGTH = 6;
        int b = Math.min(buriedness, (2 << (BURIEDNESS_BIT_LENGTH - 1)) - 1);
        return encodeInt(b, BURIEDNESS_BIT_LENGTH, target);
    }

    /**
     * 根据 2021 世界赛规则，{@literal buriedness.<type>.severity.value}
     * 的范围是 [0, 200].
     */
    public int decodeBuriedness(@Nonnull BitArray target) throws DecodingException {
        int BURIEDNESS_BIT_LENGTH = 6;
        return decodeInt(BURIEDNESS_BIT_LENGTH, target);
    }

    /**
     * encodeStuckType
     */
    @Nonnull
    public BitArray encodeStuckType(@Nonnull StuckType stuckType, @Nonnull BitArray target) throws EncodingException {
        return encodeInt(stuckType.ordinal(), 3, target);
    }

    /**
     * decodeStuckType
     */
    @Nonnull
    public StuckType decodeStuckType(@Nonnull BitArray target) throws DecodingException {
        int ordinal = decodeInt(3, target);
        return StuckType.values()[ordinal];
    }

    /**
     * encodeCivilianId
     */
    @Nonnull
    public BitArray encodeCivilianId(@Nonnull EntityID cvId, @Nonnull BitArray target) throws EncodingException {
        return encodeInt(cvId.getValue(), 32, target);
    }

    /**
     * decodeCivilianId
     */
    @Nonnull
    public EntityID decodeCivilianId(@Nonnull BitArray target) throws DecodingException {
        return new EntityID(decodeInt(32, target));
    }

    /**
     * encodeBuildingId
     */
    @Nonnull
    public BitArray encodeBuildingId(@Nonnull EntityID buildingId, @Nonnull BitArray target) throws EncodingException {
        Map<EntityID, Integer> buildingIdToIndex = propertyManageTool.getBuildingIdToIndex();
        if (!buildingIdToIndex.containsKey(buildingId))
            throw new EncodingException("BuildingID property not found: " + buildingId);
        int index = buildingIdToIndex.get(buildingId);
        return encodeInt(index, propertyManageTool.getBuildingIdListBitLength(), target);
    }

    /**
     * decodeBuildingId
     */
    @Nonnull
    public EntityID decodeBuildingId(@Nonnull BitArray target) throws DecodingException {
        int index = decodeInt(propertyManageTool.getBuildingIdListBitLength(), target);
        return propertyManageTool.getBuildingIdList().get(index);
    }

    /**
     * encodeBrokenness
     */
    @Nonnull
    public BitArray encodeBrokenness(int brokenness, @Nonnull BitArray target) throws EncodingException {
        final int BIT_LENGTH = 7; // 用 7 位编码即可
        if (brokenness < 0)
            throw new EncodingException("Invalid input! " + brokenness + " (-)");
        int val = Math.min(brokenness, 100);
        return encodeInt(val, BIT_LENGTH, target);
    }

    /**
     * decodeBrokenness
     */
    public int decodeBrokenness(@Nonnull BitArray target) throws DecodingException {
        final int BIT_LENGTH = 7; // 用 7 位编码即可
        return target.takeNAndDecode(BIT_LENGTH);
    }

    /**
     * encodeRefugeId
     */
    @Nonnull
    public BitArray encodeRefugeId(EntityID refugeId, @Nonnull BitArray target) throws EncodingException {
        Map<EntityID, Integer> refugeIdToIndex = propertyManageTool.getRefugeIdToIndex();
        if (!refugeIdToIndex.containsKey(refugeId))
            throw new EncodingException("RefugeID property not found: " + refugeId);
        int index = refugeIdToIndex.get(refugeId);
        return encodeInt(index, propertyManageTool.getRefugeIdListBitLength(), target);
    }

    /**
     * decodeRefugeId
     */
    @Nonnull
    public EntityID decodeRefugeId(@Nonnull BitArray target) throws DecodingException {
        int index = decodeInt(propertyManageTool.getRefugeIdListBitLength(), target);
        return propertyManageTool.getRefugeIdList().get(index);
    }

    /**
     * encodeBOOLEAN
     */
    @Nonnull
    public BitArray encodeBOOLEAN(boolean is, @Nonnull BitArray target) throws EncodingException {
        int temp = is ? 1 : 0;
        return encodeInt(temp, 1, target);
    }

    /**
     * decodeBOOLEAN
     */
    public boolean decodeBOOLEAN(@Nonnull BitArray target) throws DecodingException {
        return decodeInt(1, target) == 1;
    }

    /**
     * encodePathIdsProperty
     */
    @Nonnull
    public BitArray encodePathIdsProperty(@Nonnull PathIdsProperty pathIdsProperty, @Nonnull BitArray target) throws EncodingException {
        final int TRUNCATE_LENGTH = 16;
        final int LENGTH_BIT_CNT = BitArray.upperBitLength(TRUNCATE_LENGTH);
        List<EntityID> pathIds = pathIdsProperty.getPathIds();

        int len = Math.min(TRUNCATE_LENGTH, pathIds.size());
        encodeInt(len, LENGTH_BIT_CNT, target);
        for (int i = 0; i < len; i ++) {
            EntityID ithAreaId = pathIds.get(i);
            encodeAreaId(ithAreaId, target);
        }
        return target;
    }

    /**
     * decodePathIdsProperty
     */
    @Nonnull
    public PathIdsProperty decodePathIdsProperty(@Nonnull BitArray target) throws DecodingException {
        final int TRUNCATE_LENGTH = 16;
        final int LENGTH_BIT_CNT = BitArray.upperBitLength(TRUNCATE_LENGTH);
        int len = target.takeNAndDecode(LENGTH_BIT_CNT);
        List<EntityID> pathIds = new ArrayList<>();
        for (int i = 0; i < len; i ++) {
            pathIds.add(decodeAreaId(target));
        }
        return new PathIdsProperty(pathIds);
    }

    /**
     * 还能存活多少周期，精确编码 （位数根据 kernel timesteps 计算)
     */
    @Nonnull
    public BitArray encodeExactTimeToDead(int timeToDead, @Nonnull BitArray target) throws EncodingException {
        if (timeToDead > OptionsTool.KERNEL_TIMESTEPS)
            throw new EncodingException("Invalid input! " + timeToDead + " (too large)");
        else if (timeToDead < 0)
            throw new EncodingException("Invalid input! " + timeToDead + " (-)");
        return encodeInt(timeToDead, BitArray.upperBitLength(OptionsTool.KERNEL_TIMESTEPS), target);
    }

    /**
     * 还能存活多少周期，精确编码 （位数根据 kernel timesteps 计算)
     */
    public int decodeExactTimeToDead(@Nonnull BitArray target) throws DecodingException {
        return decodeInt(BitArray.upperBitLength(OptionsTool.KERNEL_TIMESTEPS), target);
    }
}
