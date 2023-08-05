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
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.Comparator;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class DebuggerImpl implements Debugger {

    private static DebuggerImpl INSTANCE;

    private Logger logger;

    private Map<EntityID, Integer> indexMap;
    private Vector<AgentVisualizer> agentVisualizers;
    private SortedListModel<StandardEntity> registeredAgents;

    public DebuggerImpl() {
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
    public static synchronized Debugger getInstance() {
        if (INSTANCE == null)
            INSTANCE = new DebuggerImpl();
        return INSTANCE;
    }

    @Override
    public synchronized Debugger registerOrRefreshAgent(
            @Nonnull WorldInfo wi,
            @Nonnull AgentInfo ai,
            @Nonnull ScenarioInfo si
    ) {
        EntityID humanId = ai.getID();
        Integer index = indexMap.get(humanId);
        if (index != null) {
            logger.info("Refresh agent visualizer for " + ai.me());
            AgentVisualizer agentVisualizer = agentVisualizers.get(index);
            agentVisualizer.showTimeStep();
        } else {
            logger.info("Register agent visualizer for " + ai.me());
            registeredAgents.add(ai.me());
            // 创建 AgentVisualizer
            AgentVisualizer agentVisualizer = new AgentVisualizer(wi, ai, si);
            agentVisualizer.showTimeStep();
            agentVisualizers.add(agentVisualizer);
            indexMap.put(humanId, indexMap.size() - 1);
        }
        return this;
    }

    @Override
    public Debugger registerInspector(EntityID agentId, String colName, Function<EntityID, String> inspectFunc) {
        Integer index = indexMap.get(agentId);
        if (index != null) {
            AgentVisualizer agentVisualizer = agentVisualizers.get(index);
            agentVisualizer.registerInspector(colName, inspectFunc);
        } else {
            logger.error("Failed to register inspector, because agent " + agentId + " is not registered yet.");
        }
        return this;
    }

    /**
     * 画一条直线
     *
     * @param fromX 起点 X 坐标
     * @param fromY 起点 Y 坐标
     * @param toX   终点 X 坐标
     * @param toY   终点 Y 坐标
     * @param color 直线颜色
     */
    @Override
    public Debugger drawLine(int fromX, int fromY, int toX, int toY, Color color) {
        return this;
    }

    /**
     * 画一个圆圈（空心）
     *
     * @param centerX 圆心 X 坐标
     * @param centerY 圆心 Y 坐标
     * @param radius  圆半径
     * @param color   圆圈颜色
     */
    @Override
    public Debugger drawCircle(int centerX, int centerY, int radius, Color color) {
        return this;
    }

    /**
     * 画一个箭头
     *
     * @param fromX 起点 X 坐标
     * @param fromY 起点 Y 坐标
     * @param toX   终点 X 坐标
     * @param toY   终点 Y 坐标
     * @param color 箭头颜色
     */
    @Override
    public Debugger drawArrow(int fromX, int fromY, int toX, int toY, Color color) {
        return this;
    }

    /**
     * 画一个圆饼（实心）
     *
     * @param centerX 圆心 X 坐标
     * @param centerY 圆心 Y 坐标
     * @param radius  圆半径
     * @param color   填充颜色
     */
    @Override
    public Debugger drawPie(int centerX, int centerY, int radius, Color color) {
        return this;
    }

    /**
     * 画一个十字叉
     *
     * @param centerX 中心 X 坐标
     * @param centerY 中心 Y 坐标
     * @param wh      宽高，宽 = 高
     */
    @Override
    public Debugger drawCross(int centerX, int centerY, int wh) {
        return this;
    }
}
