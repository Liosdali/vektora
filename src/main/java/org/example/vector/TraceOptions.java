package org.example.vector;

import java.awt.Color;
import java.util.List;
import java.util.Objects;

/**
 * @param simplification 0 keeps every traced detail; higher levels drop tiny specks and fit
 *                       straighter, smoother outlines (up to {@link #MAX_SIMPLIFICATION}).
 */
public record TraceOptions(ColorMode mode, int maxColors, List<Color> palette, int simplification) {
    public static final int MAX_SIMPLIFICATION = 4;

    public TraceOptions {
        Objects.requireNonNull(mode, "Color mode is required");
        palette = List.copyOf(Objects.requireNonNull(palette, "Palette is required"));
        if (maxColors < 1) {
            throw new IllegalArgumentException("Maximum color count must be positive");
        }
        if (mode == ColorMode.CUSTOM_PALETTE && palette.isEmpty()) {
            throw new IllegalArgumentException("A custom palette must contain at least one color");
        }
        if (mode != ColorMode.CUSTOM_PALETTE && !palette.isEmpty()) {
            throw new IllegalArgumentException("A palette is only valid in custom-palette mode");
        }
        if (simplification < 0 || simplification > MAX_SIMPLIFICATION) {
            throw new IllegalArgumentException("Simplification must be between 0 and " + MAX_SIMPLIFICATION);
        }
    }

    public TraceOptions(ColorMode mode, int maxColors, List<Color> palette) {
        this(mode, maxColors, palette, 0);
    }

    /** Eight colors at the "detailed" level: a few clean layers even for anti-aliased artwork. */
    public static TraceOptions recommended() {
        return new TraceOptions(ColorMode.REDUCE_SOURCE, 8, List.of(), 1);
    }

    public static TraceOptions preserveSourceColors() {
        return new TraceOptions(ColorMode.PRESERVE_SOURCE, 256, List.of());
    }

    public TraceOptions withSimplification(int level) {
        return new TraceOptions(mode, maxColors, palette, level);
    }

    public enum ColorMode {
        PRESERVE_SOURCE,
        REDUCE_SOURCE,
        CUSTOM_PALETTE
    }
}
