package org.example.export;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/** Treats every fill color of a traced SVG as one layer. */
public final class SvgLayers {
    private static final String SVG_NAMESPACE = "http://www.w3.org/2000/svg";
    /** Path attributes that carry layer edits to the exporter; they never reach exported files. */
    static final String LAYER_NAME = "data-layer-name";
    static final String LAYER_RANK = "data-layer-rank";
    private static final Pattern RGB = Pattern.compile(
            "rgba?\\(\\s*(\\d{1,3})\\s*,\\s*(\\d{1,3})\\s*,\\s*(\\d{1,3})\\s*(?:,[^)]*)?\\)");

    private SvgLayers() {
    }

    public static List<SvgLayer> layers(String svg) throws IOException {
        Map<String, Integer> counts = new LinkedHashMap<>();
        NodeList paths = parse(svg).getElementsByTagNameNS(SVG_NAMESPACE, "path");
        for (int index = 0; index < paths.getLength(); index++) {
            String color = visibleColor((Element) paths.item(index));
            if (color != null) {
                counts.merge(color, 1, Integer::sum);
            }
        }
        List<SvgLayer> layers = new ArrayList<>(counts.size());
        counts.forEach((color, count) -> layers.add(new SvgLayer(color, count)));
        return List.copyOf(layers);
    }

    /**
     * Marks every path of the given colors as removed. Removed paths become holes in the paths
     * painted before them when the SVG is exported, so the area turns transparent instead of
     * revealing whatever ImageTracer stacked underneath.
     */
    public static String withColorsRemoved(String svg, Set<String> colors) throws IOException {
        return withLayerChanges(svg, colors, Map.of());
    }

    /**
     * Removes the layers in {@code removed} (see {@link #withColorsRemoved}) and repaints the
     * layers keyed in {@code recolored} with their mapped {@code #rrggbb} color. Both are keyed by
     * the traced color, so a recolored layer can still be found and changed again.
     */
    public static String withLayerChanges(String svg, Set<String> removed, Map<String, String> recolored)
            throws IOException {
        Objects.requireNonNull(removed, "Removed colors are required");
        Objects.requireNonNull(recolored, "Recolored layers are required");
        return withLayerEdits(svg, new LayerEdits(removed, recolored, Map.of(), List.of()));
    }

    /**
     * Applies hiding and recoloring like {@link #withLayerChanges}, and marks each visible path
     * with its layer's name and paint rank for the exporter. A layer recolored to another traced
     * layer's color counts as that layer, so merged layers share one name, rank and group.
     */
    public static String withLayerEdits(String svg, LayerEdits edits) throws IOException {
        Objects.requireNonNull(edits, "Layer edits are required");
        edits.recolored().values().forEach(SvgLayers::requireHexColor);
        if (edits.isEmpty()) {
            return svg;
        }
        Document document = parse(svg);
        NodeList paths = document.getElementsByTagNameNS(SVG_NAMESPACE, "path");
        List<String> traced = new ArrayList<>();
        for (int index = 0; index < paths.getLength(); index++) {
            String color = visibleColor((Element) paths.item(index));
            if (color != null && !traced.contains(color)) {
                traced.add(color);
            }
        }
        boolean changed = false;
        for (int index = 0; index < paths.getLength(); index++) {
            Element path = (Element) paths.item(index);
            String color = visibleColor(path);
            if (color == null) {
                continue;
            }
            if (edits.hidden().contains(color)) {
                path.setAttribute("opacity", "0.0");
                changed = true;
                continue;
            }
            String layer = color;
            if (edits.recolored().containsKey(color)) {
                String replacement = edits.recolored().get(color);
                if (traced.contains(replacement)) {
                    // Merged into another layer: take on that layer's color, even a changed one.
                    layer = replacement;
                    replacement = edits.recolored().getOrDefault(replacement, replacement);
                }
                path.setAttribute("fill", replacement);
                if (!path.getAttribute("stroke").isBlank() && !"none".equals(path.getAttribute("stroke"))) {
                    path.setAttribute("stroke", replacement);
                }
                changed = true;
            }
            String name = edits.names().get(layer);
            if (name != null && !name.isBlank()) {
                path.setAttribute(LAYER_NAME, name.strip());
                changed = true;
            }
            if (!edits.order().isEmpty()) {
                int rank = edits.order().indexOf(layer);
                path.setAttribute(LAYER_RANK, Integer.toString(
                        rank >= 0 ? rank : edits.order().size() + traced.indexOf(layer)));
                changed = true;
            }
        }
        if (!changed) {
            return svg;
        }
        try {
            return SvgExportService.serialize(document);
        } catch (Exception exception) {
            throw new IOException("Could not write SVG with changed layers", exception);
        }
    }

    public static String requireHexColor(String color) {
        if (color == null || !color.matches("#[0-9a-f]{6}")) {
            throw new IllegalArgumentException("Layer colors are lowercase #rrggbb values");
        }
        return color;
    }

    /** Whether the path stands for a removed, transparent area. */
    static boolean isCutout(Element path) {
        return isZero(path.getAttribute("opacity")) || isZero(path.getAttribute("fill-opacity"));
    }

    /** Returns the path's fill as {@code #rrggbb}, or {@code null} when the path paints nothing. */
    static String visibleColor(Element path) {
        if (isCutout(path)) {
            return null;
        }
        String fill = path.getAttribute("fill").trim().toLowerCase(Locale.ROOT);
        if (fill.isEmpty()) {
            return "#000000";
        }
        if ("none".equals(fill) || "transparent".equals(fill)) {
            return null;
        }
        Matcher rgb = RGB.matcher(fill);
        if (rgb.matches()) {
            return String.format(Locale.ROOT, "#%02x%02x%02x",
                    Math.min(255, Integer.parseInt(rgb.group(1))),
                    Math.min(255, Integer.parseInt(rgb.group(2))),
                    Math.min(255, Integer.parseInt(rgb.group(3))));
        }
        if (fill.matches("#[0-9a-f]{3}")) {
            return "#" + fill.charAt(1) + fill.charAt(1) + fill.charAt(2) + fill.charAt(2)
                    + fill.charAt(3) + fill.charAt(3);
        }
        return fill;
    }

    private static boolean isZero(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            return Double.parseDouble(value.trim()) <= 0;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private static Document parse(String svg) throws IOException {
        try {
            return SvgExportService.secureBuilder().parse(new InputSource(new StringReader(svg)));
        } catch (Exception exception) {
            throw new IOException("Could not parse icon SVG", exception);
        }
    }
}
