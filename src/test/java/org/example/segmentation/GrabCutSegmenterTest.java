package org.example.segmentation;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.GeneralPath;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Random;
import org.example.vector.EraseStroke;
import org.example.vector.PixelCoordinate;
import org.example.vector.SubjectMask;
import org.junit.jupiter.api.Test;

class GrabCutSegmenterTest {
    @Test
    void separatesADiskFromANoisyGradientWithoutAModel() throws SegmentationException {
        BufferedImage image = noisyGradient(200, 200);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(new Color(0xE9B949));
        graphics.fillOval(50, 50, 100, 100);
        graphics.dispose();

        SubjectMask mask = new GrabCutSegmenter().segment(image);

        assertSeparates(mask, (x, y) -> (x - 99.5) * (x - 99.5) + (y - 99.5) * (y - 99.5) <= 50 * 50, 2);
    }

    @Test
    void separatesTheNoisyBadgePhotoWithinTheAcceptanceLimits() throws SegmentationException {
        SubjectMask mask = new GrabCutSegmenter().segment(badgePhoto());

        // The badge is the outer ring's disk: center 160, radius 140 plus half the 14 px stroke.
        assertSeparates(mask, (x, y) -> (x - 159.5) * (x - 159.5) + (y - 159.5) * (y - 159.5) <= 147 * 147, 2);
    }

    @Test
    void returnsTheSameMaskForTheSameImage() throws SegmentationException {
        BufferedImage badge = badgePhoto();

        assertArrayEquals(bytes(new GrabCutSegmenter().segment(badge)), bytes(new GrabCutSegmenter().segment(badge)));
    }

    @Test
    void brushStrokesAreHardConstraints() throws SegmentationException {
        BufferedImage image = noisyGradient(200, 200);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(0xE9B949));
        graphics.fillOval(50, 50, 100, 100);
        graphics.dispose();
        EraseStroke keepCorner = new EraseStroke(List.of(new PixelCoordinate(30, 30)), 4);
        EraseStroke removeCenter = new EraseStroke(List.of(new PixelCoordinate(100, 100)), 4);

        SubjectMask mask = new GrabCutSegmenter().segment(image,
                new SegmentationHints(null, List.of(keepCorner), List.of(removeCenter)));

        assertTrue(mask.isSubject(30 * 200 + 30));
        assertFalse(mask.isSubject(100 * 200 + 100));
        assertTrue(mask.isSubject(70 * 200 + 100));
    }

    @Test
    void aSmallerFrameKeepsOnlyWhatItEncloses() throws SegmentationException {
        BufferedImage image = noisyGradient(240, 120);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(0xE9B949));
        graphics.fillRect(30, 30, 60, 60);
        graphics.setColor(new Color(0x1E2A44));
        graphics.fillRect(150, 30, 60, 60);
        graphics.dispose();

        SubjectMask mask = new GrabCutSegmenter().segment(image,
                new SegmentationHints(new Rectangle(130, 10, 100, 100), List.of(), List.of()));

        assertTrue(mask.isSubject(60 * 240 + 180));
        assertFalse(mask.isSubject(60 * 240 + 60));
    }

    @Test
    void keepsFullyTransparentPixelsOutOfTheSubject() throws SegmentationException {
        BufferedImage image = noisyGradient(100, 100);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(0xE9B949));
        graphics.fillOval(25, 25, 50, 50);
        graphics.dispose();
        image.setRGB(50, 50, 0);

        assertFalse(new GrabCutSegmenter().segment(image).isSubject(50 * 100 + 50));
    }

    private interface Region {
        boolean contains(int x, int y);
    }

    /** At least 95% of the background removed and under 1% of the subject lost, outside an edge band. */
    private static void assertSeparates(SubjectMask mask, Region subject, int edgeBand) {
        long background = 0;
        long backgroundKept = 0;
        long foreground = 0;
        long foregroundLost = 0;
        for (int y = 0; y < mask.height(); y++) {
            for (int x = 0; x < mask.width(); x++) {
                if (nearBoundary(subject, x, y, edgeBand)) {
                    continue;
                }
                boolean kept = mask.isSubject(y * mask.width() + x);
                if (subject.contains(x, y)) {
                    foreground++;
                    foregroundLost += kept ? 0 : 1;
                } else {
                    background++;
                    backgroundKept += kept ? 1 : 0;
                }
            }
        }
        assertTrue(backgroundKept <= background * 0.05, "background kept: " + backgroundKept + " / " + background);
        assertTrue(foregroundLost < foreground * 0.01, "subject lost: " + foregroundLost + " / " + foreground);
    }

    private static boolean nearBoundary(Region region, int x, int y, int band) {
        boolean inside = region.contains(x, y);
        for (int dy = -band; dy <= band; dy++) {
            for (int dx = -band; dx <= band; dx++) {
                if (region.contains(x + dx, y + dy) != inside) {
                    return true;
                }
            }
        }
        return false;
    }

    private static byte[] bytes(SubjectMask mask) {
        byte[] values = new byte[mask.width() * mask.height()];
        for (int index = 0; index < values.length; index++) {
            values[index] = (byte) mask.foreground(index % mask.width(), index / mask.width());
        }
        return values;
    }

    private static BufferedImage noisyGradient(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Random random = new Random(7);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int base = 90 + (x + y) * 120 / (width + height);
                int noise = random.nextInt(31) - 15;
                image.setRGB(x, y, new Color(clamp(base + noise), clamp(base + 40 + noise), clamp(150 + noise)).getRGB());
            }
        }
        return image;
    }

    /** The badge from the roadmap's acceptance criteria: a navy, cream and gold badge on a noisy gradient. */
    static BufferedImage badgePhoto() {
        BufferedImage image = noisyGradient(320, 320);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(new Color(0xF3EBD8));
        graphics.fillOval(20, 20, 280, 280);
        graphics.setColor(new Color(0x1E2A44));
        graphics.setStroke(new BasicStroke(14));
        graphics.drawOval(20, 20, 280, 280);
        graphics.setColor(new Color(0xE9B949));
        graphics.fillOval(50, 50, 220, 220);
        graphics.setColor(new Color(0x1E2A44));
        graphics.setStroke(new BasicStroke(4));
        graphics.drawOval(50, 50, 220, 220);
        GeneralPath bird = new GeneralPath();
        double[][] points = {{160, 93}, {170, 131}, {240, 102}, {195, 157}, {230, 166}, {173, 179}, {182, 224},
                {160, 208}, {138, 224}, {147, 179}, {90, 166}, {125, 157}, {80, 102}, {150, 131}};
        bird.moveTo(points[0][0], points[0][1]);
        for (int index = 1; index < points.length; index++) {
            bird.lineTo(points[index][0], points[index][1]);
        }
        bird.closePath();
        graphics.fill(bird);
        graphics.dispose();
        return image;
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }
}
