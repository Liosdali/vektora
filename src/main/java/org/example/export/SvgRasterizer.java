package org.example.export;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.StringReader;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

/**
 * Paints the SVGs this exporter writes onto a transparent image with Java2D: nested viewports
 * with {@code xMidYMid meet}, groups and filled, optionally stroked M/L/Q/C/Z paths. It is not a
 * general SVG renderer.
 */
public final class SvgRasterizer {
    private static final String SVG_NAMESPACE = "http://www.w3.org/2000/svg";
    private static final Pattern RGB = Pattern.compile(
            "rgba?\\(\\s*(\\d{1,3})\\s*,\\s*(\\d{1,3})\\s*,\\s*(\\d{1,3})\\s*(?:,[^)]*)?\\)");

    private SvgRasterizer() {
    }

    /** Renders the SVG at {@code factor} times its width and height. */
    public static BufferedImage render(String svg, double factor) throws IOException {
        Document document;
        try {
            document = SvgExportService.secureBuilder().parse(new InputSource(new StringReader(svg)));
        } catch (Exception exception) {
            throw new IOException("Could not parse SVG for rasterizing", exception);
        }
        Element root = document.getDocumentElement();
        double width = length(root, "width");
        double height = length(root, "height");
        BufferedImage image = new BufferedImage(Math.max(1, (int) Math.round(width * factor)),
                Math.max(1, (int) Math.round(height * factor)), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.scale(factor, factor);
            graphics.transform(viewBoxTransform(root, 0, 0, width, height));
            paintChildren(root, graphics);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static void paintChildren(Element parent, Graphics2D graphics) {
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (!(node instanceof Element element) || !SVG_NAMESPACE.equals(element.getNamespaceURI())) {
                continue;
            }
            switch (element.getLocalName()) {
                case "svg" -> {
                    Graphics2D nested = (Graphics2D) graphics.create();
                    try {
                        nested.transform(viewBoxTransform(element, number(element, "x"), number(element, "y"),
                                length(element, "width"), length(element, "height")));
                        paintChildren(element, nested);
                    } finally {
                        nested.dispose();
                    }
                }
                case "g" -> paintChildren(element, graphics);
                case "path" -> paintPath(element, graphics);
                default -> {
                    // defs, symbols and anything else are not drawn directly.
                }
            }
        }
    }

    private static void paintPath(Element path, Graphics2D graphics) {
        Optional<Path2D> shape = SvgPathGeometry.parse(path.getAttribute("d"));
        if (shape.isEmpty()) {
            return;
        }
        Path2D outline = shape.get();
        if ("evenodd".equals(inherited(path, "fill-rule"))) {
            outline.setWindingRule(Path2D.WIND_EVEN_ODD);
        }
        float opacity = (float) (opacity(path, "opacity") * opacity(path, "fill-opacity"));
        if (opacity <= 0) {
            return;
        }
        Graphics2D painter = (Graphics2D) graphics.create();
        try {
            painter.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.min(1, opacity)));
            Color fill = color(inherited(path, "fill"), Color.BLACK);
            if (fill != null) {
                painter.setColor(fill);
                painter.fill(outline);
            }
            Color stroke = color(inherited(path, "stroke"), null);
            String strokeWidth = inherited(path, "stroke-width");
            double width = strokeWidth == null ? 1 : Double.parseDouble(strokeWidth);
            if (stroke != null && width > 0) {
                painter.setColor(stroke);
                painter.setStroke(new BasicStroke((float) width));
                painter.draw(outline);
            }
        } finally {
            painter.dispose();
        }
    }

    /** Maps the element's viewBox into the given box, centered and scaled to fit. */
    private static AffineTransform viewBoxTransform(Element element, double x, double y, double width, double height) {
        String[] viewBox = element.getAttribute("viewBox").trim().split("[\\s,]+");
        if (viewBox.length != 4) {
            return AffineTransform.getTranslateInstance(x, y);
        }
        double boxX = Double.parseDouble(viewBox[0]);
        double boxY = Double.parseDouble(viewBox[1]);
        double boxWidth = Double.parseDouble(viewBox[2]);
        double boxHeight = Double.parseDouble(viewBox[3]);
        double scale = Math.min(width / boxWidth, height / boxHeight);
        AffineTransform transform = AffineTransform.getTranslateInstance(
                x + (width - boxWidth * scale) / 2, y + (height - boxHeight * scale) / 2);
        transform.scale(scale, scale);
        transform.translate(-boxX, -boxY);
        return transform;
    }

    private static String inherited(Element element, String name) {
        for (Node node = element; node instanceof Element current; node = node.getParentNode()) {
            if (current.hasAttribute(name)) {
                return current.getAttribute(name).trim();
            }
        }
        return null;
    }

    /** Opacity multiplies down the tree, as group opacity does. */
    private static double opacity(Element element, String name) {
        double value = 1;
        for (Node node = element; node instanceof Element current; node = node.getParentNode()) {
            if (current.hasAttribute(name)) {
                try {
                    value *= Math.max(0, Math.min(1, Double.parseDouble(current.getAttribute(name).trim())));
                } catch (NumberFormatException ignored) {
                    // An unreadable opacity paints fully, as browsers do.
                }
            }
        }
        return value;
    }

    private static Color color(String value, Color fallback) {
        if (value == null) {
            return fallback;
        }
        String color = value.toLowerCase(Locale.ROOT);
        if (color.isEmpty() || "none".equals(color) || "transparent".equals(color)) {
            return null;
        }
        Matcher rgb = RGB.matcher(color);
        if (rgb.matches()) {
            return new Color(Math.min(255, Integer.parseInt(rgb.group(1))),
                    Math.min(255, Integer.parseInt(rgb.group(2))), Math.min(255, Integer.parseInt(rgb.group(3))));
        }
        if (color.matches("#[0-9a-f]{6}")) {
            return new Color(Integer.parseInt(color.substring(1), 16));
        }
        if (color.matches("#[0-9a-f]{3}")) {
            return new Color(Integer.parseInt("" + color.charAt(1) + color.charAt(1) + color.charAt(2)
                    + color.charAt(2) + color.charAt(3) + color.charAt(3), 16));
        }
        return fallback;
    }

    private static double length(Element element, String name) {
        double value = number(element, name);
        if (value <= 0) {
            throw new IllegalArgumentException("SVG " + name + " must be positive to rasterize");
        }
        return value;
    }

    private static double number(Element element, String name) {
        String value = element.getAttribute(name).trim().replaceAll("px$", "");
        return value.isEmpty() ? 0 : Double.parseDouble(value);
    }
}
