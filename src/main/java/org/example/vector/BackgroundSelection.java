package org.example.vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record BackgroundSelection(
        List<PixelCoordinate> seeds,
        List<EraseStroke> strokes,
        List<EraseStroke> protectStrokes,
        int tolerance,
        boolean defringe,
        boolean keepSubjectOnly) {
    /** Tolerances are CIE76 ΔE distances from the clicked color. */
    public static final int DEFAULT_TOLERANCE = 12;
    public static final int MAX_TOLERANCE = 60;
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
        this(seeds, strokes, List.of(), DEFAULT_TOLERANCE, true, false);
    }

    public boolean hasEdits() {
        return !seeds.isEmpty() || !strokes.isEmpty() || !protectStrokes.isEmpty() || keepSubjectOnly;
    }

    public BackgroundSelection withSeed(PixelCoordinate seed) {
        return new BackgroundSelection(append(seeds, seed), strokes, protectStrokes, tolerance, defringe,
                keepSubjectOnly);
    }

    public BackgroundSelection withStroke(EraseStroke stroke) {
        return new BackgroundSelection(seeds, append(strokes, stroke), protectStrokes, tolerance, defringe,
                keepSubjectOnly);
    }

    public BackgroundSelection withProtectStroke(EraseStroke stroke) {
        return new BackgroundSelection(seeds, strokes, append(protectStrokes, stroke), tolerance, defringe,
                keepSubjectOnly);
    }

    public BackgroundSelection withTolerance(int value) {
        return new BackgroundSelection(seeds, strokes, protectStrokes, value, defringe, keepSubjectOnly);
    }

    public BackgroundSelection withDefringe(boolean value) {
        return new BackgroundSelection(seeds, strokes, protectStrokes, tolerance, value, keepSubjectOnly);
    }

    /** Removes everything a subject mask marks as background, on top of the other edits. */
    public BackgroundSelection withKeepSubjectOnly(boolean value) {
        return new BackgroundSelection(seeds, strokes, protectStrokes, tolerance, defringe, value);
    }

    public BackgroundSelection withoutEdits() {
        return new BackgroundSelection(List.of(), List.of(), List.of(), tolerance, defringe, false);
    }

    private static <T> List<T> append(List<T> values, T value) {
        List<T> copy = new ArrayList<>(values);
        copy.add(Objects.requireNonNull(value, "Selection entry is required"));
        return copy;
    }
}
