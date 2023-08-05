package hfutinvincible.debug;

import rescuecore2.worldmodel.EntityID;

import java.util.function.Consumer;
import java.util.function.Function;

public interface GlobalDebugger {
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
    void registerInspector(EntityID myId, EntityID colName, Function<EntityID, String> inspectFunc);
}
