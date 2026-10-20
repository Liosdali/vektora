package org.example.vector;

import java.awt.image.BufferedImage;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.IntPredicate;

public final class BackgroundRegionProcessor {
    private static final int BORDER_TOLERANCE = 24;
    private static final int BORDER_BUCKET_MASK = 0x00f0f0f0;
    private static final int MIN_MATTE_CONTRAST_SQUARED = 30 * 30;
    private static final int MAX_MATTE_RESIDUAL_SQUARED = 40 * 40;

    private BackgroundRegionProcessor() {
    }

    public static BufferedImage removeSelectedRegions(
            BufferedImage source, List<PixelCoordinate> seeds) {
        return removeSelection(source, new BackgroundSelection(seeds, List.of()));
    }

    public static BufferedImage removeSelection(BufferedImage source, BackgroundSelection selection) {
        Objects.requireNonNull(source, "Source image is required");
        Objects.requireNonNull(selection, "Background selection is required");

        int width = source.getWidth();
        int height = source.getHeight();
        boolean[] removed = new boolean[width * height];
        for (PixelCoordinate seed : selection.seeds()) {
            Objects.requireNonNull(seed, "Background seed is required");
            validateSeed(seed, width, height);
            int seedPixel = source.getRGB(seed.x(), seed.y());
            if ((seedPixel >>> 24) == 0) {
                continue;
            }
            collectConnectedRegion(source, seed.x(), seed.y(), seedPixel, removed, false,
                    selection.tolerance());
        }
        boolean[] regionRemoved = removed.clone();
        for (EraseStroke stroke : selection.strokes()) {
            paintStroke(stroke, width, height, removed);
        }
        boolean[] protectedPixels = new boolean[width * height];
        for (EraseStroke stroke : selection.protectStrokes()) {
            paintStroke(stroke, width, height, protectedPixels);
        }
        for (int index = 0; index < removed.length; index++) {
            if (protectedPixels[index]) {
                removed[index] = false;
                regionRemoved[index] = false;
            }
        }

        return transparentRemovedPixels(source, removed,
                selection.defringe() ? regionRemoved : null, protectedPixels);
    }

    private static void paintStroke(EraseStroke stroke, int width, int height, boolean[] removed) {
        List<PixelCoordinate> points = stroke.points();
        PixelCoordinate previous = points.getFirst();
        stamp(previous.x(), previous.y(), stroke.radius(), width, height, removed);
        for (int index = 1; index < points.size(); index++) {
            PixelCoordinate next = points.get(index);
            int steps = Math.max(Math.abs(next.x() - previous.x()), Math.abs(next.y() - previous.y()));
            for (int step = 1; step <= steps; step++) {
                int x = previous.x() + Math.round((float) (next.x() - previous.x()) * step / steps);
                int y = previous.y() + Math.round((float) (next.y() - previous.y()) * step / steps);
                stamp(x, y, stroke.radius(), width, height, removed);
            }
            previous = next;
        }
    }

    private static void stamp(
            int centerX, int centerY, int radius, int width, int height, boolean[] removed) {
        for (int y = Math.max(0, centerY - radius); y <= Math.min(height - 1, centerY + radius); y++) {
            for (int x = Math.max(0, centerX - radius); x <= Math.min(width - 1, centerX + radius); x++) {
                int dx = x - centerX;
                int dy = y - centerY;
                if (dx * dx + dy * dy <= radius * radius) {
                    removed[y * width + x] = true;
                }
            }
        }
    }

