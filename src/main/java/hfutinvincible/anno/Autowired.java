package hfutinvincible.anno;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import adf.core.agent.develop.DevelopData;
import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.ScenarioInfo;
import adf.core.agent.info.WorldInfo;
import adf.core.agent.module.ModuleManager;
import org.apache.log4j.Logger;

/**
 * <p>以下是所有可以注入的类型：</p>
 * <ul>
 *     <li>{@link WorldInfo}</li>
 *     <li>{@link AgentInfo}</li>
 *     <li>{@link ScenarioInfo}</li>
 *     <li>{@link ModuleManager}</li>
 *     <li>{@link DevelopData}</li>
 *     <li>{@link Logger}</li>
 *     <li>所有 {@link HfutTool}</li>
 * </ul>
 * <p>下面是一些例子：</p>
 * <pre>{@code
 * // 注入 Server-managed 变量
 * @Autowired
 * private WorldInfo wi;
 * @Autowired
 * private AgentInfo ai;
 * // 注入 HfutTool
 * @Autowired
 * private HFUTMessageTool hfutMessageTool;
 * // 要注入的 HfutTool 的名字和类型名不同, 参数中手工指定名字
 * @Autowired("AutowiredManageTool2")
 * private RefugeManageTool refugeManageTool;
 * }</pre>
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Autowired {
    String value() default "";
}
