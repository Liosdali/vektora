package org.example.vector;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Picks a small palette for the visible pixels of an image. Median cut splits the color space
 * into boxes by pixel count, then a few weighted k-means passes pull each color to the middle of
 * the pixels it represents. The result depends only on the pixels, so tracing the same image
 * twice yields the same colors, which keeps per-color layer edits attached to their layer.
 */
public final class ColorReducer {
    private static final int REFINEMENT_PASSES = 6;
    private static final double MIN_BLEND_CONTRAST_SQUARED = 40 * 40;
    private static final double MAX_BLEND_RESIDUAL_SQUARED = 14 * 14;
    private static final double MAX_SHADE_DISTANCE_SQUARED = 30 * 30;
    // Photos can have tens of thousands of colors; checking every pair of them is not worth it.
    private static final int MAX_BLEND_CHECKED_COLORS = 1024;

    private ColorReducer() {
    }

    public static List<Color> reduce(BufferedImage image, int maxColors) {
        if (maxColors < 1) {
            throw new IllegalArgumentException("Maximum color count must be positive");
        }
        Map<Integer, Integer> histogram = new HashMap<>();
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int pixel = image.getRGB(x, y);
                if ((pixel >>> 24) != 0) {
                    histogram.merge(pixel & 0x00ffffff, 1, Integer::sum);
                }
            }
        }
        if (histogram.isEmpty()) {
            return List.of();
        }
        int[] colors = histogram.keySet().stream().mapToInt(Integer::intValue).sorted().toArray();
        int[] counts = new int[colors.length];
        for (int index = 0; index < colors.length; index++) {
            counts[index] = histogram.get(colors[index]);
        }
        if (colors.length <= maxColors) {
            List<Color> exact = new ArrayList<>(colors.length);
            for (int color : colors) {
                exact.add(new Color(color));
            }
            return List.copyOf(exact);
        }

        double[][] centers = medianCut(colors, counts, maxColors);
        for (int pass = 0; pass < REFINEMENT_PASSES; pass++) {
            double[][] sums = new double[centers.length][4];
            for (int index = 0; index < colors.length; index++) {
                double[] sum = sums[nearest(centers, colors[index])];
                sum[0] += ((colors[index] >>> 16) & 0xff) * (double) counts[index];
                sum[1] += ((colors[index] >>> 8) & 0xff) * (double) counts[index];
                sum[2] += (colors[index] & 0xff) * (double) counts[index];
                sum[3] += counts[index];
            }
            for (int center = 0; center < centers.length; center++) {
                if (sums[center][3] > 0) {
                    centers[center] = new double[] {
                            sums[center][0] / sums[center][3], sums[center][1] / sums[center][3],
                            sums[center][2] / sums[center][3]};
                }
            }
        }
        List<Color> palette = new ArrayList<>(centers.length);
        for (double[] center : centers) {
            Color color = new Color((int) Math.round(center[0]), (int) Math.round(center[1]),
                    (int) Math.round(center[2]));
            if (!palette.contains(color)) {
                palette.add(color);
            }
        }
        return List.copyOf(palette);
    }

    /**
     * Drops palette colors that are anti-aliasing blends: colors lying on the line between two
     * colors that cover more pixels, or barely different from one that covers more. Their pixels then join the nearest remaining color, so edges
     * between two flat areas stop tracing as thin slivers of in-between shades. Kept colors stay in
     * their original order.
     */
    public static List<Color> withoutBlends(List<Color> palette, BufferedImage image) {
        if (palette.size() < 3 || palette.size() > MAX_BLEND_CHECKED_COLORS) {
            return palette;
        }
        long[] coverage = new long[palette.size()];
        Map<Integer, Integer> histogram = new HashMap<>();
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int pixel = image.getRGB(x, y);
                if ((pixel >>> 24) != 0) {
                    histogram.merge(pixel & 0x00ffffff, 1, Integer::sum);
                }
            }
        }
        double[][] centers = palette.stream()
                .map(color -> new double[] {color.getRed(), color.getGreen(), color.getBlue()})
                .toArray(double[][]::new);
        histogram.forEach((rgb, count) -> coverage[nearest(centers, rgb)] += count);

        List<Integer> byCoverage = new ArrayList<>();
        for (int index = 0; index < palette.size(); index++) {
            byCoverage.add(index);
        }
        byCoverage.sort(Comparator.comparingLong((Integer index) -> coverage[index]).reversed()
                .thenComparingInt(index -> index));
        List<Integer> kept = new ArrayList<>();
        for (int index : byCoverage) {
            if (coverage[index] > 0 && !isBlendOf(centers[index], kept, centers)) {
                kept.add(index);
            }
        }
        kept.sort(Comparator.naturalOrder());
        return kept.stream().map(palette::get).toList();
    }

    private static boolean isBlendOf(double[] color, List<Integer> candidates, double[][] centers) {
        for (int candidate : candidates) {
            double red = color[0] - centers[candidate][0];
            double green = color[1] - centers[candidate][1];
            double blue = color[2] - centers[candidate][2];
            if (red * red + green * green + blue * blue <= MAX_SHADE_DISTANCE_SQUARED) {
                return true;
            }
        }
        for (int first = 0; first < candidates.size(); first++) {
            for (int second = first + 1; second < candidates.size(); second++) {
                double[] from = centers[candidates.get(first)];
                double[] to = centers[candidates.get(second)];
                double[] toward = {to[0] - from[0], to[1] - from[1], to[2] - from[2]};
                double length = toward[0] * toward[0] + toward[1] * toward[1] + toward[2] * toward[2];
                if (length < MIN_BLEND_CONTRAST_SQUARED) {
                    continue;
                }
                double[] offset = {color[0] - from[0], color[1] - from[1], color[2] - from[2]};
                double position = (offset[0] * toward[0] + offset[1] * toward[1] + offset[2] * toward[2]) / length;
                if (position < 0.1 || position > 0.9) {
                    continue;
                }
                double residual = 0;
                for (int channel = 0; channel < 3; channel++) {
                    double miss = offset[channel] - position * toward[channel];
                    residual += miss * miss;
                }
                if (residual <= MAX_BLEND_RESIDUAL_SQUARED) {
                    return true;
                }
            }
        }
        return false;
    }

    private static double[][] medianCut(int[] colors, int[] counts, int maxColors) {
        List<List<Integer>> boxes = new ArrayList<>();
        List<Integer> all = new ArrayList<>(colors.length);
        for (int index = 0; index < colors.length; index++) {
            all.add(index);
        }
        boxes.add(all);
        while (boxes.size() < maxColors) {
            // Split the box holding the most pixels along its widest channel.
            List<Integer> largest = boxes.stream()
                    .filter(box -> box.size() > 1)
                    .max(Comparator.comparingLong(box -> weight(box, counts)))
                    .orElse(null);
            if (largest == null) {
                break;
            }
            int channel = widestChannel(largest, colors);
            largest.sort(Comparator.comparingInt((Integer index) -> channel(colors[index], channel))
                    .thenComparingInt(index -> colors[index]));
            long half = weight(largest, counts) / 2;
            long running = 0;
            int split = 1;
            for (int position = 0; position < largest.size() - 1; position++) {
                running += counts[largest.get(position)];
                split = position + 1;
                if (running >= half) {
                    break;
                }
            }
            boxes.remove(largest);
            boxes.add(new ArrayList<>(largest.subList(0, split)));
            boxes.add(new ArrayList<>(largest.subList(split, largest.size())));
        }
        double[][] centers = new double[boxes.size()][];
        for (int box = 0; box < boxes.size(); box++) {
            double red = 0;
            double green = 0;
            double blue = 0;
            long total = weight(boxes.get(box), counts);
            for (int index : boxes.get(box)) {
                red += ((colors[index] >>> 16) & 0xff) * (double) counts[index];
                green += ((colors[index] >>> 8) & 0xff) * (double) counts[index];
                blue += (colors[index] & 0xff) * (double) counts[index];
            }
            centers[box] = new double[] {red / total, green / total, blue / total};
        }
        return centers;
    }

    private static long weight(List<Integer> box, int[] counts) {
        long total = 0;
        for (int index : box) {
            total += counts[index];
        }
        return total;
    }

    private static int widestChannel(List<Integer> box, int[] colors) {
        int widest = 0;
        int widestRange = -1;
        for (int channel = 0; channel < 3; channel++) {
            int low = 255;
            int high = 0;
            for (int index : box) {
                int value = channel(colors[index], channel);
                low = Math.min(low, value);
                high = Math.max(high, value);
            }
            if (high - low > widestRange) {
                widestRange = high - low;
                widest = channel;
            }
        }
        return widest;
    }

    private static int channel(int rgb, int channel) {
        return (rgb >>> (16 - channel * 8)) & 0xff;
    }

    private static int nearest(double[][] centers, int rgb) {
        int best = 0;
        double bestDistance = Double.MAX_VALUE;
        for (int center = 0; center < centers.length; center++) {
            double red = ((rgb >>> 16) & 0xff) - centers[center][0];
            double green = ((rgb >>> 8) & 0xff) - centers[center][1];
            double blue = (rgb & 0xff) - centers[center][2];
            double distance = red * red + green * green + blue * blue;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = center;
            }
        }
        return best;
    }
}
