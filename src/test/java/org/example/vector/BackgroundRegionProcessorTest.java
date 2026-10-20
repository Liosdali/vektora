package org.example.vector;

import org.junit.jupiter.api.Test;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BackgroundRegionProcessorTest {
    @Test
    void erasesDrawnStrokeAcrossDifferentColorsAndKeepsEverythingElse() {
        BufferedImage source = image(9, 5);
        fill(source, 0, 0, 9, 5, Color.RED);
        fill(source, 4, 0, 5, 5, Color.BLUE);

        BufferedImage result = BackgroundRegionProcessor.removeSelection(source,
                new BackgroundSelection(List.of(), List.of(new EraseStroke(
                        List.of(new PixelCoordinate(2, 2), new PixelCoordinate(6, 2)), 1))));

        assertEquals(0, alpha(result, 2, 2));
        assertEquals(0, alpha(result, 4, 2));
        assertEquals(0, alpha(result, 6, 2));
        assertEquals(0, alpha(result, 4, 1));
        assertEquals(255, alpha(result, 0, 0));
        assertEquals(255, alpha(result, 8, 4));
        assertEquals(255, alpha(source, 4, 2));
    }

    @Test
    void removesOnlyTheConnectedRegionMatchingTheSelectedSeed() {
        BufferedImage source = image(7, 3);
        fill(source, 0, 0, 3, 3, new Color(100, 120, 140));
        fill(source, 4, 0, 3, 3, new Color(100, 120, 140));

        BufferedImage result = BackgroundRegionProcessor.removeSelectedRegions(
                source, List.of(new PixelCoordinate(1, 1)));

        assertEquals(0, alpha(result, 1, 1));
        assertEquals(255, alpha(result, 4, 1));
    }

    @Test
    void removesMultipleSelectedRegionsInOnePass() {
        BufferedImage source = image(7, 3);
        fill(source, 0, 0, 2, 3, Color.RED);
        fill(source, 2, 0, 3, 3, Color.GREEN);
        fill(source, 5, 0, 2, 3, Color.BLUE);

        BufferedImage result = BackgroundRegionProcessor.removeSelectedRegions(
                source, List.of(new PixelCoordinate(0, 1), new PixelCoordinate(6, 1)));

        assertEquals(0, alpha(result, 0, 1));
        assertEquals(0, alpha(result, 6, 1));
        assertEquals(255, alpha(result, 3, 1));
    }

    @Test
    void comparesNeighborsToTheFixedSeedColorRatherThanExpandingToleranceTransitively() {
        BufferedImage source = image(3, 1);
        source.setRGB(0, 0, new Color(100, 100, 100).getRGB());
        source.setRGB(1, 0, new Color(120, 120, 120).getRGB());
        source.setRGB(2, 0, new Color(140, 140, 140).getRGB());

        BufferedImage result = BackgroundRegionProcessor.removeSelectedRegions(
                source, List.of(new PixelCoordinate(0, 0)));

        assertEquals(0, alpha(result, 0, 0));
        assertEquals(0, alpha(result, 1, 0));
        assertEquals(255, alpha(result, 2, 0));
    }

    @Test
    void transparentSeedsDoNotChangeTheImage() {
        BufferedImage source = image(2, 1);
        source.setRGB(1, 0, Color.RED.getRGB());

        BufferedImage result = BackgroundRegionProcessor.removeSelectedRegions(
                source, List.of(new PixelCoordinate(0, 0)));

        assertEquals(0, alpha(result, 0, 0));
        assertEquals(255, alpha(result, 1, 0));
    }

    @Test
    void rejectsSeedsOutsideTheSourceImage() {
        BufferedImage source = image(2, 2);

        assertThrows(IllegalArgumentException.class,
                () -> BackgroundRegionProcessor.removeSelectedRegions(
                        source, List.of(new PixelCoordinate(2, 1))));
    }

    @Test
    void leavesTheSourceImageUnchanged() {
        BufferedImage source = image(2, 1);
        source.setRGB(0, 0, Color.RED.getRGB());

        BackgroundRegionProcessor.removeSelectedRegions(
                source, List.of(new PixelCoordinate(0, 0)));

        assertEquals(255, alpha(source, 0, 0));
    }

    @Test
    void higherToleranceRemovesColorsFurtherFromTheSeed() {
        BufferedImage source = image(3, 1);
        source.setRGB(0, 0, new Color(100, 100, 100).getRGB());
        source.setRGB(1, 0, new Color(120, 120, 120).getRGB());
        source.setRGB(2, 0, new Color(140, 140, 140).getRGB());

        BufferedImage result = BackgroundRegionProcessor.removeSelection(source,
                BackgroundSelection.EMPTY.withSeed(new PixelCoordinate(0, 0)).withTolerance(40));

        assertEquals(0, alpha(result, 2, 0));
    }

    @Test
    void protectStrokesKeepPixelsThatARegionOrEraseStrokeWouldRemove() {
        BufferedImage source = image(6, 3);
        fill(source, 0, 0, 6, 3, Color.RED);

        BufferedImage result = BackgroundRegionProcessor.removeSelection(source, BackgroundSelection.EMPTY
                .withSeed(new PixelCoordinate(0, 0))
                .withStroke(new EraseStroke(List.of(new PixelCoordinate(5, 1)), 1))
                .withProtectStroke(new EraseStroke(List.of(new PixelCoordinate(5, 1)), 1)));

        assertEquals(0, alpha(result, 0, 0));
        assertEquals(255, alpha(result, 5, 1));
        assertEquals(255, alpha(result, 4, 1));
    }

    @Test
    void defringeRemovesMostlyBackgroundEdgeBlends() {
        BufferedImage source = edgeBlendImage(0.25);

        BufferedImage result = BackgroundRegionProcessor.removeSelection(source,
                BackgroundSelection.EMPTY.withSeed(new PixelCoordinate(0, 1)));

        assertEquals(0, alpha(result, 1, 1));
        assertEquals(0, alpha(result, 2, 1));
        assertEquals(NAVY.getRGB(), result.getRGB(3, 1));
    }

    @Test
    void defringeSnapsMostlyArtworkEdgeBlendsToTheArtworkColor() {
        BufferedImage source = edgeBlendImage(0.75);

        BufferedImage result = BackgroundRegionProcessor.removeSelection(source,
                BackgroundSelection.EMPTY.withSeed(new PixelCoordinate(0, 1)));

        assertEquals(NAVY.getRGB(), result.getRGB(2, 1));
    }

    @Test
    void edgeBlendsStayUntouchedWhenDefringeIsOff() {
        BufferedImage source = edgeBlendImage(0.25);

        BufferedImage result = BackgroundRegionProcessor.removeSelection(source, BackgroundSelection.EMPTY
                .withSeed(new PixelCoordinate(0, 1)).withDefringe(false));

        assertEquals(source.getRGB(2, 1), result.getRGB(2, 1));
    }

    @Test
    void defringeKeepsThinOutlinesThatAreNotABlendOfTheirNeighbors() {
        BufferedImage source = image(6, 3);
        fill(source, 0, 0, 2, 3, YELLOW);
        fill(source, 2, 0, 1, 3, Color.RED);
        fill(source, 3, 0, 3, 3, NAVY);

        BufferedImage result = BackgroundRegionProcessor.removeSelection(source,
                BackgroundSelection.EMPTY.withSeed(new PixelCoordinate(0, 1)));

        assertEquals(Color.RED.getRGB(), result.getRGB(2, 1));
    }

    @Test
    void automaticBorderCleanupHandlesJpgStyleNoiseInTheBackground() {
        BufferedImage source = image(12, 12);
        int[][] noisyWhites = {
                {255, 255, 255}, {250, 252, 251}, {253, 249, 255},
                {248, 254, 250}, {252, 250, 247}, {246, 251, 253}};
        for (int y = 0; y < 12; y++) {
            for (int x = 0; x < 12; x++) {
                int[] white = noisyWhites[(x + y * 5) % noisyWhites.length];
                source.setRGB(x, y, new Color(white[0], white[1], white[2]).getRGB());
            }
        }
        fill(source, 4, 4, 4, 4, Color.RED);

        BufferedImage result = BackgroundRegionProcessor.removeBorderBackground(source);

        assertEquals(0, alpha(result, 0, 0));
        assertEquals(0, alpha(result, 2, 9));
        assertEquals(Color.RED.getRGB(), result.getRGB(5, 5));
    }

    private static final Color YELLOW = new Color(233, 185, 73);
    private static final Color NAVY = new Color(30, 42, 68);

    private static BufferedImage edgeBlendImage(double artworkShare) {
        BufferedImage source = image(6, 3);
        fill(source, 0, 0, 2, 3, YELLOW);
        fill(source, 2, 0, 1, 3, blend(YELLOW, NAVY, artworkShare));
        fill(source, 3, 0, 3, 3, NAVY);
        return source;
    }

    private static Color blend(Color background, Color artwork, double artworkShare) {
        return new Color(
                (int) Math.round(background.getRed() + (artwork.getRed() - background.getRed()) * artworkShare),
                (int) Math.round(background.getGreen() + (artwork.getGreen() - background.getGreen()) * artworkShare),
                (int) Math.round(background.getBlue() + (artwork.getBlue() - background.getBlue()) * artworkShare));
    }

    private static BufferedImage image(int width, int height) {
        return new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    }

    private static void fill(BufferedImage image, int x, int y, int width, int height, Color color) {
        for (int row = y; row < y + height; row++) {
            for (int column = x; column < x + width; column++) {
                image.setRGB(column, row, color.getRGB());
            }
        }
    }

    private static int alpha(BufferedImage image, int x, int y) {
        return image.getRGB(x, y) >>> 24;
    }
}
