package org.example.vector;

/**
 * Converts sRGB colors to CIELAB (D65) and measures their CIE76 distance, so color tolerances
 * follow what the eye sees: a ΔE of about 2 is barely noticeable, 10 is clearly different.
 */
public final class LabColor {
    private static final double[] LINEAR = new double[256];
    private static final double EPSILON = 216.0 / 24389.0;
    private static final double KAPPA = 24389.0 / 27.0;

    static {
        for (int value = 0; value < LINEAR.length; value++) {
            double channel = value / 255.0;
            LINEAR[value] = channel <= 0.04045 ? channel / 12.92 : Math.pow((channel + 0.055) / 1.055, 2.4);
        }
    }

    private LabColor() {
    }

    /** The L*, a* and b* values of an RGB color; alpha is ignored. */
    public static double[] lab(int rgb) {
        double red = LINEAR[(rgb >>> 16) & 0xff];
        double green = LINEAR[(rgb >>> 8) & 0xff];
        double blue = LINEAR[rgb & 0xff];
        double x = f((0.4124564 * red + 0.3575761 * green + 0.1804375 * blue) / 0.95047);
        double y = f(0.2126729 * red + 0.7151522 * green + 0.0721750 * blue);
        double z = f((0.0193339 * red + 0.1191920 * green + 0.9503041 * blue) / 1.08883);
        return new double[] {116 * y - 16, 500 * (x - y), 200 * (y - z)};
    }

    public static double deltaE(int first, int second) {
        return deltaE(lab(first), lab(second));
    }

    public static double deltaE(double[] first, double[] second) {
        double lightness = first[0] - second[0];
        double a = first[1] - second[1];
        double b = first[2] - second[2];
        return Math.sqrt(lightness * lightness + a * a + b * b);
    }

    private static double f(double value) {
        return value > EPSILON ? Math.cbrt(value) : (KAPPA * value + 16) / 116;
    }
}
