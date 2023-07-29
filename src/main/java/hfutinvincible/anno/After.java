package hfutinvincible.anno;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <p>用于指定一个 HfutTool 中一个生命周期方法应该在另一个 HfutTool 的同一方法之后执行。</p>
 * <p>一个例子：假设 {@code RescueMySelfTool} 控制警察在 HP 较低时回到 Refuge；
 * {@code ClearBlockadeOnRoad} 控制警察清理路上的障碍，显然前者优先级较高。
 * 也就是说，两个类中的 calc 函数应该先执行。此时，可以在 {@code ClearBlockadeOnRoad}
 * 类的 calc 函数上加上 {@code @After({"RescueMySelfTool"})}。</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface After {
    String[] value();
}
