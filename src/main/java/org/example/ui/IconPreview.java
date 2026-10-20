package org.example.ui;

import java.util.List;
import org.example.export.IconVariant;
import org.example.export.SvgLayer;

public record IconPreview(List<IconVariant> variants, List<SvgLayer> layers) {
    public IconPreview {
        variants = List.copyOf(variants);
        layers = List.copyOf(layers);
    }
}
