package hfutinvincible.tool;

import adf.core.agent.communication.MessageManager;
import adf.core.agent.precompute.PrecomputeData;

public interface LifeCycle {
    public default void precompute(PrecomputeData precomputeData) {}
    public default void resume(PrecomputeData precomputeData) {}
    public default void preparate() {}
    public default void updateInfo() {}
    public default void updateInfo(MessageManager messageManager) {}
    public default void calc(CalcContext context) {}
    public default void afterCalc(CalcContext context) {}
}
