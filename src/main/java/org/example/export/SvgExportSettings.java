package org.example.export;

public record SvgExportSettings(int width, int height, double scale) {
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
}
