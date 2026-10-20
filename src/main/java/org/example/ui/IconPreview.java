package org.example.ui;

import java.util.List;
import java.util.Objects;
import org.example.export.IconVariant;
import org.example.export.SvgLayer;

/**
 * @param sourceSvg the original image framed exactly like the variants, for before/after comparison
 */
public record IconPreview(List<IconVariant> variants, List<SvgLayer> layers, String sourceSvg) {
    public IconPreview {
        variants = List.copyOf(variants);
        layers = List.copyOf(layers);
        Objects.requireNonNull(sourceSvg, "Source preview is required");
    }
}
