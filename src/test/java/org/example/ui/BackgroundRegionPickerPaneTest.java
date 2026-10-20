package org.example.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.example.vector.PixelCoordinate;
import org.junit.jupiter.api.Test;

class BackgroundRegionPickerPaneTest {
    @Test
    void mapsAspectRatioFittedViewCoordinatesToSourcePixels() {
        Optional<PixelCoordinate> coordinate = BackgroundRegionPickerPane.sourceCoordinate(
                30, 30, 10, 20, 40, 20, 4, 2);

        assertEquals(Optional.of(new PixelCoordinate(2, 1)), coordinate);
    }

    @Test
    void mapsTheImageCornersToTheFirstAndLastSourcePixels() {
        Optional<PixelCoordinate> topLeft = BackgroundRegionPickerPane.sourceCoordinate(
                10, 20, 10, 20, 40, 20, 4, 2);
        Optional<PixelCoordinate> bottomRight = BackgroundRegionPickerPane.sourceCoordinate(
                49.99, 39.99, 10, 20, 40, 20, 4, 2);

        assertEquals(Optional.of(new PixelCoordinate(0, 0)), topLeft);
        assertEquals(Optional.of(new PixelCoordinate(3, 1)), bottomRight);
    }

    @Test
    void ignoresClicksInLetterboxedMarginsAndOutsideImageBounds() {
        assertTrue(BackgroundRegionPickerPane.sourceCoordinate(
                9.99, 30, 10, 20, 40, 20, 4, 2).isEmpty());
        assertTrue(BackgroundRegionPickerPane.sourceCoordinate(
                50, 30, 10, 20, 40, 20, 4, 2).isEmpty());
        assertTrue(BackgroundRegionPickerPane.sourceCoordinate(
                30, 40, 10, 20, 40, 20, 4, 2).isEmpty());
    }
}
