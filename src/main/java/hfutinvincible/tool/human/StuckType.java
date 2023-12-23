package hfutinvincible.tool.human;

public enum StuckType {
    /** 没有被卡住 */
    NOT_STUCK,
    /** 完全卡在障碍里，或者被障碍封住的 Building 里 */
    STUCK_IN_BLOCKADE_OR_BUILDING,
    /** 移动时卡住 */
    STUCK_WHEN_MOVING,
}