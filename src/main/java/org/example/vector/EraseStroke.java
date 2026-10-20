package org.example.vector;

import java.util.List;
import java.util.Objects;

public record EraseStroke(List<PixelCoordinate> points, int radius) {
    public EraseStroke {
        points = List.copyOf(Objects.requireNonNull(points, "Stroke points are required"));
        if (points.isEmpty()) {
            throw new IllegalArgumentException("Stroke must contain at least one point");
        }
        if (radius < 1) {
            throw new IllegalArgumentException("Stroke radius must be positive");
        }
    }
}
