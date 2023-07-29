package hfutinvincible.anno;

import hfutinvincible.brain.AgentBrain;

import java.lang.annotation.*;

/**
 * <p>所有 HfutTool 都需要打上这个注解，让 {@link AgentBrain} 能扫描出所有的 HfutTool,
 * 在初始化时构造对应实例，完成依赖注入，并在合适的时机调用这些类中的生命周期方法。</p>
 * <p>每个 HfutTool 都有一个唯一表示它的名字，可以通过这一注解的参数指定。如果不指定，
 * 每个 HfutTool 的名字就是类的名字。在使用 {@link Autowired} 注入时需要指定 HfutTool 的名字。</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface HfutTool {
    String value() default "";
}
