package hfutinvincible.debug;

import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.ScenarioInfo;
import adf.core.agent.info.WorldInfo;
import rescuecore2.view.LayerViewComponent;
import rescuecore2.view.RenderedObject;
import rescuecore2.view.ViewComponent;
import rescuecore2.view.ViewListener;
import rescuecore2.worldmodel.Entity;
import rescuecore2.worldmodel.EntityID;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class AgentVisualizer extends JPanel {

    private WorldInfo wi;

    private AgentInfo ai;

    private ScenarioInfo si;

    /** 用于显示当前周期 */
    private JLabel timestep;

    private AnimatedWorldViewer animatedWorldViewer;

    private InspectorPanel inspectorPanel;

    boolean isShown;

    public AgentVisualizer(WorldInfo wi, AgentInfo ai, ScenarioInfo si) {
        super(new BorderLayout());
        this.wi = wi;
        this.ai = ai;
        this.si = si;
        // 初始化 AnimatedWorldViewer
        this.animatedWorldViewer = new AnimatedWorldViewer((id) -> {
            this.inspectorPanel.setTargetEntityId(id);
        });
        animatedWorldViewer.initialise(this.si.getRawConfig());
        // 初始化 InspectorPanel
        this.inspectorPanel = new InspectorPanel();
        this.isShown = false;
    }

    /**
     * <p>注册一个 inspector</p>
     * <p>一个 inspector 可以看成一个函数, 输入一个 EntityID, 输出对应 Entity 的一个属性.
     * 比如 HpInspector 就是用于检视智能体生命值的 Inspector, 在输入是一个智能体的 EntityID 时,
     * 应该返回此智能体的 Hp, 如果是其他 Area 之类的 Entity, 则返回空字符串.</p>
     * <p>在点击调试器图上的某个元素时, 会遍历所有已注册的的 inspectors，收集所有的属性显示在左侧栏中.</p>
     * @param colName 这个 inspector 检视的属性名 (比如 Hp, Damage 等)
     * @param inspectFunc 检视函数, 输入待检视的 Entity, 返回属性值的字符串表示
     */
    public void registerInspector(String colName, Function<EntityID, String> inspectFunc) {
        this.inspectorPanel.registerInspector(colName, inspectFunc);
    }

    /**
     * 显示当前周期的最新情况
     */
    public synchronized void showTimeStep() {
        try {
            int i = ai.getTime();
            if (!this.isShown) {
                this.isShown = true;
                this.timestep = new JLabel("Timestep: " + this.ai.getTime());

                JTabbedPane viewersPane = new JTabbedPane();
                viewersPane.addTab("Animated World Viewer", animatedWorldViewer);

                // 两栏
                JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, inspectorPanel, viewersPane);
                add(split, BorderLayout.CENTER);
                add(this.timestep, BorderLayout.NORTH);
            }

            this.timestep.setText("Timestep: " + i);
            this.animatedWorldViewer.view(wi.getRawWorld());
            this.animatedWorldViewer.repaint();
        } catch (Exception e) {}
    }

    public AnimatedWorldViewer getAnimatedWorldViewer() {
        return animatedWorldViewer;
    }
}