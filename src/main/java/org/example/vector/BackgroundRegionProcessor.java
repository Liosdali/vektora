package org.example.vector;

import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

public final class BackgroundRegionProcessor {
    private static final int COLOR_TOLERANCE = 24;

    private BackgroundRegionProcessor() {
    }

    public static BufferedImage removeSelectedRegions(
            BufferedImage source, List<PixelCoordinate> seeds) {
        Objects.requireNonNull(source, "Source image is required");
        Objects.requireNonNull(seeds, "Background seeds are required");

        int width = source.getWidth();
        int height = source.getHeight();
        boolean[] removed = new boolean[width * height];
        for (PixelCoordinate seed : seeds) {
            Objects.requireNonNull(seed, "Background seed is required");
            validateSeed(seed, width, height);
            int seedPixel = source.getRGB(seed.x(), seed.y());
            if ((seedPixel >>> 24) == 0) {
                continue;
            }
            collectConnectedRegion(source, seed.x(), seed.y(), seedPixel, removed);
        }

        return transparentRemovedPixels(source, removed);
    }

    public static BufferedImage removeBorderBackground(BufferedImage source) {
        Objects.requireNonNull(source, "Source image is required");
        int width = source.getWidth();
        int height = source.getHeight();
        if (width < 3 || height < 3) {
            return source;
        }

        Map<Integer, Integer> borderColors = new LinkedHashMap<>();
        long transparentBorderPixels = 0;
        for (int x = 0; x < width; x++) {
            transparentBorderPixels += countBorderColor(source.getRGB(x, 0), borderColors);
            transparentBorderPixels += countBorderColor(source.getRGB(x, height - 1), borderColors);
        }
        for (int y = 1; y < height - 1; y++) {
            transparentBorderPixels += countBorderColor(source.getRGB(0, y), borderColors);
            transparentBorderPixels += countBorderColor(source.getRGB(width - 1, y), borderColors);
        }
        long borderPixelCount = (long) width * 2 + (long) height * 2 - 4;
        if (transparentBorderPixels >= borderPixelCount / 2 || borderColors.isEmpty()) {
            return source;
        }

        Map.Entry<Integer, Integer> dominantBorderColor = borderColors.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .orElseThrow();
        if (dominantBorderColor.getValue() < Math.max(3, borderPixelCount / 4)) {
            return source;
        }

        int backgroundRgb = dominantBorderColor.getKey();
        long backgroundCount = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (matchesBackground(source.getRGB(x, y), backgroundRgb, true)) {
                    backgroundCount++;
                }
            }
        }
        if (backgroundCount == 0 || backgroundCount == (long) width * height) {
            return source;
        }

        boolean[] removed = new boolean[width * height];
        Consumer<PixelCoordinate> collectFromBorder = seed -> collectConnectedRegion(
                source, seed.x(), seed.y(), backgroundRgb, removed, true);
        for (int x = 0; x < width; x++) {
            collectIfBackground(source, x, 0, backgroundRgb, collectFromBorder);
            collectIfBackground(source, x, height - 1, backgroundRgb, collectFromBorder);
        }
        for (int y = 1; y < height - 1; y++) {
            collectIfBackground(source, 0, y, backgroundRgb, collectFromBorder);
            collectIfBackground(source, width - 1, y, backgroundRgb, collectFromBorder);
        }

        return transparentRemovedPixels(source, removed);
    }

    private static void collectConnectedRegion(
            BufferedImage source, int seedX, int seedY, int seedRgb, boolean[] removed) {
        collectConnectedRegion(source, seedX, seedY, seedRgb, removed, false);
    }

    private static void collectConnectedRegion(
            BufferedImage source, int seedX, int seedY, int seedRgb,
            boolean[] removed, boolean opaqueOnly) {
        int width = source.getWidth();
        int height = source.getHeight();
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        int seedIndex = seedY * width + seedX;
        if (removed[seedIndex] || !matchesBackground(source.getRGB(seedX, seedY), seedRgb, opaqueOnly)) {
            return;
        }
        removed[seedIndex] = true;
        queue.add(seedIndex);

        while (!queue.isEmpty()) {
            int index = queue.removeFirst();
            int x = index % width;
            int y = index / width;
            if (x > 0) {
                enqueueIfMatching(source, x - 1, y, seedRgb, opaqueOnly, removed, queue);
            }
            if (x + 1 < width) {
                enqueueIfMatching(source, x + 1, y, seedRgb, opaqueOnly, removed, queue);
            }
            if (y > 0) {
                enqueueIfMatching(source, x, y - 1, seedRgb, opaqueOnly, removed, queue);
            }
            if (y + 1 < height) {
                enqueueIfMatching(source, x, y + 1, seedRgb, opaqueOnly, removed, queue);
            }
        }
    }

    private static void enqueueIfMatching(
            BufferedImage source, int x, int y, int seedRgb, boolean opaqueOnly,
            boolean[] removed, ArrayDeque<Integer> queue) {
        int index = y * source.getWidth() + x;
        if (!removed[index] && matchesBackground(source.getRGB(x, y), seedRgb, opaqueOnly)) {
            removed[index] = true;
            queue.addLast(index);
        }
    }

    private static BufferedImage transparentRemovedPixels(BufferedImage source, boolean[] removed) {
        BufferedImage result = new BufferedImage(
                source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                int pixel = source.getRGB(x, y);
                if (removed[y * source.getWidth() + x]) {
                    pixel &= 0x00ffffff;
                }
                result.setRGB(x, y, pixel);
            }
        }
        return result;
    }

    private static void collectIfBackground(
            BufferedImage source, int x, int y, int backgroundRgb, Consumer<PixelCoordinate> consumer) {
        if (matchesBackground(source.getRGB(x, y), backgroundRgb, true)) {
            consumer.accept(new PixelCoordinate(x, y));
        }
    }

    private static int countBorderColor(int pixel, Map<Integer, Integer> colors) {
        if ((pixel >>> 24) != 255) {
            return 1;
        }
        colors.merge(pixel & 0x00ffffff, 1, Integer::sum);
        return 0;
    }

    private static boolean matchesBackground(int pixel, int seedRgb, boolean opaqueOnly) {
        int alpha = pixel >>> 24;
        if ((opaqueOnly && alpha != 255) || (!opaqueOnly && alpha == 0)) {
            return false;
        }
        return Math.abs(((pixel >>> 16) & 0xff) - ((seedRgb >>> 16) & 0xff)) <= COLOR_TOLERANCE
                && Math.abs(((pixel >>> 8) & 0xff) - ((seedRgb >>> 8) & 0xff)) <= COLOR_TOLERANCE
                && Math.abs((pixel & 0xff) - (seedRgb & 0xff)) <= COLOR_TOLERANCE;
    }

    private static void validateSeed(PixelCoordinate seed, int width, int height) {
        if (seed.x() < 0 || seed.y() < 0 || seed.x() >= width || seed.y() >= height) {
            throw new IllegalArgumentException("Background seed must be inside the source image");
        }
    }
}
