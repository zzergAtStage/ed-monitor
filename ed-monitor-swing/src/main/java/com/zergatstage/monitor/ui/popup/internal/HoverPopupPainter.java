package com.zergatstage.monitor.ui.popup.internal;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;

/**
 * Small helper to paint a rounded card with a synthetic drop shadow so we can reuse it across cards.
 */
public final class HoverPopupPainter {

    private HoverPopupPainter() {
    }

    public static void paintCard(Graphics2D g2, Rectangle drawArea, Color background, Color border, int arc, int shadowSize) {
        g2 = (Graphics2D) g2.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int width = drawArea.width;
        int height = drawArea.height;
        int x = drawArea.x;
        int y = drawArea.y;

        for (int i = shadowSize; i >= 1; i--) {
            float ratio = (float) (shadowSize - i + 1) / (shadowSize + 2);
            int alpha = Math.min(90, Math.round(120 * ratio));
            g2.setColor(new Color(0, 0, 0, alpha));
            int offset = shadowSize - i;
            g2.fillRoundRect(x + offset, y + offset, width - offset * 2, height - offset * 2, arc + offset * 2, arc + offset * 2);
        }

        g2.setColor(background);
        g2.fillRoundRect(x + shadowSize, y + shadowSize, width - shadowSize * 2, height - shadowSize * 2, arc, arc);

        g2.setColor(border);
        g2.drawRoundRect(x + shadowSize, y + shadowSize, width - shadowSize * 2, height - shadowSize * 2, arc, arc);
        g2.dispose();
    }
}
