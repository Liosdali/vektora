package org.example.export;

/**
 * @param mosaic   cut every layer out of the layers painted before it, so no two shapes overlap
 *                 (for vinyl cutting and engraving)
 * @param compact  write smaller SVG: shared path attributes move to the layer group and
 *                 neighboring paths of one layer are joined, without changing how it looks
 * @param decimals in compact output, round coordinates to this many decimals (0-2); -1 keeps them
 */
public record SvgExportSettings(int width, int height, double scale, boolean mosaic, boolean compact, int decimals) {
    public static final int MAX_DECIMALS = 2;

    public SvgExportSettings {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("SVG canvas width and height must be positive");
        }
        if (!Double.isFinite(scale) || scale <= 0) {
            throw new IllegalArgumentException("SVG scale must be a positive finite number");
        }
        if (!Double.isFinite(width * scale) || !Double.isFinite(height * scale)) {
            throw new IllegalArgumentException("Scaled SVG dimensions must remain finite");
        }
        if (decimals < -1 || decimals > MAX_DECIMALS) {
            throw new IllegalArgumentException("Decimals must be between 0 and " + MAX_DECIMALS + ", or -1");
        }
    }

    public SvgExportSettings(int width, int height, double scale) {
        this(width, height, scale, false);
    }

    public SvgExportSettings(int width, int height, double scale, boolean mosaic) {
        this(width, height, scale, mosaic, false, -1);
    }

    public SvgExportSettings withMosaic(boolean value) {
        return new SvgExportSettings(width, height, scale, value, compact, decimals);
    }

    public SvgExportSettings withCompact(boolean value, int roundTo) {
        return new SvgExportSettings(width, height, scale, mosaic, value, roundTo);
    }
}
