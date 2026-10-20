package org.example.vector;

import java.util.List;
import java.util.Objects;

public record BackgroundSelection(List<PixelCoordinate> seeds, List<EraseStroke> strokes) {
    public static final BackgroundSelection EMPTY = new BackgroundSelection(List.of(), List.of());

    public BackgroundSelection {
        seeds = List.copyOf(Objects.requireNonNull(seeds, "Seeds are required"));
        strokes = List.copyOf(Objects.requireNonNull(strokes, "Strokes are required"));
    }
}
