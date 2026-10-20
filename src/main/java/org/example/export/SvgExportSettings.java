package org.example.export;

/**
 * @param mosaic cut every layer out of the layers painted before it, so no two shapes overlap
 *               (for vinyl cutting and engraving)
 */
public record SvgExportSettings(int width, int height, double scale, boolean mosaic) {
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
    }

    public SvgExportSettings(int width, int height, double scale) {
        this(width, height, scale, false);
    }

    public SvgExportSettings withMosaic(boolean value) {
        return new SvgExportSettings(width, height, scale, value);
    }
}
