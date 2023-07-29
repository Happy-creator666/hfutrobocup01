package hfutinvincible.anno;

import rescuecore2.standard.entities.Human;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <p>用于指定一个 {@link HfutTool} 用于哪些类型的智能体.<p/>
 * <p>一个例子：假设希望实现一个 RescueMySelfTool，控制智能体在 HP 较低时回到 Refuge。
 * 显然这一行为应该被所有智能体所共有，因此 AgentType 中应该写警察，消防，救护三种智能体。如下所示：</p>
 * <pre>{@code
 * @HfutTool
 * @AgentType({PoliceForce.class, FireBrigade.class, AmbulanceTeam.class})
 * public class RescueMyselfTool implements LifeCycle {...}
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface AgentType {
    Class<? extends Human>[] value();
}
