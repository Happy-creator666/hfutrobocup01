//package hfutinvincible.tool.human;
//
//import adf.core.agent.develop.DevelopData;
//import adf.core.agent.info.AgentInfo;
//import adf.core.agent.info.ScenarioInfo;
//import adf.core.agent.info.WorldInfo;
//import adf.core.agent.module.ModuleManager;
//import hfutinvincible.Tuple2;
//import hfutinvincible.tool.misc.OptionsTool;
//import org.jblas.DoubleMatrix;
//import rescuecore2.standard.entities.Area;
//import rescuecore2.worldmodel.EntityID;
//
//import javax.annotation.Nonnull;
//import javax.annotation.Nullable;
//import java.util.HashMap;
//import java.util.List;
//import java.util.Map;
//
//public class AreaPriorityByHumanTool extends HFUTTool {
//
//    private final Map<EntityID, Double> pfAreaPriorityMapByHuman;
//    private final Map<EntityID, Double> fbAreaPriorityMapByHuman;
//    private final Map<EntityID, Double> atAreaPriorityMapByHuman;
//
//    // Tools
//    private HumanPositionTool humanPositionTool;
//    private StuckInfoTool stuckInfoTool;
//    private TimeToDeadTool timeToDeadTool;
//    private BuriednessTool buriednessTool;
//
//    public AreaPriorityByHumanTool(AgentInfo ai, WorldInfo wi, ScenarioInfo si, ModuleManager moduleManager, DevelopData developData) {
//        super(ai, wi, si, moduleManager, developData);
//        this.pfAreaPriorityMapByHuman = new HashMap<>();
//        this.fbAreaPriorityMapByHuman = new HashMap<>();
//        this.atAreaPriorityMapByHuman = new HashMap<>();
//
//        onInitialize(toolProvider -> {
//            this.humanPositionTool = toolProvider.get("HumanPositionTool");
//            this.stuckInfoTool = toolProvider.get("StuckInfoTool");
//            this.timeToDeadTool = toolProvider.get("TimeToDeadTool");
//            this.buriednessTool = toolProvider.get("BuriednessTool");
//        });
//
//        onUpdateInfo(messageManager -> {
//            // 清空已有记录
//            this.pfAreaPriorityMapByHuman.clear();
//            this.atAreaPriorityMapByHuman.clear();
//            this.fbAreaPriorityMapByHuman.clear();
//        });
//    }
//
//    /**
//     * 获得一个 area 的重要程度，根据其上被卡 humans 的重要程度综合得到
//     */
//    @Nullable
//    public Double getAreaPriorityByHumanForPf(@Nonnull EntityID areaId) {
//        return pfAreaPriorityMapByHuman.get(areaId);
//    }
//
//    /**
//     * 获得一个 area 的重要程度，根据其上受伤 humans 的重要程度综合得到
//     */
//    @Nullable
//    public Double getAreaPriorityByHumanForAt(@Nonnull EntityID areaId) {
//        return atAreaPriorityMapByHuman.get(areaId);
//    }
//
//    /**
//     * 获得一个 area 的重要程度，根据其上被埋 humans 的重要程度综合得到
//     */
//    @Nullable
//    public Double getAreaPriorityByHumanForFb(@Nonnull EntityID areaId) {
//        return fbAreaPriorityMapByHuman.get(areaId);
//    }
//
//    /**
//     * 返回二元组，第一个元素为 N x 2 的矩阵，第二个元素为列名构成的 list
//     * <ol>
//     *     <li>这个 area 上被卡的人的重要程度
//     *     <li>这个 area 上有没有人被卡，1 表示有人被卡，0 表示没有
//     */
//    public Tuple2<DoubleMatrix, List<String>> getHumanPriorityForPf(@Nonnull List<Area> candidates) {
//
//        // 每个区域 priority 初始值为 0
//        candidates.forEach(c -> pfAreaPriorityMapByHuman.put(c.getID(), 0.0));
//
//        for (var entry : humanPositionTool.recordMap.entrySet()) {
//            EntityID posAreaId = entry.getValue().getHumanPosId();
//            if (pfAreaPriorityMapByHuman.get(posAreaId) == null) // 不是候选
//                continue;
//            EntityID humanId = entry.getKey();
//            StuckType stuckType = stuckInfoTool.getStuckType(humanId);
//            // 这个人被卡了
//            if (stuckType != null && !stuckType.equals(StuckType.NOT_STUCK_FROM_OTHERS) && !stuckType.equals(StuckType.NOT_STUCK_FROM_MYSELF)) {
//                Integer timeToDead = timeToDeadTool.getTimeToDeadNowEstimate(humanId);
//                if (timeToDead == null) // 如果不知道 timeToDead
//                    timeToDead = OptionsTool.TIME_TO_DEAD_UNKNOWN;
//                Integer buriedness = buriednessTool.getBuriedness(humanId);
//                if (buriedness == null)
//                    buriedness = 0; //  如果不知道 buriedness，默认为 0
//                // 每个被卡的人都使指标 +1 至少
//                // 根据 timeToDead 和 buriedness 确定增量
//                // 保证被卡的人多的 area 一定比被卡的人少的 area 更重要
//                // 被卡的人一样多，则看 timeToDead 和 buriedness
//                double newVal = pfAreaPriorityMapByHuman.get(posAreaId) + 1.0 + fun(timeToDead - buriedness);
//                pfAreaPriorityMapByHuman.put(posAreaId, newVal);
//            }
//        }
//
//        // 结果放到矩阵里
//        DoubleMatrix resultMatrix = DoubleMatrix.zeros(candidates.size(), 2);
//        for (int i = 0; i < candidates.size(); i ++) {
//            Area ithCandidate = candidates.get(i);
//            double priority = pfAreaPriorityMapByHuman.get(ithCandidate.getID());
//            resultMatrix.put(i, 0, priority);
//            resultMatrix.put(i, 1, priority > 0.0 ? 1.0 : 0.0);
//        }
//
//        return Tuple2.of(resultMatrix, List.of("Priority of stuck human", "Has stuck human?"));
//    }
//
//    /**
//     * 返回二元组，第一个元素为 N x 2 的矩阵，第二个元素为列名构成的 list
//     * <ol>
//     *     <li>这个 area 上受伤的人的重要程度
//     *     <li>这个 area 上有没有人受伤，1 表示有人被受伤，0 表示没有人受伤
//     */
//    public Tuple2<DoubleMatrix, List<String>> getHumanPriorityForAt(@Nonnull List<Area> candidates) {
//
//        // 每个区域 priority 初始值为 0
//        candidates.forEach(c -> atAreaPriorityMapByHuman.put(c.getID(), 0.0));
//
//        for (var entry : humanPositionTool.recordMap.entrySet()) {
//            EntityID posAreaId = entry.getValue().getHumanPosId();
//            if (atAreaPriorityMapByHuman.get(posAreaId) == null) // 不是候选
//                continue;
//            EntityID humanId = entry.getKey();
//            Integer timeToDead = timeToDeadTool.getTimeToDeadNowEstimate(humanId);
//            if (timeToDead == null) // 如果不知道 timeToDead
//                timeToDead = OptionsTool.TIME_TO_DEAD_UNKNOWN;
//            if (timeToDead < OptionsTool.KERNEL_TIMESTEPS) { // 这个人受伤了
//                Integer buriedness = buriednessTool.getBuriedness(humanId);
//                if (buriedness == null)
//                    buriedness = 0; //  如果不知道 buriedness，默认为 0
//                // 根据 timeToDead 和 buriedness 确定增量
//                // 和 StuckHumanConditionCriterion 相比，没有每个人至少 + 1.0
//                // 因为 AT 并不像 PF 能以很低的成本同时救两个人 TODO 调参数
//                double newVal = atAreaPriorityMapByHuman.get(posAreaId) + 1.0 + fun(timeToDead - buriedness);
//                atAreaPriorityMapByHuman.put(posAreaId, newVal);
//            }
//        }
//
//        // 结果放到矩阵里
//        DoubleMatrix resultMatrix = DoubleMatrix.zeros(candidates.size(), 2);
//        for (int i = 0; i < candidates.size(); i ++) {
//            Area ithCandidate = candidates.get(i);
//            double priority = atAreaPriorityMapByHuman.get(ithCandidate.getID());
//            resultMatrix.put(i, 0, priority);
//            resultMatrix.put(i, 1, priority > 0.0 ? 1.0 : 0.0);
//        }
//
//        return Tuple2.of(resultMatrix, List.of("Priority of hurt human", "Has hurt human?"));
//    }
//
//    /**
//     * 返回二元组，第一个元素为 N x 2 的矩阵，第二个元素为列名构成的 list
//     * <ol>
//     *     <li>这个 area 上被埋的人的重要程度
//     *     <li>这个 area 上有没有人被埋，1 表示有人被埋，0 表示没有
//     */
//    public Tuple2<DoubleMatrix, List<String>> getHumanPriorityForFb(@Nonnull List<Area> candidates) {
//
//        // 每个区域 priority 初始值为 0
//        candidates.forEach(c -> fbAreaPriorityMapByHuman.put(c.getID(), 0.0));
//
//        for (var entry : humanPositionTool.recordMap.entrySet()) {
//            EntityID posAreaId = entry.getValue().getHumanPosId();
//            if (fbAreaPriorityMapByHuman.get(posAreaId) == null) // 不是候选
//                continue;
//            EntityID humanId = entry.getKey();
//            Integer buriedness = buriednessTool.getBuriedness(humanId);
//            if (buriedness == null)
//                buriedness = 0; //  如果不知道 buriedness，默认为 0
//            if (buriedness > 0) { // 这个人受伤了
//                Integer timeToDead = timeToDeadTool.getTimeToDeadNowEstimate(humanId);
//                if (timeToDead == null) // 如果不知道 timeToDead
//                    timeToDead = OptionsTool.TIME_TO_DEAD_UNKNOWN;
//                // 根据 timeToDead 和 buriedness 确定增量
//                // 和 StuckHumanConditionCriterion 相比，每个人至少 + 0.5 而不是 1.0
//                // 因为在同一地点的两个人对 FB 来说，移动成本较低，但仍然要花时间分别救人 TODO 调参数
//                double newVal = fbAreaPriorityMapByHuman.get(posAreaId) + 1.0 + fun(timeToDead - buriedness);
//                fbAreaPriorityMapByHuman.put(posAreaId, newVal);
//            }
//        }
//
//        // 结果放到矩阵里
//        DoubleMatrix resultMatrix = DoubleMatrix.zeros(candidates.size(), 2);
//        for (int i = 0; i < candidates.size(); i ++) {
//            Area ithCandidate = candidates.get(i);
//            double priority = fbAreaPriorityMapByHuman.get(ithCandidate.getID());
//            resultMatrix.put(i, 0, priority);
//            resultMatrix.put(i, 1, priority > 0.0 ? 1.0 : 0.0);
//        }
//
//        return Tuple2.of(resultMatrix, List.of("Priority of buried humans", "Has buried human?"));
//    }
//
//    // 一个乱取的评估函数，需要改
//    private double fun(int timeToDead) {
//        if (timeToDead < OptionsTool.TIME_TO_DEAD_A) // 马上就要死了，没有必要救
//            return 0;
//        else if (timeToDead < OptionsTool.TIME_TO_DEAD_B) // 上升部分
//            return (1.0 / (OptionsTool.TIME_TO_DEAD_B - OptionsTool.TIME_TO_DEAD_A)) * (timeToDead - OptionsTool.TIME_TO_DEAD_A);
//        else if (timeToDead < OptionsTool.TIME_TO_DEAD_C) // 下降部分
//            return OptionsTool.TIME_TO_DEAD_D + (OptionsTool.TIME_TO_DEAD_C - timeToDead) * (1.0 - OptionsTool.TIME_TO_DEAD_D) / (OptionsTool.TIME_TO_DEAD_C - OptionsTool.TIME_TO_DEAD_B);
//        else return OptionsTool.TIME_TO_DEAD_D; // 还能活很久的人所在区域，优先级为一个定值
//    }
//}