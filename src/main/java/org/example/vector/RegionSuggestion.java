package org.example.vector;

import java.util.Objects;

/**
 * A connected same-color region that is a likely background candidate.
 *
 * @param seed          a pixel that removes this region when selected as a background seed
 * @param rgb           the seed's color without alpha
 * @param share         the region's fraction of all visible pixels, from 0 to 1
 * @param touchesBorder whether the region reaches the image edge
 */
public record RegionSuggestion(PixelCoordinate seed, int rgb, int pixelCount, double share, boolean touchesBorder) {
    public RegionSuggestion {
        Objects.requireNonNull(seed, "Seed is required");
        if (pixelCount < 1 || share <= 0 || share > 1) {
            throw new IllegalArgumentException("A suggested region must contain visible pixels");
        }
    }
}
