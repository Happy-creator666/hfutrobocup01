package hfutinvincible.debug;

import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.ScenarioInfo;
import adf.core.agent.info.WorldInfo;
import rescuecore2.view.LayerViewComponent;
import rescuecore2.view.RenderedObject;
import rescuecore2.view.ViewComponent;
import rescuecore2.view.ViewListener;
import rescuecore2.worldmodel.Entity;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

public class AgentVisualizer extends JPanel {

    private WorldInfo wi;

    private AgentInfo ai;

    private ScenarioInfo si;

    /** 用于显示当前周期 */
    private JLabel timestep;

    private AnimatedWorldViewer animatedWorldViewer;

    boolean isShown;

    public AgentVisualizer(WorldInfo wi, AgentInfo ai, ScenarioInfo si) {
        super(new BorderLayout());
        this.wi = wi;
        this.ai = ai;
        this.si = si;
        // 初始化 AnimatedWorldViewer
        this.animatedWorldViewer = new AnimatedWorldViewer();
        animatedWorldViewer.initialise(this.si.getRawConfig());
        this.isShown = false;
    }

    public synchronized void showTimeStep(int i, WorldInfo wi) {
        if (!this.isShown) {
            this.isShown = true;
            this.timestep = new JLabel("Timestep: " + this.ai.getTime());

            JTabbedPane viewersPane = new JTabbedPane();
            viewersPane.addTab("Animated World Viewer", animatedWorldViewer);

            // 两栏
            JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, entityInspector, viewersPane);
            add(split, BorderLayout.CENTER);
            add(this.timestep, BorderLayout.NORTH);
        }

        this.timestep.setText("Timestep: " + i);
        for (LayerViewComponent viewer : this.viewers.values()) {
            if (viewer instanceof AnimatedWorldViewer)
                ((AnimatedWorldViewer) viewer).updateInfo(agentInfo, worldinfo, scenarioInfo)
                        .updateTools((Map) toolMap);
            viewer.view(worldinfo.getRawWorld());
            viewer.repaint();
        }
    }
}