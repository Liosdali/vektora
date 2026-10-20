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

    /** Marks every pixel the round brush covers along the stroke in a row-major mask. */
    public void paint(boolean[] mask, int width, int height) {
        PixelCoordinate previous = points.getFirst();
        stamp(previous.x(), previous.y(), mask, width, height);
        for (int index = 1; index < points.size(); index++) {
            PixelCoordinate next = points.get(index);
            int steps = Math.max(Math.abs(next.x() - previous.x()), Math.abs(next.y() - previous.y()));
            for (int step = 1; step <= steps; step++) {
                int x = previous.x() + Math.round((float) (next.x() - previous.x()) * step / steps);
                int y = previous.y() + Math.round((float) (next.y() - previous.y()) * step / steps);
                stamp(x, y, mask, width, height);
            }
            previous = next;
        }
    }

    private void stamp(int centerX, int centerY, boolean[] mask, int width, int height) {
        for (int y = Math.max(0, centerY - radius); y <= Math.min(height - 1, centerY + radius); y++) {
            for (int x = Math.max(0, centerX - radius); x <= Math.min(width - 1, centerX + radius); x++) {
                int dx = x - centerX;
                int dy = y - centerY;
                if (dx * dx + dy * dy <= radius * radius) {
                    mask[y * width + x] = true;
                }
            }
        }
    }
}
