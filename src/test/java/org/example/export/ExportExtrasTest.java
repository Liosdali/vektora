package org.example.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import org.example.vector.ImageTracerVectorizer;
import org.example.vector.TraceOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExportExtrasTest {
    private static final String HALF_RED = """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 10 10">
              <path fill="rgb(255,0,0)" d="M 0 0 L 5 0 L 5 10 L 0 10 Z"/>
            </svg>
            """;
    private final SvgExportService service = new SvgExportService();

    @Test
    void rasterizesTheExportedSvgAtTheRequestedScale() throws Exception {
        String svg = service.renderSvg(new IconVariant("Half", "", HALF_RED), new SvgExportSettings(20, 20, 1));

        BufferedImage image = SvgRasterizer.render(svg, 2);

        assertEquals(40, image.getWidth());
        assertEquals(0xffff0000, image.getRGB(5, 20));
        assertEquals(0, image.getRGB(35, 20) >>> 24);
    }

    @Test
    void exportsTransparentPngsAtOneTwoAndFourTimesTheCanvas(@TempDir Path directory) throws Exception {
        service.export(List.of(new IconVariant("Half", "", HALF_RED)), new SvgExportSettings(32, 16, 1),
                directory, ExportFormat.PNG);

        assertEquals(32, ImageIO.read(directory.resolve("half.png").toFile()).getWidth());
        assertEquals(64, ImageIO.read(directory.resolve("half@2x.png").toFile()).getWidth());
        BufferedImage large = ImageIO.read(directory.resolve("half@4x.png").toFile());
        assertEquals(128, large.getWidth());
        assertEquals(64, large.getHeight());
        assertEquals(0, large.getRGB(120, 32) >>> 24);
    }

    @Test
    void exportsAFaviconAndAppIconSet(@TempDir Path directory) throws Exception {
        service.export(List.of(new IconVariant("Half", "", HALF_RED)), new SvgExportSettings(32, 32, 1),
                directory, ExportFormat.ICON_SET);

        for (int size : new int[] {16, 32, 48, 64, 128, 180, 192, 256, 512}) {
            BufferedImage icon = ImageIO.read(directory.resolve("half-" + size + ".png").toFile());
            assertEquals(size, icon.getWidth());
            assertEquals(size, icon.getHeight());
        }
        ByteBuffer ico = ByteBuffer.wrap(Files.readAllBytes(directory.resolve("half.ico"))).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(0, ico.getShort(0));
        assertEquals(1, ico.getShort(2));
        assertEquals(3, ico.getShort(4));
        assertEquals(16, ico.get(6));
        int firstImage = ico.getInt(6 + 12);
        assertEquals((byte) 0x89, ico.get(firstImage));
        assertEquals('P', ico.get(firstImage + 1));
    }

    @Test
    void compactSvgIsMuchSmallerAndLooksTheSame() throws Exception {
        String traced = new ImageTracerVectorizer().vectorize(badge(), TraceOptions.recommended());
        IconVariant variant = new IconVariant("Badge", "", traced);

        String plain = service.renderSvg(variant, new SvgExportSettings(128, 128, 1));
        String compact = service.renderSvg(variant, new SvgExportSettings(128, 128, 1, false, true, -1));

        assertTrue(compact.length() <= plain.length() * 0.7, compact.length() + " vs " + plain.length());
        assertFalse(compact.contains("desc="));
        assertSameLook(SvgRasterizer.render(plain, 2), SvgRasterizer.render(compact, 2));
    }

    @Test
    void roundsCoordinatesToTheChosenDecimals() throws Exception {
        String sharp = """
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 10 10">
                  <path fill="rgb(255,0,0)" d="M 0.123 0 L 5.678 0 L 5.5 9.99 Z"/>
                </svg>
                """;

        String whole = service.renderSvg(new IconVariant("A", "", sharp), new SvgExportSettings(16, 16, 1, false, true, 0));
        String one = service.renderSvg(new IconVariant("A", "", sharp), new SvgExportSettings(16, 16, 1, false, true, 1));

        assertTrue(pathData(whole).matches("[MLZ0-9 ]+"), pathData(whole));
        assertTrue(pathData(one).contains("5.7") && pathData(one).contains("0.1"), pathData(one));
    }

    private static String pathData(String svg) {
        Matcher matcher = Pattern.compile(" d=\"([^\"]*)\"").matcher(svg);
        assertTrue(matcher.find());
        return matcher.group(1);
    }

    /** Equal but for antialiasing where separately drawn neighbors now share one outline. */
    private static void assertSameLook(BufferedImage first, BufferedImage second) {
        long different = 0;
        for (int y = 0; y < first.getHeight(); y++) {
            for (int x = 0; x < first.getWidth(); x++) {
                int a = first.getRGB(x, y);
                int b = second.getRGB(x, y);
                for (int shift = 0; shift < 32; shift += 8) {
                    if (Math.abs((a >>> shift & 0xff) - (b >>> shift & 0xff)) > 24) {
                        different++;
                        break;
                    }
                }
            }
        }
        assertTrue(different <= first.getWidth() * first.getHeight() / 200, different + " pixels differ");
    }

    private static BufferedImage badge() {
        BufferedImage image = new BufferedImage(160, 160, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, 160, 160);
        graphics.setColor(new Color(0xF3EBD8));
        graphics.fillOval(10, 10, 140, 140);
        graphics.setColor(new Color(0x1E2A44));
        graphics.setStroke(new BasicStroke(7));
        graphics.drawOval(10, 10, 140, 140);
        graphics.setColor(new Color(0xE9B949));
        graphics.fillOval(25, 25, 110, 110);
        graphics.setColor(new Color(0x1E2A44));
        graphics.fillPolygon(new int[] {80, 95, 120, 95, 80, 65, 40, 65}, new int[] {45, 65, 80, 95, 115, 95, 80, 65}, 8);
        graphics.dispose();
        return image;
    }
}
