package org.example.export;

import java.util.Objects;

public record IconVariant(String iconName, String variantName, String svg) {
    public IconVariant {
        iconName = requireName(iconName, "Icon name");
        variantName = Objects.requireNonNull(variantName, "Variant name is required").trim();
        svg = Objects.requireNonNull(svg, "SVG source is required");
    }

    private static String requireName(String value, String label) {
        String name = Objects.requireNonNull(value, label + " is required").trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException(label + " must not be empty");
        }
        return name;
    }
}
