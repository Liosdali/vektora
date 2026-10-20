package org.example.export;

import java.util.Objects;

public record SvgLayer(String color, int pathCount) {
    public SvgLayer {
        Objects.requireNonNull(color, "Layer color is required");
        if (pathCount < 1) {
            throw new IllegalArgumentException("A layer contains at least one path");
        }
    }
}
