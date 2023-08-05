package hfutinvincible.debug;

import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.ScenarioInfo;
import adf.core.agent.info.WorldInfo;
import rescuecore2.worldmodel.EntityID;

import javax.annotation.Nonnull;
import java.awt.*;
import java.util.function.Function;

public interface Debugger {

    /**
     * 获得一个 Debugger 实例
     */
    static Debugger get() {
        return DebuggerImpl.getInstance();
    }

    Debugger registerOrRefreshAgent(
            @Nonnull WorldInfo wi,
            @Nonnull AgentInfo ai,
            @Nonnull ScenarioInfo si
    );

    /**
     * <p>注册一个 inspector</p>
     * <p>一个 inspector 可以看成一个函数, 输入一个 EntityID, 输出对应 Entity 的一个属性.
     * 比如 HpInspector 就是用于检视智能体生命值的 Inspector, 在输入是一个智能体的 EntityID 时,
     * 应该返回此智能体的 Hp, 如果是其他 Area 之类的 Entity, 则返回空字符串.</p>
     * <p>在点击调试器图上的某个元素时, 会遍历所有已注册的的 inspectors，收集所有的属性显示在左侧栏中.</p>
     * @param myId 智能体 id (注意不同智能体对世界的认知不同)
     * @param colName 这个 inspector 检视的属性名 (比如 Hp, Damage 等)
     * @param inspectFunc 检视函数, 输入待检视的 Entity, 返回属性值的字符串表示
     */
    Debugger registerInspector(EntityID myId, String colName, Function<EntityID, String> inspectFunc);

    /**
     * 画一条直线
     * @param fromX 起点 X 坐标
     * @param fromY 起点 Y 坐标
     * @param toX 终点 X 坐标
     * @param toY 终点 Y 坐标
     * @param color 直线颜色
     */
    Debugger drawLine(int fromX, int fromY, int toX, int toY, Color color);

    /**
     * 画一个圆圈（空心）
     * @param centerX 圆心 X 坐标
     * @param centerY 圆心 Y 坐标
     * @param radius 圆半径
     * @param color 圆圈颜色
     */
    Debugger drawCircle(int centerX, int centerY, int radius, Color color);

    /**
     * 画一个箭头
     * @param fromX 起点 X 坐标
     * @param fromY 起点 Y 坐标
     * @param toX 终点 X 坐标
     * @param toY 终点 Y 坐标
     * @param color 箭头颜色
     */
    Debugger drawArrow(int fromX, int fromY, int toX, int toY, Color color);

    /**
     * 画一个圆饼（实心）
     * @param centerX 圆心 X 坐标
     * @param centerY 圆心 Y 坐标
     * @param radius 圆半径
     * @param color 填充颜色
     */
    Debugger drawPie(int centerX, int centerY, int radius, Color color);

    /**
     * 画一个十字叉
     * @param centerX 中心 X 坐标
     * @param centerY 中心 Y 坐标
     * @param wh 宽高，宽 = 高
     */
    Debugger drawCross(int centerX, int centerY, int wh);
}
