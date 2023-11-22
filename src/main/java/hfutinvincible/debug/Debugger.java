package hfutinvincible.debug;

import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.ScenarioInfo;
import adf.core.agent.info.WorldInfo;
import hfutinvincible.common.SortedListModel;
import org.apache.log4j.Logger;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.standard.entities.StandardEntityURN;
import rescuecore2.worldmodel.EntityID;


import javax.annotation.Nonnull;
import javax.swing.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class Debugger {

    private static Debugger INSTANCE;

    private Logger logger;

    private Map<EntityID, Integer> indexMap;
    private Vector<AgentVisualizer> agentVisualizers;
    private SortedListModel<StandardEntity> registeredAgents;

    public Debugger() {
        this.logger = TestLogger.getLogger("Debugger");
        this.indexMap = new ConcurrentHashMap<>();
        this.agentVisualizers = new Vector<>();
        this.registeredAgents = new SortedListModel<>((o1, o2) -> {
            StandardEntityURN standardURNo1 = o1.getStandardURN();
            StandardEntityURN standardURNo2 = o2.getStandardURN();
            if (standardURNo1.equals(standardURNo2)) {
                int o1ID = o1.getID().getValue();
                int o2ID = o2.getID().getValue();
                return Integer.compare(o1ID, o2ID);
            } else {
                int o1urn = o1.getURN();
                int o2urn = o2.getURN();
                int compare = Integer.compare(o1urn, o2urn);
                return -compare;
            }
        });
        openDebugger();
    }

    private void openDebugger() {
        JFrame frame = new JFrame("HFUTDebugger");
        JList<StandardEntity> jList = new JList<>(registeredAgents);
        frame.getContentPane().add(new JScrollPane(jList));
        jList.addMouseListener(new MouseListener() {
            @Override
            public void mouseClicked(MouseEvent e) {
                // 双击打开选中列表项所对应 Agent 的 AgentViewer
                if (e.getClickCount() == 2) {
                    int selectedIndex = jList.getSelectedIndex();
                    StandardEntity entity = registeredAgents.getElementAt(selectedIndex);
                    openAgentVisualizerOf(entity);
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {}
            @Override
            public void mouseReleased(MouseEvent e) {}
            @Override
            public void mouseEntered(MouseEvent e) {}
            @Override
            public void mouseExited(MouseEvent e) {}
        });
        frame.setSize(300, 500);
        frame.setVisible(true);
    }

    private void openAgentVisualizerOf(@Nonnull StandardEntity agent) {
        Integer index = indexMap.get(agent.getID());
        if (index != null) {
            AgentVisualizer agentVisualizer = agentVisualizers.get(index);
            JFrame frame = new JFrame(agent.toString());
            frame.getContentPane().add(agentVisualizer);
            frame.setExtendedState(frame.getExtendedState() | JFrame.MAXIMIZED_BOTH);
            frame.setSize(600, 480);
            frame.setVisible(true);
            logger.info("Open agent visualizer for " + agent);
        }
    }

    ////////////////////////////////

    @Nonnull
    private static synchronized Debugger getInstance() {
        if (INSTANCE == null)
            INSTANCE = new Debugger();
        return INSTANCE;
    }

    public synchronized static Painter<?> getPainterOfAgent(AgentInfo ai) {
        Debugger debugger = getInstance();
        EntityID humanId = ai.getID();
        Integer index = debugger.indexMap.get(humanId);
        if (index != null) {
            return debugger.agentVisualizers.get(index).getAnimatedWorldViewer();
        } else {
            debugger.logger.info("Return an empty painter for agent " + ai.me());
            return Painter.EMPTY;
        }
    }

    public synchronized static void registerOrRefreshAgent(
            @Nonnull WorldInfo wi,
            @Nonnull AgentInfo ai,
            @Nonnull ScenarioInfo si
    ) {
        Debugger debugger = getInstance();
        EntityID humanId = ai.getID();
        Integer index = debugger.indexMap.get(humanId);
        if (index != null) {
            debugger.logger.info("Refresh agent visualizer for " + ai.me());
            AgentVisualizer agentVisualizer = debugger.agentVisualizers.get(index);
            agentVisualizer.showTimeStep();
        } else {
            debugger.logger.info("Register agent visualizer for " + ai.me());
            debugger.registeredAgents.add(ai.me());
            // 创建 AgentVisualizer
            AgentVisualizer agentVisualizer = new AgentVisualizer(wi, ai, si);
            agentVisualizer.showTimeStep();
            debugger.agentVisualizers.add(agentVisualizer);
            debugger.indexMap.put(humanId, debugger.agentVisualizers.size() - 1);
        }
    }

    public synchronized static void removeAllNonPersistPaintables() {
        Debugger debugger = getInstance();
        for (AgentVisualizer agentVisualizer : debugger.agentVisualizers) {
            agentVisualizer.getAnimatedWorldViewer()
                    .removeAllNonPersistPaintables();
        }
    }

    /**
     * <p>注册一个 inspector</p>
     * <p>一个 inspector 可以看成一个函数, 输入一个 EntityID, 输出对应 Entity 的一个属性.
     * 比如 HpInspector 就是用于检视智能体生命值的 Inspector, 在输入是一个智能体的 EntityID 时,
     * 应该返回此智能体的 Hp, 如果是其他 Area 之类的 Entity, 则返回空字符串.</p>
     * <p>在点击调试器图上的某个元素时, 会遍历所有已注册的的 inspectors，收集所有的属性显示在左侧栏中.</p>
     *
     * @param agentId 智能体 id (注意不同智能体对世界的认知不同)
     * @param colName 这个 inspector 检视的属性名 (比如 Hp, Damage 等)
     * @param inspectFunc 检视函数, 输入待检视的 Entity, 返回属性值的字符串表示
     */
    public synchronized static void registerInspector(EntityID agentId, String colName, Function<EntityID, String> inspectFunc) {
        Debugger debugger = getInstance();
        Integer index = debugger.indexMap.get(agentId);
        if (index != null) {
            AgentVisualizer agentVisualizer = debugger.agentVisualizers.get(index);
            agentVisualizer.registerInspector(colName, inspectFunc);
        } else {
            debugger.logger.error("Failed to register inspector, because agent " + agentId + " is not registered yet.");
        }
    }
}
