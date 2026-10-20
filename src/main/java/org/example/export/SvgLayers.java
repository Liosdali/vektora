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
        Objects.requireNonNull(colors, "Removed colors are required");
        if (colors.isEmpty()) {
            return svg;
        }
        Document document = parse(svg);
        NodeList paths = document.getElementsByTagNameNS(SVG_NAMESPACE, "path");
        boolean changed = false;
        for (int index = 0; index < paths.getLength(); index++) {
            Element path = (Element) paths.item(index);
            String color = visibleColor(path);
            if (color != null && colors.contains(color)) {
                path.setAttribute("opacity", "0.0");
                changed = true;
            }
        }
        if (!changed) {
            return svg;
        }
        try {
            return SvgExportService.serialize(document);
        } catch (Exception exception) {
            throw new IOException("Could not write SVG without removed layers", exception);
        }
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
