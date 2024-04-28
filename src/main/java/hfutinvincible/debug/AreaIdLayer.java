package hfutinvincible.debug;

import rescuecore2.config.Config;
import rescuecore2.misc.gui.ScreenTransform;
import rescuecore2.standard.entities.Area;
import rescuecore2.standard.entities.Building;
import rescuecore2.standard.entities.Road;
import rescuecore2.standard.view.StandardEntityViewLayer;
import rescuecore2.view.Icons;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * 用于显示所有 Area 的 EntityID 的图层
 */
public class AreaIdLayer extends StandardEntityViewLayer<Area> {
    private static final Color ID_COLOR = Color.WHITE;

    private boolean renderBuildingID;
    private boolean renderRoadID;
    private RenderBuildingIDAction renderBuildingIDAction;
    private RenderRoadIDAction renderRoadIDAction;

    public AreaIdLayer() {
        super(Area.class);
        renderBuildingID = true;
        renderRoadID = true;
        renderBuildingIDAction = new RenderBuildingIDAction();
        renderRoadIDAction = new RenderRoadIDAction();
    }

    public void setRenderBuildingID(boolean render) {
        renderBuildingID = render;
        renderBuildingIDAction.update();
    }

    public void setRenderRoadID(boolean render) {
        renderRoadID = render;
        renderRoadIDAction.update();
    }

    @Override
    public void initialise(Config config) {
        super.initialise(config);
        setVisible(false);
    }

    /**
     * 设置右键弹出菜单
     */
    @Override
    public List<JMenuItem> getPopupMenuItems() {
        List<JMenuItem> result = new ArrayList<JMenuItem>();
        result.add(new JMenuItem(renderBuildingIDAction));
        result.add(new JMenuItem(renderRoadIDAction));
        return result;
    }

    @Override
    public String getName() {
        return "AreaID";
    }

    @Override
    public Shape render(Area area, Graphics2D g, ScreenTransform t) {
        if ((area instanceof Building && renderBuildingID) || (area instanceof Road && renderRoadID)) {
            String id = area.getID().toString();
            g.setColor(ID_COLOR);
            FontMetrics metrics = g.getFontMetrics();
            int width = metrics.stringWidth(id);
            int height = metrics.getHeight();
            g.drawString(id, t.xToScreen(area.getX()) - width / 2, t.yToScreen(area.getY()) + height / 2);
        }
        return null;
    }

    private final class RenderBuildingIDAction extends AbstractAction {
        public RenderBuildingIDAction() {
            super("Show building IDs");
            update();
        }

        void update() {
            putValue(Action.SELECTED_KEY, renderBuildingID);
            putValue(Action.SMALL_ICON, renderBuildingID ? Icons.TICK : Icons.CROSS);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            setRenderBuildingID(!renderBuildingID);
            component.repaint();
        }
    }

    private final class RenderRoadIDAction extends AbstractAction {
        public RenderRoadIDAction() {
            super("Show road IDs");
            update();
        }

        void update() {
            putValue(Action.SELECTED_KEY, renderRoadID);
            putValue(Action.SMALL_ICON, renderRoadID ? Icons.TICK : Icons.CROSS);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            setRenderRoadID(!renderRoadID);
            component.repaint();
        }
    }
}
