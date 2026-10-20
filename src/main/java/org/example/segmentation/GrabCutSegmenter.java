package org.example.segmentation;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.Objects;
import org.example.vector.EraseStroke;
import org.example.vector.SubjectMask;

/**
 * Separates the subject without a trained model, using GrabCut (Rother, Kolmogorov and Blake,
 * "GrabCut — Interactive Foreground Extraction using Iterated Graph Cuts", 2004). Foreground and
 * background each get a Gaussian color mixture; a graph cut then balances how well every pixel
 * fits either model against keeping similar neighbors together, and the two steps repeat.
 *
 * <p>The image edge is background and the middle is probably foreground; a drawn frame narrows
 * that guess and brush strokes pin pixels to either side. Large images are cut at about
 * {@value #MAX_WORKING_SIDE} pixels and only the boundary is cut again at full resolution.
 */
public final class GrabCutSegmenter implements SubjectSegmenter {
    static final int MAX_WORKING_SIDE = 512;
    private static final int ITERATIONS = 5;
    private static final double FRAME_INSET = 0.08;
    private static final double GAMMA = 50;
    private static final double LAMBDA = 9 * GAMMA;
    private static final double DIAGONAL = 1 / Math.sqrt(2);

    private static final byte BACKGROUND = 0;
    private static final byte FOREGROUND = 1;
    private static final byte PROBABLY_BACKGROUND = 2;
    private static final byte PROBABLY_FOREGROUND = 3;
    private static final byte UNSET = -1;

    @Override
    public SubjectMask segment(BufferedImage image) throws SegmentationException {
        return segment(image, SegmentationHints.NONE);
    }

