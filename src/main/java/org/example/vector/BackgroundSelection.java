package org.example.vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record BackgroundSelection(
        List<RegionSeed> regions,
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
        regions = List.copyOf(Objects.requireNonNull(regions, "Regions are required"));
        strokes = List.copyOf(Objects.requireNonNull(strokes, "Strokes are required"));
        protectStrokes = List.copyOf(Objects.requireNonNull(protectStrokes, "Protect strokes are required"));
        if (tolerance < 0 || tolerance > MAX_TOLERANCE) {
            throw new IllegalArgumentException("Tolerance must be between 0 and " + MAX_TOLERANCE);
        }
    }

    public BackgroundSelection(List<PixelCoordinate> seeds, List<EraseStroke> strokes) {
        this(seeds.stream().map(RegionSeed::at).toList(), strokes, List.of(), DEFAULT_TOLERANCE, true, false);
    }

    /** The clicked points of every selected region, in click order. */
    public List<PixelCoordinate> seeds() {
        return regions.stream().map(RegionSeed::point).toList();
    }

    public boolean hasEdits() {
        return !regions.isEmpty() || !strokes.isEmpty() || !protectStrokes.isEmpty() || keepSubjectOnly;
    }

    public BackgroundSelection withSeed(PixelCoordinate seed) {
        return withSeed(RegionSeed.at(seed));
    }

    public BackgroundSelection withSeed(RegionSeed seed) {
        return new BackgroundSelection(append(regions, seed), strokes, protectStrokes, tolerance, defringe,
                keepSubjectOnly);
    }

    public BackgroundSelection withStroke(EraseStroke stroke) {
        return new BackgroundSelection(regions, append(strokes, stroke), protectStrokes, tolerance, defringe,
                keepSubjectOnly);
    }

    public BackgroundSelection withProtectStroke(EraseStroke stroke) {
        return new BackgroundSelection(regions, strokes, append(protectStrokes, stroke), tolerance, defringe,
                keepSubjectOnly);
    }

    public BackgroundSelection withTolerance(int value) {
        return new BackgroundSelection(regions, strokes, protectStrokes, value, defringe, keepSubjectOnly);
    }

    public BackgroundSelection withDefringe(boolean value) {
        return new BackgroundSelection(regions, strokes, protectStrokes, tolerance, value, keepSubjectOnly);
    }

    /** Removes everything a subject mask marks as background, on top of the other edits. */
    public BackgroundSelection withKeepSubjectOnly(boolean value) {
        return new BackgroundSelection(regions, strokes, protectStrokes, tolerance, defringe, value);
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
