package org.example.vector;

import java.util.Objects;

/**
 * A background click and how the removed region grows from it.
 *
 * @param followGradient compare each pixel with the smoothed color of the region next to it rather
 *                       than with the clicked color, so slow gradients are removed in one click
 * @param contiguous     only remove pixels connected to the click; when false every pixel of a
 *                       similar color is removed, like a color key
 */
public record RegionSeed(PixelCoordinate point, boolean followGradient, boolean contiguous) {
    public RegionSeed {
        Objects.requireNonNull(point, "Seed point is required");
    }

    /** A plain click: the connected area close to the clicked color. */
    public static RegionSeed at(PixelCoordinate point) {
        return new RegionSeed(point, false, true);
    }
}
