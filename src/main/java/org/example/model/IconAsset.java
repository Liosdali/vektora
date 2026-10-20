package org.example.model;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.Objects;

public record IconAsset(Path source, String name, BufferedImage image) {
    public IconAsset {
        source = Objects.requireNonNull(source, "Source path is required").toAbsolutePath().normalize();
        name = Objects.requireNonNull(name, "Icon name is required").trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Icon name must not be empty");
        }
        Objects.requireNonNull(image, "Decoded icon image is required");
    }
}
