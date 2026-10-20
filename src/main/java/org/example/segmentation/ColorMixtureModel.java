package org.example.segmentation;

import java.util.Arrays;

/**
 * A Gaussian mixture over RGB colors with full covariances, as GrabCut uses for the foreground
 * and background color models. Fitting is deterministic: components start from equal-count
 * brightness slices and are refined with k-means, so the same samples always give the same model.
 */
final class ColorMixtureModel {
    static final int COMPONENTS = 5;
    private static final int KMEANS_PASSES = 10;
    // Flat synthetic colors have no variance; this keeps their covariance invertible.
    private static final double MIN_VARIANCE = 0.01;
    private static final double MIN_DETERMINANT = 1e-12;

    private final double[] weights;
    private final double[][] means;
    private final double[][] inverses;
    private final double[] scales;

    private ColorMixtureModel(double[] weights, double[][] means, double[][] inverses, double[] scales) {
        this.weights = weights;
        this.means = means;
        this.inverses = inverses;
        this.scales = scales;
    }

    /** Fits a new model to the first {@code count} RGB samples. */
    static ColorMixtureModel fit(int[] samples, int count) {
        return learn(samples, count, kMeansLabels(samples, count));
    }

    /** Assigns each sample to this model's most likely component and fits the components again. */
    ColorMixtureModel refit(int[] samples, int count) {
        int[] labels = new int[count];
        for (int index = 0; index < count; index++) {
            labels[index] = mostLikelyComponent(samples[index]);
        }
        return learn(samples, count, labels);
    }

    /** The mixture density of the color, up to a constant factor shared by every model. */
    double probability(int rgb) {
        double total = 0;
        for (int component = 0; component < COMPONENTS; component++) {
            if (weights[component] > 0) {
                total += weights[component] * density(component, rgb);
            }
        }
        return total;
    }

    int mostLikelyComponent(int rgb) {
        int best = 0;
        double bestDensity = -1;
        for (int component = 0; component < COMPONENTS; component++) {
            if (weights[component] > 0) {
                double value = density(component, rgb);
                if (value > bestDensity) {
                    bestDensity = value;
                    best = component;
                }
            }
        }
        return best;
    }

    private double density(int component, int rgb) {
        double[] mean = means[component];
        double red = ((rgb >>> 16) & 0xff) - mean[0];
        double green = ((rgb >>> 8) & 0xff) - mean[1];
        double blue = (rgb & 0xff) - mean[2];
        double[] inverse = inverses[component];
        double distance = red * (red * inverse[0] + green * inverse[3] + blue * inverse[6])
                + green * (red * inverse[1] + green * inverse[4] + blue * inverse[7])
                + blue * (red * inverse[2] + green * inverse[5] + blue * inverse[8]);
        return scales[component] * Math.exp(-0.5 * distance);
    }

    private static ColorMixtureModel learn(int[] samples, int count, int[] labels) {
        double[][] sums = new double[COMPONENTS][9];
        long[] counts = new long[COMPONENTS];
        for (int index = 0; index < count; index++) {
            int rgb = samples[index];
            double red = (rgb >>> 16) & 0xff;
            double green = (rgb >>> 8) & 0xff;
            double blue = rgb & 0xff;
            double[] sum = sums[labels[index]];
            sum[0] += red;
            sum[1] += green;
            sum[2] += blue;
            sum[3] += red * red;
            sum[4] += red * green;
            sum[5] += red * blue;
            sum[6] += green * green;
            sum[7] += green * blue;
            sum[8] += blue * blue;
            counts[labels[index]]++;
        }

        double[] weights = new double[COMPONENTS];
        double[][] means = new double[COMPONENTS][3];
        double[][] inverses = new double[COMPONENTS][9];
        double[] scales = new double[COMPONENTS];
        for (int component = 0; component < COMPONENTS; component++) {
            long n = counts[component];
            if (n == 0) {
                continue;
            }
            double[] sum = sums[component];
            double[] mean = {sum[0] / n, sum[1] / n, sum[2] / n};
            double[] covariance = {
                    sum[3] / n - mean[0] * mean[0], sum[4] / n - mean[0] * mean[1], sum[5] / n - mean[0] * mean[2],
                    sum[4] / n - mean[0] * mean[1], sum[6] / n - mean[1] * mean[1], sum[7] / n - mean[1] * mean[2],
                    sum[5] / n - mean[0] * mean[2], sum[7] / n - mean[1] * mean[2], sum[8] / n - mean[2] * mean[2]};
            double determinant = determinant(covariance);
            if (determinant <= MIN_DETERMINANT) {
                covariance[0] += MIN_VARIANCE;
                covariance[4] += MIN_VARIANCE;
                covariance[8] += MIN_VARIANCE;
                determinant = determinant(covariance);
            }
            weights[component] = (double) n / count;
            means[component] = mean;
            inverses[component] = inverse(covariance, determinant);
            scales[component] = 1 / Math.sqrt(determinant);
        }
        return new ColorMixtureModel(weights, means, inverses, scales);
    }

