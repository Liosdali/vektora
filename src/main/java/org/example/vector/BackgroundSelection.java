package org.example.vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record BackgroundSelection(
        List<PixelCoordinate> seeds,
        List<EraseStroke> strokes,
        List<EraseStroke> protectStrokes,
        int tolerance,
        boolean defringe) {
    public static final int DEFAULT_TOLERANCE = 24;
    public static final int MAX_TOLERANCE = 128;
    public static final BackgroundSelection EMPTY = new BackgroundSelection(List.of(), List.of());

    public BackgroundSelection {
        seeds = List.copyOf(Objects.requireNonNull(seeds, "Seeds are required"));
        strokes = List.copyOf(Objects.requireNonNull(strokes, "Strokes are required"));
        protectStrokes = List.copyOf(Objects.requireNonNull(protectStrokes, "Protect strokes are required"));
        if (tolerance < 0 || tolerance > MAX_TOLERANCE) {
            throw new IllegalArgumentException("Tolerance must be between 0 and " + MAX_TOLERANCE);
        }
    }

    public BackgroundSelection(List<PixelCoordinate> seeds, List<EraseStroke> strokes) {
        this(seeds, strokes, List.of(), DEFAULT_TOLERANCE, true);
    }

    public boolean hasEdits() {
        return !seeds.isEmpty() || !strokes.isEmpty() || !protectStrokes.isEmpty();
    }

    public BackgroundSelection withSeed(PixelCoordinate seed) {
        return new BackgroundSelection(append(seeds, seed), strokes, protectStrokes, tolerance, defringe);
    }

    public BackgroundSelection withStroke(EraseStroke stroke) {
        return new BackgroundSelection(seeds, append(strokes, stroke), protectStrokes, tolerance, defringe);
    }

    public BackgroundSelection withProtectStroke(EraseStroke stroke) {
        return new BackgroundSelection(seeds, strokes, append(protectStrokes, stroke), tolerance, defringe);
    }

    public BackgroundSelection withTolerance(int value) {
        return new BackgroundSelection(seeds, strokes, protectStrokes, value, defringe);
    }

    public BackgroundSelection withDefringe(boolean value) {
        return new BackgroundSelection(seeds, strokes, protectStrokes, tolerance, value);
    }

    public BackgroundSelection withoutEdits() {
        return new BackgroundSelection(List.of(), List.of(), List.of(), tolerance, defringe);
    }

    private static <T> List<T> append(List<T> values, T value) {
        List<T> copy = new ArrayList<>(values);
        copy.add(Objects.requireNonNull(value, "Selection entry is required"));
        return copy;
    }
}
