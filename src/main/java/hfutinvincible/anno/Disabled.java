package hfutinvincible.anno;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <p>用于指定一个 HfutTool 不会被加载。在废弃一个 HfutTool 时，打上 {@link Disabled} 比注释掉整个类更方便。</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Disabled {
}
