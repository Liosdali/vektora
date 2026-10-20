package org.example.vector;

import org.junit.jupiter.api.Test;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ColorReducerTest {
    private static final Color CREAM = new Color(0xF3EBD8);
    private static final Color NAVY = new Color(0x1E2A44);
    private static final Color GOLD = new Color(0xE9B949);

    @Test
    void dropsAReducedColorThatOnlyAveragesSeveralKindsOfEdgeBlends() {
        BufferedImage source = new BufferedImage(160, 160, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = source.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, 160, 160);
        graphics.setColor(CREAM);
        graphics.fillOval(4, 4, 152, 152);
        graphics.setColor(NAVY);
        graphics.setStroke(new BasicStroke(3));
        graphics.drawOval(30, 30, 100, 100);
        graphics.setColor(GOLD);
        graphics.fillOval(40, 40, 80, 80);
        graphics.setColor(NAVY);
        graphics.fillPolygon(new int[] {80, 95, 80, 65}, new int[] {50, 80, 110, 80}, 4);
        graphics.dispose();
        BufferedImage badge = BackgroundRegionProcessor.removeBorderBackground(source);

        List<Color> palette = ColorReducer.withoutBlends(ColorReducer.reduce(badge, 8), badge);

        assertEquals(3, palette.size(), "palette: " + palette);
    }
}