    /** k-means labels starting from equal-count slices of the samples ordered by brightness. */
    private static int[] kMeansLabels(int[] samples, int count) {
        if (count < 1) {
            throw new IllegalArgumentException("A color model needs at least one sample");
        }
        long[] byBrightness = new long[count];
        for (int index = 0; index < count; index++) {
            int rgb = samples[index];
            long brightness = 299L * ((rgb >>> 16) & 0xff) + 587L * ((rgb >>> 8) & 0xff) + 114L * (rgb & 0xff);
            byBrightness[index] = brightness << 32 | index;
        }
        Arrays.sort(byBrightness);
        int[] labels = new int[count];
        for (int rank = 0; rank < count; rank++) {
            labels[(int) byBrightness[rank]] = (int) ((long) rank * COMPONENTS / count);
        }

        for (int pass = 0; pass < KMEANS_PASSES; pass++) {
            double[][] centers = new double[COMPONENTS][4];
            for (int index = 0; index < count; index++) {
                int rgb = samples[index];
                double[] center = centers[labels[index]];
                center[0] += (rgb >>> 16) & 0xff;
                center[1] += (rgb >>> 8) & 0xff;
                center[2] += rgb & 0xff;
                center[3]++;
            }
            boolean changed = false;
            for (int index = 0; index < count; index++) {
                int rgb = samples[index];
                int best = labels[index];
                double bestDistance = Double.MAX_VALUE;
                for (int component = 0; component < COMPONENTS; component++) {
                    double[] center = centers[component];
                    if (center[3] == 0) {
                        continue;
                    }
                    double red = ((rgb >>> 16) & 0xff) - center[0] / center[3];
                    double green = ((rgb >>> 8) & 0xff) - center[1] / center[3];
                    double blue = (rgb & 0xff) - center[2] / center[3];
                    double distance = red * red + green * green + blue * blue;
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = component;
                    }
                }
                changed |= best != labels[index];
                labels[index] = best;
            }
            if (!changed) {
                break;
            }
        }
        return labels;
    }

    private static double determinant(double[] m) {
        return m[0] * (m[4] * m[8] - m[5] * m[7])
                - m[1] * (m[3] * m[8] - m[5] * m[6])
                + m[2] * (m[3] * m[7] - m[4] * m[6]);
    }

    private static double[] inverse(double[] m, double determinant) {
        return new double[] {
                (m[4] * m[8] - m[5] * m[7]) / determinant,
                (m[2] * m[7] - m[1] * m[8]) / determinant,
                (m[1] * m[5] - m[2] * m[4]) / determinant,
                (m[5] * m[6] - m[3] * m[8]) / determinant,
                (m[0] * m[8] - m[2] * m[6]) / determinant,
                (m[2] * m[3] - m[0] * m[5]) / determinant,
                (m[3] * m[7] - m[4] * m[6]) / determinant,
                (m[1] * m[6] - m[0] * m[7]) / determinant,
                (m[0] * m[4] - m[1] * m[3]) / determinant};
    }
}
