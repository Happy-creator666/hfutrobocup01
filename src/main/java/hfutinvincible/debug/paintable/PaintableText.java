package hfutinvincible.debug.paintable;

import rescuecore2.misc.gui.ScreenTransform;

import java.awt.*;

public class PaintableText implements Paintable {

    private int leftTopX;
    private int leftTopY;
    private String text;
    private Color color;
    private Font font;

    @Override
    public void paint(Graphics2D g, ScreenTransform t) {
        g.setFont(font);
        g.setColor(color);
        FontMetrics metrics = g.getFontMetrics();
        int width = metrics.stringWidth(text);
        int height = metrics.getHeight();
        g.drawString(
                text,
                t.xToScreen(leftTopX) - width / 2,
                t.yToScreen(leftTopY) + height / 2
        );
    }
}