    @Override
    public SubjectMask segment(BufferedImage image, SegmentationHints hints) throws SegmentationException {
        Objects.requireNonNull(image, "Image is required");
        Objects.requireNonNull(hints, "Hints are required");
        int width = image.getWidth();
        int height = image.getHeight();
        int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
        byte[] pinned = pinnedLabels(pixels, width, height, hints);

        double scale = Math.min(1, (double) MAX_WORKING_SIDE / Math.max(width, height));
        int workWidth = Math.max(1, (int) Math.round(width * scale));
        int workHeight = Math.max(1, (int) Math.round(height * scale));
        boolean scaled = workWidth != width || workHeight != height;
        int[] work = scaled ? downsample(pixels, width, height, workWidth, workHeight) : pixels;

        byte[] labels = initialLabels(work, workWidth, workHeight, frameIn(hints.frame(), width, height,
                workWidth, workHeight), hints.frame() != null);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                byte label = pinned[y * width + x];
                if (label != UNSET && visible(pixels[y * width + x])) {
                    labels[(y * workHeight / height) * workWidth + x * workWidth / width] = label;
                }
            }
        }

        Models models = iterate(work, workWidth, workHeight, labels);
        boolean[] subject = new boolean[width * height];
        if (models == null) {
            // One side had nothing to learn from, so every label went to the other side.
            java.util.Arrays.fill(subject, isForeground(labels[0]));
        } else if (scaled) {
            refineAtFullResolution(pixels, width, height, labels, workWidth, workHeight, pinned, models, subject);
        } else {
            for (int index = 0; index < subject.length; index++) {
                subject[index] = isForeground(labels[index]);
            }
        }

        byte[] foreground = new byte[width * height];
        for (int index = 0; index < foreground.length; index++) {
            if (subject[index] && visible(pixels[index])) {
                foreground[index] = (byte) 255;
            }
        }
        return new SubjectMask(width, height, foreground);
    }

    private record Models(ColorMixtureModel foreground, ColorMixtureModel background, double beta) {
    }

    /**
     * Alternates fitting the color models to the current labels and re-cutting the graph. Returns
     * null when one side has no pixels to learn from, leaving every label where it started.
     */
    private static Models iterate(int[] colors, int width, int height, byte[] labels) {
        int[] foregroundSamples = new int[colors.length];
        int[] backgroundSamples = new int[colors.length];
        double beta = beta(colors, width, height);
        ColorMixtureModel foreground = null;
        ColorMixtureModel background = null;
        for (int iteration = 0; iteration < ITERATIONS; iteration++) {
            int foregroundCount = 0;
            int backgroundCount = 0;
            // The first background model learns only from certain background, such as the image
            // edge: the margin around the frame may still hold artwork that would teach it wrong.
            boolean certainOnly = iteration == 0 && hasCertainBackground(colors, labels);
            for (int index = 0; index < colors.length; index++) {
                if (!visible(colors[index])) {
                    continue;
                }
                if (isForeground(labels[index])) {
                    foregroundSamples[foregroundCount++] = colors[index];
                } else if (!certainOnly || labels[index] == BACKGROUND) {
                    backgroundSamples[backgroundCount++] = colors[index];
                }
            }
            if (foregroundCount == 0 || backgroundCount == 0) {
                if (iteration == 0) {
                    for (int index = 0; index < labels.length; index++) {
                        labels[index] = foregroundCount == 0 ? BACKGROUND : FOREGROUND;
                    }
                    return null;
                }
                break;
            }
            foreground = foreground == null ? ColorMixtureModel.fit(foregroundSamples, foregroundCount)
                    : foreground.refit(foregroundSamples, foregroundCount);
            background = background == null ? ColorMixtureModel.fit(backgroundSamples, backgroundCount)
                    : background.refit(backgroundSamples, backgroundCount);
            cut(colors, width, height, labels, foreground, background, beta);
        }
        return new Models(foreground, background, beta);
    }

    private static boolean hasCertainBackground(int[] colors, byte[] labels) {
        for (int index = 0; index < colors.length; index++) {
            if (labels[index] == BACKGROUND && visible(colors[index])) {
                return true;
            }
        }
        return false;
    }

    private static void cut(int[] colors, int width, int height, byte[] labels,
                            ColorMixtureModel foreground, ColorMixtureModel background, double beta) {
        MaxFlowGraph graph = new MaxFlowGraph(colors.length, colors.length * 4);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int index = y * width + x;
                addDataTerm(graph, index, labels[index], colors[index], foreground, background);
                if (x > 0) {
                    addSmoothness(graph, colors, index, index - 1, beta, 1);
                }
                if (y > 0) {
                    addSmoothness(graph, colors, index, index - width, beta, 1);
                    if (x > 0) {
                        addSmoothness(graph, colors, index, index - width - 1, beta, DIAGONAL);
                    }
                    if (x + 1 < width) {
                        addSmoothness(graph, colors, index, index - width + 1, beta, DIAGONAL);
                    }
                }
            }
        }
        graph.maxFlow();
        for (int index = 0; index < labels.length; index++) {
            if (labels[index] == PROBABLY_BACKGROUND || labels[index] == PROBABLY_FOREGROUND) {
                labels[index] = graph.isSource(index) ? PROBABLY_FOREGROUND : PROBABLY_BACKGROUND;
            }
        }
    }

    private static void addDataTerm(MaxFlowGraph graph, int node, byte label, int color,
                                    ColorMixtureModel foreground, ColorMixtureModel background) {
        switch (label) {
            case BACKGROUND -> graph.addTerminalWeights(node, 0, LAMBDA);
            case FOREGROUND -> graph.addTerminalWeights(node, LAMBDA, 0);
            default -> graph.addTerminalWeights(node, cost(background, color), cost(foreground, color));
        }
    }

    /** The penalty for giving the color to a model: its negative log-likelihood. */
    private static double cost(ColorMixtureModel model, int color) {
        return -Math.log(Math.max(model.probability(color), Double.MIN_NORMAL));
    }

    private static void addSmoothness(MaxFlowGraph graph, int[] colors, int first, int second,
                                      double beta, double factor) {
        double weight = smoothness(colors[first], colors[second], beta, factor);
        if (weight > 0) {
            graph.addEdge(first, second, weight, weight);
        }
    }

    private static double smoothness(int first, int second, double beta, double factor) {
        if (!visible(first) || !visible(second)) {
            return 0;
        }
        return GAMMA * factor * Math.exp(-beta * squaredDistance(first, second));
    }

    /**
     * Cuts again only where the upscaled labels change, at full resolution: band pixels become
     * graph nodes and their neighbors outside the band pull them through fixed terminal weights.
     */
    private static void refineAtFullResolution(
            int[] pixels, int width, int height, byte[] labels, int workWidth, int workHeight,
            byte[] pinned, Models models, boolean[] subject) {
        int[] node = new int[width * height];
        int nodeCount = 0;
        for (int y = 0; y < height; y++) {
            int workY = y * workHeight / height;
            for (int x = 0; x < width; x++) {
                int index = y * width + x;
                int workX = x * workWidth / width;
                subject[index] = isForeground(labels[workY * workWidth + workX]);
                if (pinned[index] != UNSET) {
                    subject[index] = pinned[index] == FOREGROUND;
                }
                boolean boundary = pinned[index] == UNSET && visible(pixels[index])
                        && changesNearby(labels, workWidth, workHeight, workX, workY);
                node[index] = boundary ? nodeCount++ : -1;
            }
        }
        if (nodeCount == 0) {
            return;
        }

        double beta = beta(pixels, width, height);
        MaxFlowGraph graph = new MaxFlowGraph(nodeCount, nodeCount * 4);
        int[][] offsets = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}, {-1, -1}, {1, -1}, {-1, 1}, {1, 1}};
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int index = y * width + x;
                if (node[index] < 0) {
                    continue;
                }
                double toSource = cost(models.background(), pixels[index]);
                double toSink = cost(models.foreground(), pixels[index]);
                for (int[] offset : offsets) {
                    int nx = x + offset[0];
                    int ny = y + offset[1];
                    if (nx < 0 || ny < 0 || nx >= width || ny >= height) {
                        continue;
                    }
                    int neighbor = ny * width + nx;
                    double weight = smoothness(pixels[index], pixels[neighbor], beta,
                            offset[0] != 0 && offset[1] != 0 ? DIAGONAL : 1);
                    if (node[neighbor] >= 0) {
                        if (neighbor < index && weight > 0) {
                            graph.addEdge(node[index], node[neighbor], weight, weight);
                        }
                    } else if (subject[neighbor]) {
                        toSource += weight;
                    } else {
                        toSink += weight;
                    }
                }
                graph.addTerminalWeights(node[index], toSource, toSink);
            }
        }
        graph.maxFlow();
        for (int index = 0; index < node.length; index++) {
            if (node[index] >= 0) {
                subject[index] = graph.isSource(node[index]);
            }
        }
    }

    private static boolean changesNearby(byte[] labels, int width, int height, int x, int y) {
        boolean foreground = isForeground(labels[y * width + x]);
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                int nx = x + dx;
                int ny = y + dy;
                if (nx >= 0 && ny >= 0 && nx < width && ny < height
                        && isForeground(labels[ny * width + nx]) != foreground) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Pixels the user pinned with brushes: remove strokes first, keep strokes win. */
    private static byte[] pinnedLabels(int[] pixels, int width, int height, SegmentationHints hints) {
        boolean[] removed = new boolean[width * height];
        for (EraseStroke stroke : hints.remove()) {
            stroke.paint(removed, width, height);
        }
        boolean[] kept = new boolean[width * height];
        for (EraseStroke stroke : hints.keep()) {
            stroke.paint(kept, width, height);
        }
        byte[] pinned = new byte[width * height];
        for (int index = 0; index < pinned.length; index++) {
            pinned[index] = !visible(pixels[index]) || (removed[index] && !kept[index]) ? BACKGROUND
                    : kept[index] ? FOREGROUND : UNSET;
        }
        return pinned;
    }

    /**
     * Inside the frame is probably foreground. Without a drawn frame the area around the default
     * one is only probably background, so artwork reaching into the margin can still be kept, and
     * the outermost pixels are background; outside a drawn frame everything is background.
     */
    private static byte[] initialLabels(int[] colors, int width, int height, Rectangle frame, boolean drawn) {
        byte[] labels = new byte[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int index = y * width + x;
                boolean edge = x == 0 || y == 0 || x == width - 1 || y == height - 1;
                if (!visible(colors[index])) {
                    labels[index] = BACKGROUND;
                } else if (frame.contains(x, y) && (drawn || !edge)) {
                    labels[index] = PROBABLY_FOREGROUND;
                } else {
                    labels[index] = drawn || edge ? BACKGROUND : PROBABLY_BACKGROUND;
                }
            }
        }
        return labels;
    }

    private static Rectangle frameIn(Rectangle frame, int width, int height, int workWidth, int workHeight) {
        if (frame == null) {
            int insetX = (int) Math.round(workWidth * FRAME_INSET);
            int insetY = (int) Math.round(workHeight * FRAME_INSET);
            return new Rectangle(insetX, insetY, workWidth - 2 * insetX, workHeight - 2 * insetY);
        }
        Rectangle clipped = frame.intersection(new Rectangle(0, 0, width, height));
        int left = clipped.x * workWidth / width;
        int top = clipped.y * workHeight / height;
        int right = (int) Math.ceil((double) (clipped.x + clipped.width) * workWidth / width);
        int bottom = (int) Math.ceil((double) (clipped.y + clipped.height) * workHeight / height);
        return new Rectangle(left, top, Math.max(0, right - left), Math.max(0, bottom - top));
    }

    /** Box-filters the image down; a cell is transparent when most of its pixels are. */
    private static int[] downsample(int[] pixels, int width, int height, int workWidth, int workHeight) {
        int[] work = new int[workWidth * workHeight];
        for (int workY = 0; workY < workHeight; workY++) {
            int top = workY * height / workHeight;
            int bottom = Math.max(top + 1, (workY + 1) * height / workHeight);
            for (int workX = 0; workX < workWidth; workX++) {
                int left = workX * width / workWidth;
                int right = Math.max(left + 1, (workX + 1) * width / workWidth);
                long red = 0;
                long green = 0;
                long blue = 0;
                long opaque = 0;
                for (int y = top; y < bottom; y++) {
                    for (int x = left; x < right; x++) {
                        int pixel = pixels[y * width + x];
                        if (visible(pixel)) {
                            red += (pixel >>> 16) & 0xff;
                            green += (pixel >>> 8) & 0xff;
                            blue += pixel & 0xff;
                            opaque++;
                        }
                    }
                }
                long cells = (long) (bottom - top) * (right - left);
                if (opaque * 2 >= cells && opaque > 0) {
                    work[workY * workWidth + workX] = 0xff000000
                            | (int) Math.round((double) red / opaque) << 16
                            | (int) Math.round((double) green / opaque) << 8
                            | (int) Math.round((double) blue / opaque);
                }
            }
        }
        return work;
    }

    /** 1 / (2 · mean squared color difference between neighbors), as in the GrabCut paper. */
    private static double beta(int[] colors, int width, int height) {
        double total = 0;
        long pairs = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int index = y * width + x;
                if (!visible(colors[index])) {
                    continue;
                }
                int[] neighbors = {
                        x > 0 ? index - 1 : -1,
                        y > 0 ? index - width : -1,
                        x > 0 && y > 0 ? index - width - 1 : -1,
                        x + 1 < width && y > 0 ? index - width + 1 : -1};
                for (int neighbor : neighbors) {
                    if (neighbor >= 0 && visible(colors[neighbor])) {
                        total += squaredDistance(colors[index], colors[neighbor]);
                        pairs++;
                    }
                }
            }
        }
        return total <= 0 ? 0 : 1 / (2 * total / pairs);
    }

    private static double squaredDistance(int first, int second) {
        int red = ((first >>> 16) & 0xff) - ((second >>> 16) & 0xff);
        int green = ((first >>> 8) & 0xff) - ((second >>> 8) & 0xff);
        int blue = (first & 0xff) - (second & 0xff);
        return red * red + green * green + blue * blue;
    }

    private static boolean isForeground(byte label) {
        return label == FOREGROUND || label == PROBABLY_FOREGROUND;
    }

    private static boolean visible(int pixel) {
        return (pixel >>> 24) != 0;
    }
}
