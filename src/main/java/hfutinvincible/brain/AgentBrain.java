package hfutinvincible.brain;

import adf.core.agent.action.common.ActionRest;
import adf.core.agent.communication.MessageManager;
import adf.core.agent.develop.DevelopData;
import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.ScenarioInfo;
import adf.core.agent.info.WorldInfo;
import adf.core.agent.module.ModuleManager;
import adf.core.agent.precompute.PrecomputeData;
import adf.core.component.extaction.ExtAction;
import hfutinvincible.anno.*;
import hfutinvincible.common.ReflectUtil;
import hfutinvincible.debug.Debugger;
import hfutinvincible.debug.TestLogger;
import hfutinvincible.tool.CalcContext;
import hfutinvincible.tool.LifeCycle;
import org.apache.log4j.Logger;
import rescuecore2.standard.entities.Human;
import rescuecore2.worldmodel.EntityID;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;
import java.util.function.Predicate;

public class AgentBrain extends ExtAction {

    private static final String TOOL_PACKAGE = "hfutinvincible.tool";

    private Map<String, Object> toolMap;
    private List<Object> precomputeOrder;
    private List<Object> resumeOrder;
    private List<Object> preparateOrder;
    private List<Object> calcOrder;
    private List<Object> afterCalcOrder;
    private List<Object> updateInfoOrder;
    private final Logger logger;

