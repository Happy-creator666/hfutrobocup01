package hfutinvincible.debug;

import java.awt.*;
import java.util.Collection;

import rescuecore2.misc.gui.ScreenTransform;
import rescuecore2.standard.entities.Area;
import rescuecore2.standard.entities.Blockade;
import rescuecore2.standard.view.StandardEntityViewLayer;
import rescuecore2.view.RenderedObject;

public class RoadBlockageLayer extends StandardEntityViewLayer<Blockade> {
    private static final Color BLOCKED_COLOR = new Color(25, 25, 25);
    private static final Color BLOCKED_EDGE_COLOR = Color.black;
    private static final Stroke BLOCKED_EDGE_STROKE = new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER);
    private static final Color CLEANED_COLOR = new Color(0,0,0,0);
    private static final Color CLEANED_EDGE_COLOR = new Color(0,0,0,0);
    private static final Stroke CLEANED_EDGE_STROKE = new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER);


    public RoadBlockageLayer() {
        super(Blockade.class);
    }

    public String getName() {
        return "Road blockages";
    }

    @Override
    public Collection<RenderedObject> render(Graphics2D g, ScreenTransform transform, int width, int height) {
        var result = super.render(g, transform, width, height);
        System.out.println("rerender road blockage layer");
        return result;
    }

    public Shape render(Blockade b, Graphics2D g, ScreenTransform t) {
        if (!b.isApexesDefined()) {
            return null;
        } else {
            int[] apexes = b.getApexes();
            int count = apexes.length / 2;
            int[] xs = new int[count];
            int[] ys = new int[count];

            for(int i = 0; i < count; ++i) {
                xs[i] = t.xToScreen((double)apexes[i * 2]);
                ys[i] = t.yToScreen((double)apexes[i * 2 + 1]);
            }

            Polygon shape = new Polygon(xs, ys, count);
            Area area = (Area) world.getEntity(b.getPosition());
            boolean cleaned = (area == null || !area.getBlockades().contains(b.getID()));
            if (cleaned)
                g.setColor(CLEANED_COLOR);
            else
                g.setColor(BLOCKED_COLOR);
            g.fill(shape);
            if (cleaned) {
                g.setColor(CLEANED_EDGE_COLOR);
                g.setStroke(CLEANED_EDGE_STROKE);
            }
            else {
                g.setColor(BLOCKED_EDGE_COLOR);
                g.setStroke(BLOCKED_EDGE_STROKE);
            }
            g.fill(shape);
            return shape;
        }
    }
}