    public static BufferedImage removeBorderBackground(BufferedImage source) {
        Objects.requireNonNull(source, "Source image is required");
        int width = source.getWidth();
        int height = source.getHeight();
        if (width < 3 || height < 3) {
            return source;
        }

        // JPG noise splits one background into many near-identical colors, so count coarse buckets
        // and use the dominant bucket's average color as the background.
        Map<Integer, long[]> borderBuckets = new LinkedHashMap<>();
        long transparentBorderPixels = 0;
        for (int x = 0; x < width; x++) {
            transparentBorderPixels += countBorderColor(source.getRGB(x, 0), borderBuckets);
            transparentBorderPixels += countBorderColor(source.getRGB(x, height - 1), borderBuckets);
        }
        for (int y = 1; y < height - 1; y++) {
            transparentBorderPixels += countBorderColor(source.getRGB(0, y), borderBuckets);
            transparentBorderPixels += countBorderColor(source.getRGB(width - 1, y), borderBuckets);
        }
        long borderPixelCount = (long) width * 2 + (long) height * 2 - 4;
        if (transparentBorderPixels >= borderPixelCount / 2 || borderBuckets.isEmpty()) {
            return source;
        }

        long[] dominantBucket = borderBuckets.values().stream()
                .max(Comparator.comparingLong(bucket -> bucket[0]))
                .orElseThrow();
        if (dominantBucket[0] < Math.max(3, borderPixelCount / 4)) {
            return source;
        }

        int backgroundRgb = (int) (dominantBucket[1] / dominantBucket[0]) << 16
                | (int) (dominantBucket[2] / dominantBucket[0]) << 8
                | (int) (dominantBucket[3] / dominantBucket[0]);
        long backgroundCount = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (matchesBackground(source.getRGB(x, y), backgroundRgb, true, BORDER_TOLERANCE)) {
                    backgroundCount++;
                }
            }
        }
        if (backgroundCount == 0 || backgroundCount == (long) width * height) {
            return source;
        }

        boolean[] removed = new boolean[width * height];
        Consumer<PixelCoordinate> collectFromBorder = seed -> collectConnectedRegion(
                source, seed.x(), seed.y(), backgroundRgb, removed, true, BORDER_TOLERANCE);
        for (int x = 0; x < width; x++) {
            collectIfBackground(source, x, 0, backgroundRgb, collectFromBorder);
            collectIfBackground(source, x, height - 1, backgroundRgb, collectFromBorder);
        }
        for (int y = 1; y < height - 1; y++) {
            collectIfBackground(source, 0, y, backgroundRgb, collectFromBorder);
            collectIfBackground(source, width - 1, y, backgroundRgb, collectFromBorder);
        }

        return transparentRemovedPixels(source, removed, removed.clone(), new boolean[removed.length]);
    }

    private static void collectConnectedRegion(
            BufferedImage source, int seedX, int seedY, int seedRgb,
            boolean[] removed, boolean opaqueOnly, int tolerance) {
        int width = source.getWidth();
        int height = source.getHeight();
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        int seedIndex = seedY * width + seedX;
        if (removed[seedIndex]
                || !matchesBackground(source.getRGB(seedX, seedY), seedRgb, opaqueOnly, tolerance)) {
            return;
        }
        removed[seedIndex] = true;
        queue.add(seedIndex);

        while (!queue.isEmpty()) {
            int index = queue.removeFirst();
            int x = index % width;
            int y = index / width;
            if (x > 0) {
                enqueueIfMatching(source, x - 1, y, seedRgb, opaqueOnly, tolerance, removed, queue);
            }
            if (x + 1 < width) {
                enqueueIfMatching(source, x + 1, y, seedRgb, opaqueOnly, tolerance, removed, queue);
            }
            if (y > 0) {
                enqueueIfMatching(source, x, y - 1, seedRgb, opaqueOnly, tolerance, removed, queue);
            }
            if (y + 1 < height) {
                enqueueIfMatching(source, x, y + 1, seedRgb, opaqueOnly, tolerance, removed, queue);
            }
        }
    }

    private static void enqueueIfMatching(
            BufferedImage source, int x, int y, int seedRgb, boolean opaqueOnly, int tolerance,
            boolean[] removed, ArrayDeque<Integer> queue) {
        int index = y * source.getWidth() + x;
        if (!removed[index] && matchesBackground(source.getRGB(x, y), seedRgb, opaqueOnly, tolerance)) {
            removed[index] = true;
            queue.addLast(index);
        }
    }

    /**
     * Clears removed pixels. When {@code matteSource} is given, opaque pixels touching that removed
     * region are treated as anti-aliased blends of the region's color and the neighboring artwork:
     * mostly-background blends are removed and mostly-artwork blends take the artwork color, so the
     * tracer does not outline the artwork with a halo of mixed colors.
     */
    private static BufferedImage transparentRemovedPixels(
            BufferedImage source, boolean[] removed, boolean[] matteSource, boolean[] locked) {
        int width = source.getWidth();
        int height = source.getHeight();
        int[] pixels = source.getRGB(0, 0, width, height, null, 0, width);
        int[] result = pixels.clone();
        for (int index = 0; index < result.length; index++) {
            if (removed[index]) {
                result[index] &= 0x00ffffff;
            }
        }
        if (matteSource != null) {
            defringe(pixels, result, width, height, removed, matteSource, locked);
        }

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, width, height, result, 0, width);
        return image;
    }

    private static void defringe(
            int[] pixels, int[] result, int width, int height,
            boolean[] removed, boolean[] matteSource, boolean[] locked) {
        boolean[] edge = new boolean[pixels.length];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int index = y * width + x;
                if (removed[index] || locked[index] || (pixels[index] >>> 24) == 0) {
                    continue;
                }
                edge[index] = hasNeighbor(x, y, width, height,
                        neighbor -> matteSource[neighbor] && (pixels[neighbor] >>> 24) != 0);
            }
        }

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int index = y * width + x;
                if (edge[index]) {
                    result[index] = matte(pixels, x, y, width, height, removed, matteSource, edge);
                }
            }
        }
    }

    private static int matte(
            int[] pixels, int x, int y, int width, int height,
            boolean[] removed, boolean[] matteSource, boolean[] edge) {
        int pixel = pixels[y * width + x];
        long[] background = new long[4];
        int foreground = 0;
        int foregroundDistance = Integer.MAX_VALUE;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                int nx = x + dx;
                int ny = y + dy;
                if ((dx == 0 && dy == 0) || nx < 0 || ny < 0 || nx >= width || ny >= height) {
                    continue;
                }
                int neighbor = ny * width + nx;
                int neighborPixel = pixels[neighbor];
                if ((neighborPixel >>> 24) == 0) {
                    continue;
                }
                if (matteSource[neighbor]) {
                    background[0]++;
                    background[1] += (neighborPixel >>> 16) & 0xff;
                    background[2] += (neighborPixel >>> 8) & 0xff;
                    background[3] += neighborPixel & 0xff;
                } else if (!removed[neighbor] && !edge[neighbor]) {
                    int distance = squaredDistance(pixel, neighborPixel);
                    if (distance < foregroundDistance) {
                        foregroundDistance = distance;
                        foreground = neighborPixel;
                    }
                }
            }
        }
        if (background[0] == 0 || foregroundDistance == Integer.MAX_VALUE) {
            return pixel;
        }

        double backgroundRed = (double) background[1] / background[0];
        double backgroundGreen = (double) background[2] / background[0];
        double backgroundBlue = (double) background[3] / background[0];
        double towardRed = ((foreground >>> 16) & 0xff) - backgroundRed;
        double towardGreen = ((foreground >>> 8) & 0xff) - backgroundGreen;
        double towardBlue = (foreground & 0xff) - backgroundBlue;
        double contrast = towardRed * towardRed + towardGreen * towardGreen + towardBlue * towardBlue;
        if (contrast < MIN_MATTE_CONTRAST_SQUARED) {
            return pixel;
        }
        double red = ((pixel >>> 16) & 0xff) - backgroundRed;
        double green = ((pixel >>> 8) & 0xff) - backgroundGreen;
        double blue = (pixel & 0xff) - backgroundBlue;
        double coverage = (red * towardRed + green * towardGreen + blue * towardBlue) / contrast;
        double residualRed = red - coverage * towardRed;
        double residualGreen = green - coverage * towardGreen;
        double residualBlue = blue - coverage * towardBlue;
        if (residualRed * residualRed + residualGreen * residualGreen + residualBlue * residualBlue
                > MAX_MATTE_RESIDUAL_SQUARED) {
            // Not a blend of these two colors, e.g. a thin outline with a color of its own.
            return pixel;
        }
        return coverage < 0.5
                ? pixel & 0x00ffffff
                : (pixel & 0xff000000) | (foreground & 0x00ffffff);
    }

    private static boolean hasNeighbor(int x, int y, int width, int height, IntPredicate predicate) {
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                int nx = x + dx;
                int ny = y + dy;
                if ((dx != 0 || dy != 0) && nx >= 0 && ny >= 0 && nx < width && ny < height
                        && predicate.test(ny * width + nx)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int squaredDistance(int first, int second) {
        int red = ((first >>> 16) & 0xff) - ((second >>> 16) & 0xff);
        int green = ((first >>> 8) & 0xff) - ((second >>> 8) & 0xff);
        int blue = (first & 0xff) - (second & 0xff);
        return red * red + green * green + blue * blue;
    }

    private static void collectIfBackground(
            BufferedImage source, int x, int y, int backgroundRgb, Consumer<PixelCoordinate> consumer) {
        if (matchesBackground(source.getRGB(x, y), backgroundRgb, true, BORDER_TOLERANCE)) {
            consumer.accept(new PixelCoordinate(x, y));
        }
    }

    private static int countBorderColor(int pixel, Map<Integer, long[]> buckets) {
        if ((pixel >>> 24) != 255) {
            return 1;
        }
        long[] bucket = buckets.computeIfAbsent(pixel & BORDER_BUCKET_MASK, ignored -> new long[4]);
        bucket[0]++;
        bucket[1] += (pixel >>> 16) & 0xff;
        bucket[2] += (pixel >>> 8) & 0xff;
        bucket[3] += pixel & 0xff;
        return 0;
    }

    private static boolean matchesBackground(int pixel, int seedRgb, boolean opaqueOnly, int tolerance) {
        int alpha = pixel >>> 24;
        if ((opaqueOnly && alpha != 255) || (!opaqueOnly && alpha == 0)) {
            return false;
        }
        return Math.abs(((pixel >>> 16) & 0xff) - ((seedRgb >>> 16) & 0xff)) <= tolerance
                && Math.abs(((pixel >>> 8) & 0xff) - ((seedRgb >>> 8) & 0xff)) <= tolerance
                && Math.abs((pixel & 0xff) - (seedRgb & 0xff)) <= tolerance;
    }

    /**
     * Splits the visible pixels into the connected regions a background click would remove and
     * returns the larger ones, edge-touching regions first, then by size.
     */
    public static List<RegionSuggestion> suggestRegions(
            BufferedImage image, int tolerance, int limit, double minimumShare) {
        Objects.requireNonNull(image, "Image is required");
        int width = image.getWidth();
        int height = image.getHeight();
        int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
        long visible = Arrays.stream(pixels).filter(pixel -> (pixel >>> 24) != 0).count();
        if (visible == 0 || limit < 1) {
            return List.of();
        }

        boolean[] claimed = new boolean[pixels.length];
        int[] stack = new int[pixels.length];
        List<RegionSuggestion> regions = new ArrayList<>();
        for (int start = 0; start < pixels.length; start++) {
            if (claimed[start] || (pixels[start] >>> 24) == 0) {
                continue;
            }
            int seedRgb = pixels[start];
            int size = 0;
            int count = 0;
            boolean touchesBorder = false;
            claimed[start] = true;
            stack[size++] = start;
            while (size > 0) {
                int index = stack[--size];
                count++;
                int x = index % width;
                int y = index / width;
                touchesBorder |= x == 0 || y == 0 || x == width - 1 || y == height - 1;
                int[] neighbors = {
                        x > 0 ? index - 1 : -1, x + 1 < width ? index + 1 : -1,
                        y > 0 ? index - width : -1, y + 1 < height ? index + width : -1};
                for (int neighbor : neighbors) {
                    if (neighbor >= 0 && !claimed[neighbor]
                            && matchesBackground(pixels[neighbor], seedRgb, false, tolerance)) {
                        claimed[neighbor] = true;
                        stack[size++] = neighbor;
                    }
                }
            }
            double share = (double) count / visible;
            if (share >= minimumShare) {
                regions.add(new RegionSuggestion(new PixelCoordinate(start % width, start / width),
                        seedRgb & 0x00ffffff, count, share, touchesBorder));
            }
        }
        regions.sort(Comparator.comparing(RegionSuggestion::touchesBorder).reversed()
                .thenComparing(Comparator.comparingInt(RegionSuggestion::pixelCount).reversed()));
        return List.copyOf(regions.subList(0, Math.min(limit, regions.size())));
    }

    private static void validateSeed(PixelCoordinate seed, int width, int height) {
        if (seed.x() < 0 || seed.y() < 0 || seed.x() >= width || seed.y() >= height) {
            throw new IllegalArgumentException("Background seed must be inside the source image");
        }
    }
}