    public AgentBrain(AgentInfo ai, WorldInfo wi, ScenarioInfo si, ModuleManager moduleManager, DevelopData developData) {
        super(ai, wi, si, moduleManager, developData);
        logger = TestLogger.getLogger("AgentBrain/" + agentInfo.me());
        toolMap = new HashMap<>();
        try {
            // 将指定包路径下所有带有 @Hfutool 注解
            // 并且 AgentType 包含当前智能体类型的类收集到 toolClassList 中
            Class<?> myType = agentInfo.me().getClass();
            Predicate<Class<?>> classFilter = clazz -> {
                if (!clazz.isAnnotationPresent(HfutTool.class))
                    return false;
                if (clazz.isAnnotationPresent(Disabled.class)) // 这个 tool 已被禁用
                    return false;
                AgentType agentType = clazz.getAnnotation(AgentType.class);
                for (Class<? extends Human> allowedType : agentType.value()) {
                    if (myType.isAssignableFrom(allowedType))
                        return true;
                }
                return false;
            };
            List<Class<Object>> toolClassList = ReflectUtil.scanAllClasses(TOOL_PACKAGE, classFilter);
            logger.debug(toolClassList);
            // 对 toolClassList 中每个 Class，调用其无参构造函数创建实例加入 toolMap
            for (Class<?> toolClass : toolClassList) {
                if (!LifeCycle.class.isAssignableFrom(toolClass)) {
                    throw new RuntimeException("A class annotated with @HfutTool must also implement LifeCycle interface.");
                }
                HfutTool hfutToolAnno = toolClass.getAnnotation(HfutTool.class);
                Object tool = toolClass.getDeclaredConstructor().newInstance();
                String toolName = hfutToolAnno.value().isEmpty()
                        ? toolClass.getSimpleName()
                        : hfutToolAnno.value();
                toolMap.put(toolName, tool);
            }
            // 对 toolMap 中的每个 tool
            // 扫描其所有 field，如果带有 @Autowired 注解，则注入值
            for (Object tool : toolMap.values()) {
                Field[] fields = tool.getClass().getDeclaredFields();
                for (Field field : fields) {
                    if (!field.isAnnotationPresent(Autowired.class))
                        continue;
                    field.setAccessible(true);
                    Autowired autowiredAnno = field.getAnnotation(Autowired.class);
                    Class<?> fieldClass = field.getType();
                    if (fieldClass.equals(WorldInfo.class)) {
                        field.set(tool, wi);
                    } else if (fieldClass.equals(AgentInfo.class)) {
                        field.set(tool, ai);
                    } else if (fieldClass.equals(ScenarioInfo.class)) {
                        field.set(tool, si);
                    } else if (fieldClass.equals(ModuleManager.class)) {
                        field.set(tool, moduleManager);
                    } else if (fieldClass.equals(DevelopData.class)) {
                        field.set(tool, developData);
                    } else if (fieldClass.equals(Logger.class)) {
                        String loggerName = "HFUTTool/" + tool.getClass().getSimpleName() + "/" + agentInfo.me();
                        field.set(tool, TestLogger.getLogger(loggerName));
                    } else {// 尝试注入的 field 类型没有标注为 Hfutool
                        if (!fieldClass.isAnnotationPresent(HfutTool.class)) {
                            throw new RuntimeException("Failed to inject field " + field);
                        }
                        HfutTool hfutToolAnno = fieldClass.getAnnotation(HfutTool.class);
                        String toolName = !autowiredAnno.value().isEmpty()
                                ? autowiredAnno.value()
                                : hfutToolAnno.value().isEmpty()
                                ? fieldClass.getSimpleName()
                                : hfutToolAnno.value();
                        Object tool2 = toolMap.get(toolName);
                        if (tool2 == null) {
                            throw new RuntimeException("Failed to inject field " + field);
                        }
                        field.set(tool, tool2);
                    }
                }
            }
            //////// 拓扑排序，计算每个阶段的计算顺序
            // TODO 仅通过方法名找方法，没有考虑重载
            Set<String> visited = new HashSet<>();
            // 计算 precompute 顺序
            precomputeOrder = new ArrayList<>();
            visited.clear();
            for (String toolName : toolMap.keySet())
                if (!visited.contains(toolName))
                    topoDfs(toolName, visited, "precompute", precomputeOrder);
            // 计算 resume 顺序
            resumeOrder = new ArrayList<>();
            visited.clear();
            for (String toolName : toolMap.keySet())
                if (!visited.contains(toolName))
                    topoDfs(toolName, visited, "resume", resumeOrder);
            // 计算 preparate 顺序
            preparateOrder = new ArrayList<>();
            visited.clear();
            for (String toolName : toolMap.keySet())
                if (!visited.contains(toolName))
                    topoDfs(toolName, visited, "preparate", preparateOrder);
            // 计算 calc 顺序
            calcOrder = new ArrayList<>();
            visited.clear();
            for (String toolName : toolMap.keySet())
                if (!visited.contains(toolName))
                    topoDfs(toolName, visited, "calc", calcOrder);
            // 计算 afterCalc 顺序
            afterCalcOrder = new ArrayList<>();
            visited.clear();
            for (String toolName : toolMap.keySet())
                if (!visited.contains(toolName))
                    topoDfs(toolName, visited, "afterCalc", afterCalcOrder);
            // 计算 updateInfo 顺序
            updateInfoOrder = new ArrayList<>();
            visited.clear();

            for (String toolName : toolMap.keySet())
                if (!visited.contains(toolName))
                    topoDfs(toolName, visited, "updateInfo", updateInfoOrder);
        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            logger.error(sw.toString());
            throw new RuntimeException(e);
        }
    }

    private void topoDfs(String thisToolName, Set<String> visited, String methodName, List<Object> topoOrder) {
        visited.add(thisToolName);
        Object tool = toolMap.get(thisToolName);
        // 计算这个 tool 这个方法依赖的其他 tool
        if (tool == null)
            throw new RuntimeException("IMPOSSIBLE!");
        List<String> dependencies = new ArrayList<>();
        for (Method method : tool.getClass().getDeclaredMethods()) {
            // 不是正在处理的方法
            if (!method.getName().equals(methodName))
                continue;
            // 没有指定依赖关系
            if (!method.isAnnotationPresent(After.class))
                continue;
            // 加入依赖
            After afterAnno = method.getAnnotation(After.class);
            dependencies.addAll(List.of(afterAnno.value()));
            break; // 应该只有一个满足要求的方法
        }
        for (String toolName : dependencies) {
            if (!visited.contains(toolName))
                topoDfs(toolName, visited, methodName, topoOrder);
        }
        topoOrder.add(tool);
    }

