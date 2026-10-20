package org.example.vector;

import java.awt.image.BufferedImage;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.IntPredicate;

public final class BackgroundRegionProcessor {
    /** CIE76 ΔE allowed between the border background color and the pixels it removes. */
    private static final int BORDER_TOLERANCE = 12;
    private static final int BORDER_BUCKET_MASK = 0x00f0f0f0;
    private static final int MIN_MATTE_CONTRAST_SQUARED = 30 * 30;
    private static final int MAX_MATTE_RESIDUAL_SQUARED = 40 * 40;
    // How quickly a gradient-following region's reference color moves toward each new pixel:
    // slow enough to average out noise, fast enough to keep up with a gradient.
    private static final double GRADIENT_SMOOTHING = 0.25;

    private BackgroundRegionProcessor() {
    }

    public static BufferedImage removeSelectedRegions(
            BufferedImage source, List<PixelCoordinate> seeds) {
        return removeSelection(source, new BackgroundSelection(seeds, List.of()));
    }

    public static BufferedImage removeSelection(BufferedImage source, BackgroundSelection selection) {
        return removeSelection(source, selection, null);
    }

    /**
     * Applies the selection. When it keeps only the subject and {@code subjectMask} is given, every
     * visible pixel the mask marks as background is removed like a selected region.
     */
    public static BufferedImage removeSelection(
            BufferedImage source, BackgroundSelection selection, SubjectMask subjectMask) {
        Objects.requireNonNull(source, "Source image is required");
        Objects.requireNonNull(selection, "Background selection is required");
        if (subjectMask != null
                && (subjectMask.width() != source.getWidth() || subjectMask.height() != source.getHeight())) {
            throw new IllegalArgumentException("Subject mask must match the source image size");
        }

        int width = source.getWidth();
        int height = source.getHeight();
        boolean[] removed = new boolean[width * height];
        for (RegionSeed region : selection.regions()) {
            PixelCoordinate seed = region.point();
            validateSeed(seed, width, height);
            int seedPixel = source.getRGB(seed.x(), seed.y());
            if ((seedPixel >>> 24) == 0) {
                continue;
            }
            if (!region.contiguous()) {
                collectMatchingPixels(source, seedPixel, removed, selection.tolerance());
            } else if (region.followGradient()) {
                collectGradientRegion(source, seed.x(), seed.y(), removed, selection.tolerance());
            } else {
                collectConnectedRegion(source, seed.x(), seed.y(), seedPixel, removed, false,
                        selection.tolerance());
            }
        }
        if (selection.keepSubjectOnly() && subjectMask != null) {
            for (int index = 0; index < removed.length; index++) {
                if (!subjectMask.isSubject(index) && (source.getRGB(index % width, index / width) >>> 24) != 0) {
                    removed[index] = true;
                }
            }
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
        double[] backgroundLab = LabColor.lab(backgroundRgb);
        long backgroundCount = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (matchesBackground(source.getRGB(x, y), backgroundLab, true, BORDER_TOLERANCE)) {
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
            collectIfBackground(source, x, 0, backgroundLab, collectFromBorder);
            collectIfBackground(source, x, height - 1, backgroundLab, collectFromBorder);
        }
        for (int y = 1; y < height - 1; y++) {
            collectIfBackground(source, 0, y, backgroundLab, collectFromBorder);
            collectIfBackground(source, width - 1, y, backgroundLab, collectFromBorder);
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
        double[] seedLab = LabColor.lab(seedRgb);
        if (removed[seedIndex]
                || !matchesBackground(source.getRGB(seedX, seedY), seedLab, opaqueOnly, tolerance)) {
            return;
        }
        removed[seedIndex] = true;
        queue.add(seedIndex);

        while (!queue.isEmpty()) {
            int index = queue.removeFirst();
            int x = index % width;
            int y = index / width;
            if (x > 0) {
                enqueueIfMatching(source, x - 1, y, seedLab, opaqueOnly, tolerance, removed, queue);
            }
            if (x + 1 < width) {
                enqueueIfMatching(source, x + 1, y, seedLab, opaqueOnly, tolerance, removed, queue);
            }
            if (y > 0) {
                enqueueIfMatching(source, x, y - 1, seedLab, opaqueOnly, tolerance, removed, queue);
            }
            if (y + 1 < height) {
                enqueueIfMatching(source, x, y + 1, seedLab, opaqueOnly, tolerance, removed, queue);
            }
        }
    }

    /** Removes every visible pixel close to the seed color, connected to it or not. */
    private static void collectMatchingPixels(BufferedImage source, int seedRgb, boolean[] removed, int tolerance) {
        int width = source.getWidth();
        int[] pixels = source.getRGB(0, 0, width, source.getHeight(), null, 0, width);
        double[] seedLab = LabColor.lab(seedRgb);
        for (int index = 0; index < pixels.length; index++) {
            if (!removed[index] && matchesBackground(pixels[index], seedLab, false, tolerance)) {
                removed[index] = true;
            }
        }
    }

    /**
     * Grows a region by comparing each neighbor with a smoothed reference color carried along from
     * the pixel that reached it. The reference drifts with a slow gradient but averages out noise,
     * and a sudden jump such as the artwork's edge still stops the region.
     */
    private static void collectGradientRegion(
            BufferedImage source, int seedX, int seedY, boolean[] removed, int tolerance) {
        int width = source.getWidth();
        int height = source.getHeight();
        int seedIndex = seedY * width + seedX;
        if (removed[seedIndex]) {
            return;
        }
        int[] pixels = source.getRGB(0, 0, width, height, null, 0, width);
        double[][] reference = new double[pixels.length][];
        reference[seedIndex] = LabColor.lab(pixels[seedIndex]);
        removed[seedIndex] = true;
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        queue.add(seedIndex);
        while (!queue.isEmpty()) {
            int index = queue.removeFirst();
            int x = index % width;
            int y = index / width;
            int[] neighbors = {
                    x > 0 ? index - 1 : -1, x + 1 < width ? index + 1 : -1,
                    y > 0 ? index - width : -1, y + 1 < height ? index + width : -1};
            for (int neighbor : neighbors) {
                if (neighbor < 0 || removed[neighbor] || (pixels[neighbor] >>> 24) == 0) {
                    continue;
                }
                double[] color = LabColor.lab(pixels[neighbor]);
                double[] from = reference[index];
                if (LabColor.deltaE(color, from) <= tolerance) {
                    removed[neighbor] = true;
                    reference[neighbor] = new double[] {
                            from[0] + (color[0] - from[0]) * GRADIENT_SMOOTHING,
                            from[1] + (color[1] - from[1]) * GRADIENT_SMOOTHING,
                            from[2] + (color[2] - from[2]) * GRADIENT_SMOOTHING};
                    queue.addLast(neighbor);
                }
            }
            reference[index] = null;
        }
    }

    private static void enqueueIfMatching(
            BufferedImage source, int x, int y, double[] seedLab, boolean opaqueOnly, int tolerance,
            boolean[] removed, ArrayDeque<Integer> queue) {
        int index = y * source.getWidth() + x;
        if (!removed[index] && matchesBackground(source.getRGB(x, y), seedLab, opaqueOnly, tolerance)) {
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
            BufferedImage source, int x, int y, double[] backgroundLab, Consumer<PixelCoordinate> consumer) {
        if (matchesBackground(source.getRGB(x, y), backgroundLab, true, BORDER_TOLERANCE)) {
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

    /** Whether the pixel is visible and within {@code tolerance} CIE76 ΔE of the seed color. */
    private static boolean matchesBackground(int pixel, double[] seedLab, boolean opaqueOnly, int tolerance) {
        int alpha = pixel >>> 24;
        if ((opaqueOnly && alpha != 255) || (!opaqueOnly && alpha == 0)) {
            return false;
        }
        return LabColor.deltaE(LabColor.lab(pixel), seedLab) <= tolerance;
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
            double[] seedLab = LabColor.lab(seedRgb);
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
                            && matchesBackground(pixels[neighbor], seedLab, false, tolerance)) {
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
