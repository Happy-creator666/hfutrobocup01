package hfutinvincible.debug.paintable;

import rescuecore2.misc.gui.ScreenTransform;

import java.awt.*;

public class PaintableText implements Paintable {

    private double x;
    private double y;
    private String text;
    private Color color;
    private Font font;

    public PaintableText(double x, double y, String text, Color color, Font font) {
        this.x = x;
        this.y = y;
        this.text = text;
        this.color = color;
        this.font = font;
    }

    @Override
    public void paint(Graphics2D g, ScreenTransform t) {
        g.setFont(font);
        g.setColor(color);
        FontMetrics metrics = g.getFontMetrics();
        int width = metrics.stringWidth(text);
        int height = metrics.getHeight();
        g.drawString(
                text,
                t.xToScreen(x) - width / 2,
                t.yToScreen(y) + height / 2
        );
    }
}