    @Override
    public ExtAction setTarget(EntityID targets) {
        return this; // Do nothing
    }

    @Override
    public ExtAction precompute(PrecomputeData precomputeData) {
        try {
            logger.info("------------------------ Start precompute ------------------------");
            for (Object tool : precomputeOrder) {
                logger.info("Start precompute " + tool + "...");
                ((LifeCycle) tool).precompute(precomputeData);
                logger.info("" + tool + "precompute finished!");
            }
            logger.info("------------------------ Precompute finished ------------------------");
        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            logger.error(sw.toString());
        }
        return this;
    }

    @Override
    public ExtAction resume(PrecomputeData precomputeData) {
        try {
            logger.info("------------------------ Start resume ------------------------");
            for (Object tool : resumeOrder) {
                logger.info("Start resume " + tool + "...");
                ((LifeCycle) tool).resume(precomputeData);
                logger.info("" + tool + "resume finished!");
            }
            Debugger.registerOrRefreshAgent(worldInfo, agentInfo, scenarioInfo);
            logger.info("Debugger loaded.");
            logger.info("------------------------ Resume finished ------------------------");
        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            logger.error(sw.toString());
        }
        return this;
    }

    @Override
    public ExtAction preparate() {
        try {
            logger.info("------------------------ Start preparate ------------------------");
            for (Object tool : preparateOrder) {
                logger.info("Start preparate " + tool + "...");
                ((LifeCycle) tool).preparate();
                logger.info("" + tool + "preparate finished!");
            }
            Debugger.registerOrRefreshAgent(worldInfo, agentInfo, scenarioInfo);
            logger.info("Debugger loaded.");
            logger.info("------------------------ Preparate finished ------------------------");
        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            logger.error(sw.toString());
        }
        return this;
    }

    @Override
    public ExtAction updateInfo(MessageManager messageManager) {
        try {
            logger.info("------------------------ Start updateInfo ------------------------");
            for (Object tool : updateInfoOrder) {
                logger.info("Start updateInfo " + tool + "...");
                ((LifeCycle) tool).updateInfo();
                ((LifeCycle) tool).updateInfo(messageManager);
                logger.info("" + tool + "updateInfo finished!");
            }
            logger.info("------------------------ UpdateInfo finished ------------------------");
        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            logger.error(sw.toString());
        }
        return this;
    }

    @Override
    public ExtAction calc() {
        try {
            logger.info("---------------- Start calculating... [Timestep: " + agentInfo.getTime() + "] ----------------");
            logger.info("Current area: " + agentInfo.getPositionArea());
            logger.info("My Position: (" + agentInfo.getX() + ", " + agentInfo.getY() + ")");
            Debugger.removeAllNonPersistPaintables();
            CalcContext context = new CalcContext();
            for (Object tool : calcOrder) {
                ((LifeCycle) tool).calc(context);
                if (context.resultAction != null) {
                    logger.info("get result from tool=" + tool);
                    break;
                }
            }
            // 如果所有 Tool 都没有返回有效的 action，则本周期休息
            if (context.resultAction == null) {
                context.resultAction = new ActionRest();
            }
            logger.info("final action=" + context.resultAction);
            this.result = context.resultAction;
            logger.info("------------------------ Calc finished ------------------------");
            logger.info("------------------------ Start afterCalc ------------------------");
            for (Object tool : afterCalcOrder) {
                logger.info("Start afterCalc " + tool + "...");
                ((LifeCycle) tool).afterCalc(context);
                logger.info("" + tool + "afterCalc finished!");
            }
            logger.info("------------------------ AfterCalc finished ------------------------");
            Debugger.registerOrRefreshAgent(worldInfo, agentInfo, scenarioInfo);
        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            logger.error(sw.toString());
        }
        return this;
    }
}
