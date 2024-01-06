package hfutinvincible.tool;

public enum UnavailableType {
    /** 到不了目标 */
    block,
    /** 目标被埋 */
    buried,
    /**
     * 应该没有受伤的人
     * （因为我看到的 damage 有误差，因此 damage = 0 并不能说明一个人没受伤，
     * 还需要根据其他信息综合判断。如果能判断受伤，则转换为 {@link UnavailableType#noHurtDefinitely}）
     */
    noHurtNotSure,
    /** 确定没有受伤的人 */
    noHurtDefinitely,
    /** 目标上没人被埋 */
    noBuried,
    /** pf 提取不出动作 */
    pfNoAction,
    /** 竞争失败 */
    competeFail
}
