package hfutinvincible.tool.criterion;

public enum CriterionEnum {
    atSelectHereWhoseIdLtMe,
    atSelectHereWhoseIdGtMe,
    pfSelectHereWhoseIdLtMe,
    pfSelectHereWhoseIdGtMe,
    fbSelectHereWhoseIdLtMe,
    fbSelectHereWhoseIdGtMe,
    impassableRatio,
    buildingEntranceProximity,
    priorityOfBuriedHuman,
    numOfBuriedHumansWorthRescuing,
    fbTargetPathPriority,
    atTargetPathPriority,
    /** 其上 cv 的数量 */
    cvNum,
    /** 其上 pf 的数量 */
    pfNum,
    /** 其上 at 的数量 */
    atNum,
    /** 其上 fb 的数量 */
    fbNum,
    /** 受伤的人的个数 */
    hurtHumanNum,
    /** 其上受伤的人的优先级 */
    priorityOfHurtHuman,
    /** 根据 passCost 计算出需要几步才能从这一目标移动到最优的 refuge */
    stepsByPassCostToOptimalRefuge,
    /** 根据 passDistance 计算出需要几步才能移动到这一目标 */
    stepsByPassDistanceToTarget,
    /** 根据 passCost 计算出需要几步才能移动到这一目标 */
    stepsByPassCostToTarget,
    /** 从智能体所在位置到这个 area 一路上的堵塞程度 */
    blockRatio,
    /** 从智能体所在位置到这个 area，一路上所有 area 的 （当前周期数 - 上一次访问周期数）之和，越大越好 */
    totalLastVisitTime,
    /** 从我所在位置到这个 area 一路上和其他 pf 的清理路径有多少比例重合 */
    pfCleanPathOverlapRatio,
    /** 是否为 refuge */
    isRefuge,
    /** 是否为 building */
    isBuilding,
    /** 是否为 road */
    isRoad,
    /**
     * refuge 的空床位情况，有充足空床位，床位紧张两种情况指标值相同
     * ，需要排队则指标值为 - 排队人数
     */
    freeBedsCondition,
    /** 是否为 refuge 入口 */
    isRefugeEntrance,
    /** 和 refuge 入口的邻近程度 */
    refugeEntranceProximity,
    /** 其上被卡的人的优先级 */
    priorityOfStuckHuman,
    /** 最近一次被标记为 unavailable 的时间 */
    isUnavailable,
    /** 是否在我被分派到的分区 */
    isInMyAssignedPartition,
    /** 是否在我当前所在分区 */
    isInMyCurrentPartition,
    /** 一个 area 妨碍 at 和 fb 的程度。如果一个 area 妨碍了很多 at 或 fb 到达某个目标，则此值标会很高 */
    blockTargetPriority,
    /** 如果是上一个周期所选中的目标，则值为上一个周期计算得到的评分，否则为 0 */
    prevScore,
    /** 此 area 上是否有被埋的 human */
    hasBuriedHuman,
    /** 此 area 上所有 human buriedness 的最小值 */
    minBuriedness,
    helpOuchCount,
    isUnavailableBuried,
    isUnavailableBlock,
    isUnavailableNoHurt,
    isUnavailableNoBuried,
    isUnavailablePfNoAction,
}