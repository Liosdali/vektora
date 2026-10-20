package org.example.export;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Changes to an icon's color layers, each keyed by the color the tracer gave the layer.
 *
 * @param hidden    layers removed from the drawing
 * @param recolored new {@code #rrggbb} colors; a layer given another layer's color joins it
 * @param names     names written as SVG ids and Inkscape layer labels
 * @param order     paint order, first painted first; unlisted layers follow in traced order
 */
public record LayerEdits(Set<String> hidden, Map<String, String> recolored, Map<String, String> names,
                         List<String> order) {
    public static final LayerEdits NONE = new LayerEdits(Set.of(), Map.of(), Map.of(), List.of());

    public LayerEdits {
        hidden = Set.copyOf(Objects.requireNonNull(hidden, "Hidden layers are required"));
        recolored = Map.copyOf(Objects.requireNonNull(recolored, "Recolored layers are required"));
        names = Map.copyOf(Objects.requireNonNull(names, "Layer names are required"));
        order = List.copyOf(Objects.requireNonNull(order, "Layer order is required"));
    }

    public boolean isEmpty() {
        return hidden.isEmpty() && recolored.isEmpty() && names.isEmpty() && order.isEmpty();
    }
}
