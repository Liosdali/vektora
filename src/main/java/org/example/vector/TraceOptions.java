package org.example.vector;

import java.awt.Color;
import java.util.List;
import java.util.Objects;

public record TraceOptions(ColorMode mode, int maxColors, List<Color> palette) {
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
    }

    public static TraceOptions preserveSourceColors() {
        return new TraceOptions(ColorMode.PRESERVE_SOURCE, 256, List.of());
    }

    public enum ColorMode {
        PRESERVE_SOURCE,
        REDUCE_SOURCE,
        CUSTOM_PALETTE
    }
}
