package org.example.export;

import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

public final class SvgExportService {
    private static final String SVG_NAMESPACE = "http://www.w3.org/2000/svg";
    private static final String XMLNS_NAMESPACE = "http://www.w3.org/2000/xmlns/";
    private static final String INKSCAPE_NAMESPACE = "http://www.inkscape.org/namespaces/inkscape";
    private static final int[] PNG_FACTORS = {1, 2, 4};
    private static final int[] ICON_SIZES = {16, 32, 48, 64, 128, 180, 192, 256, 512};
    private static final int[] FAVICON_SIZES = {16, 32, 48};
    /** Path attributes that compact output moves to the layer group when every path agrees. */
    private static final List<String> SHARED_ATTRIBUTES = List.of("fill", "stroke", "stroke-width", "fill-rule");
    private static final java.util.regex.Pattern NUMBER =
            java.util.regex.Pattern.compile("-?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][-+]?\\d+)?");

    public String renderSvg(IconVariant icon, SvgExportSettings settings) throws IOException {
        if (icon == null || settings == null) {
            throw new IllegalArgumentException("Icon and export settings are required");
        }
        SourceSvg source = parseSource(icon.svg());
        return individualSvg(source, settings, variantSlug(icon), null);
    }

    public void export(
            List<IconVariant> icons,
            SvgExportSettings settings,
            Path destination,
            ExportFormat format) throws IOException {
        if (icons == null || icons.isEmpty()) {
            throw new IllegalArgumentException("At least one icon is required for export");
        }
        if (settings == null || destination == null || format == null) {
            throw new IllegalArgumentException("Export settings, destination, and format are required");
        }

        List<PreparedIcon> prepared = prepare(icons, settings);
        switch (format) {
            case INDIVIDUAL_SVGS -> writeIndividualSvgs(prepared, destination);
            case SPRITE_SVG -> writeSprite(prepared, settings, destination);
            case ZIP -> writeZip(prepared, settings, destination);
            case LAYER_SVGS -> writeLayerSvgs(prepared, settings, destination);
            case PNG -> writePngs(prepared, destination);
            case ICON_SET -> writeIconSets(prepared, settings, destination);
        }
    }

    private static List<PreparedIcon> prepare(List<IconVariant> icons, SvgExportSettings settings)
            throws IOException {
        Set<String> usedNames = new HashSet<>();
        List<PreparedIcon> prepared = new ArrayList<>(icons.size());
        for (IconVariant icon : icons) {
            if (icon == null) {
                throw new IllegalArgumentException("Icon list must not contain null entries");
            }
            SourceSvg source = parseSource(icon.svg());
            String uniqueName = uniqueName(variantSlug(icon), usedNames);
            prepared.add(new PreparedIcon(uniqueName, source, individualSvg(source, settings, uniqueName, null)));
        }
        return List.copyOf(prepared);
    }

    private static SourceSvg parseSource(String svg) throws IOException {
        try {
            Document document = secureBuilder().parse(new InputSource(new StringReader(svg)));
            Element root = document.getDocumentElement();
            if (root == null || !SVG_NAMESPACE.equals(root.getNamespaceURI())
                    || !"svg".equals(root.getLocalName())) {
                throw new IOException("Icon source must have an SVG root element");
            }
            if (root.getElementsByTagNameNS(SVG_NAMESPACE, "image").getLength() > 0) {
                throw new IOException("Raster images are not allowed in vector SVG exports");
            }
            NodeList paths = root.getElementsByTagNameNS(SVG_NAMESPACE, "path");
            if (paths.getLength() == 0) {
                throw new IOException("Icon source contains no vector paths");
            }

            String[] viewBox = root.getAttribute("viewBox").trim().split("\\s+");
            if (viewBox.length != 4) {
                throw new IOException("Icon SVG must have a four-value viewBox");
            }
            double[] dimensions = new double[4];
            for (int index = 0; index < viewBox.length; index++) {
                dimensions[index] = Double.parseDouble(viewBox[index]);
                if (!Double.isFinite(dimensions[index])) {
                    throw new IOException("Icon SVG viewBox values must be finite");
                }
            }
            if (dimensions[2] <= 0 || dimensions[3] <= 0) {
                throw new IOException("Icon SVG viewBox width and height must be positive");
            }
            return new SourceSvg(document, dimensions[0], dimensions[1], dimensions[2], dimensions[3]);
        } catch (IOException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IOException("Could not parse icon SVG", exception);
        }
    }

    private static String variantSlug(IconVariant icon) {
        return slug(icon.iconName())
                + (icon.variantName().isBlank() ? "" : "-" + slug(icon.variantName()));
    }

    static DocumentBuilder secureBuilder() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        builder.setErrorHandler(new ErrorHandler() {
            @Override
            public void warning(SAXParseException exception) throws SAXException {
                throw exception;
            }

            @Override
            public void error(SAXParseException exception) throws SAXException {
                throw exception;
            }

            @Override
            public void fatalError(SAXParseException exception) throws SAXException {
                throw exception;
            }
        });
        return builder;
    }

    /** The icon on its canvas; with {@code onlyColor}, only that layer, cut as in mosaic mode. */
    private static String individualSvg(SourceSvg source, SvgExportSettings settings, String idPrefix,
                                        String onlyColor) throws IOException {
        try {
            Document output = newDocument();
            Element root = output.createElementNS(SVG_NAMESPACE, "svg");
            root.setAttribute("width", Integer.toString(settings.width()));
            root.setAttribute("height", Integer.toString(settings.height()));
            root.setAttribute("viewBox", "0 0 " + settings.width() + " " + settings.height());
            root.setAttribute("preserveAspectRatio", "xMidYMid meet");
            output.appendChild(root);
            appendScaledSource(output, root, source, settings, idPrefix, onlyColor);
            return serialize(output);
        } catch (IOException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IOException("Could not build scaled SVG", exception);
        }
    }

    private static void appendScaledSource(Document output, Element parent, SourceSvg source,
                                           SvgExportSettings settings, String idPrefix, String onlyColor) {
        Element viewport = output.createElementNS(SVG_NAMESPACE, "svg");
        double scaledWidth = settings.width() * settings.scale();
        double scaledHeight = settings.height() * settings.scale();
        viewport.setAttribute("x", number((settings.width() - scaledWidth) / 2));
        viewport.setAttribute("y", number((settings.height() - scaledHeight) / 2));
        viewport.setAttribute("width", number(scaledWidth));
        viewport.setAttribute("height", number(scaledHeight));
        viewport.setAttribute("viewBox", source.viewBox());
        viewport.setAttribute("preserveAspectRatio", "xMidYMid meet");
        parent.appendChild(viewport);

        // ImageTracer stacks filled outlines and paints removed areas with invisible paths, which
        // hide nothing. Subtract every invisible path from the paths painted before it so removed
        // areas become real holes, then group consecutive same-color paths so editors show layers
        // without changing paint order.
        NodeList sourcePaths = source.document().getElementsByTagNameNS(SVG_NAMESPACE, "path");
        List<Element> paths = new ArrayList<>(sourcePaths.getLength());
        for (int index = 0; index < sourcePaths.getLength(); index++) {
            paths.add((Element) sourcePaths.item(index));
        }
        Map<Element, Area> cutPaths = new IdentityHashMap<>();
        Area removedAfter = new Area();
        for (int index = paths.size() - 1; index >= 0; index--) {
            Element path = paths.get(index);
            Optional<Path2D> shape = SvgPathGeometry.parse(path.getAttribute("d"));
            if (shape.isEmpty()) {
                continue;
            }
            if (SvgLayers.visibleColor(path) != null) {
                if (!removedAfter.isEmpty()
                        && SvgPathGeometry.intersects(shape.get().getBounds2D(), removedAfter.getBounds2D())) {
                    Area remaining = new Area(shape.get());
                    remaining.subtract(removedAfter);
                    cutPaths.put(path, remaining);
                }
            } else if (SvgLayers.isCutout(path)) {
                removedAfter.add(new Area(shape.get()));
            }
        }

        // A chosen layer order repaints the layers in that order; the sort is stable, so paths of
        // one layer keep their own order.
        List<Element> painted = new ArrayList<>();
        for (Element path : paths) {
            if (SvgLayers.visibleColor(path) != null) {
                painted.add(path);
            }
        }
        if (painted.stream().anyMatch(path -> path.hasAttribute(SvgLayers.LAYER_RANK))) {
            painted.sort(java.util.Comparator.comparingInt(SvgExportService::layerRank));
        }
        if (settings.mosaic() || onlyColor != null) {
            cutOutLaterLayers(painted, cutPaths);
        }

        Map<String, Integer> groupCounts = new HashMap<>();
        List<Element> groups = new ArrayList<>();
        Element group = null;
        String groupColor = null;
        for (Element path : painted) {
            String color = SvgLayers.visibleColor(path);
            Area cut = cutPaths.get(path);
            if ((cut != null && cut.isEmpty()) || (onlyColor != null && !onlyColor.equals(color))) {
                continue;
            }
            if (!color.equals(groupColor)) {
                String name = path.getAttribute(SvgLayers.LAYER_NAME);
                String base = slug(name.isBlank() ? color : name);
                int count = groupCounts.merge(base, 1, Integer::sum);
                group = output.createElementNS(SVG_NAMESPACE, "g");
                group.setAttribute("id", idPrefix + "-" + base + (count > 1 ? "-" + count : ""));
                group.setAttribute("data-color", color);
                if (!name.isBlank()) {
                    output.getDocumentElement().setAttributeNS(XMLNS_NAMESPACE, "xmlns:inkscape", INKSCAPE_NAMESPACE);
                    group.setAttributeNS(INKSCAPE_NAMESPACE, "inkscape:label", name);
                    group.setAttributeNS(INKSCAPE_NAMESPACE, "inkscape:groupmode", "layer");
                }
                viewport.appendChild(group);
                groups.add(group);
                groupColor = color;
            }
            Element imported = (Element) output.importNode(path, true);
            imported.removeAttribute(SvgLayers.LAYER_NAME);
            imported.removeAttribute(SvgLayers.LAYER_RANK);
            if (cut != null) {
                imported.setAttribute("d", SvgPathGeometry.format(cut));
                imported.setAttribute("fill-rule", SvgPathGeometry.fillRule(cut));
            }
            group.appendChild(imported);
        }
        if (settings.compact()) {
            groups.forEach(each -> compact(each, settings.decimals()));
        }
    }

    /**
     * Shrinks a layer group without changing how it looks: drops ImageTracer's debug
     * {@code desc} and default opacity, moves attributes every path shares to the group and joins
     * neighboring paths whose bounds stay apart (overlapping subpaths could cancel out when their
     * windings differ, so those stay separate).
     */
    private static void compact(Element group, int decimals) {
        List<Element> paths = new ArrayList<>();
        for (org.w3c.dom.Node node = group.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element && "path".equals(element.getLocalName())) {
                paths.add(element);
            }
        }
        if (paths.isEmpty()) {
            return;
        }
        for (Element path : paths) {
            path.removeAttribute("desc");
            String opacity = path.getAttribute("opacity").trim();
            if (opacity.equals("1") || opacity.equals("1.0")) {
                path.removeAttribute("opacity");
            }
        }
        for (String name : SHARED_ATTRIBUTES) {
            Element first = paths.getFirst();
            if (first.hasAttribute(name) && paths.stream().allMatch(path -> path.hasAttribute(name)
                    && path.getAttribute(name).equals(first.getAttribute(name)))) {
                group.setAttribute(name, first.getAttribute(name));
                paths.forEach(path -> path.removeAttribute(name));
            }
        }

        Element joined = null;
        List<java.awt.geom.Rectangle2D> joinedBounds = new ArrayList<>();
        for (Element path : paths) {
            java.awt.geom.Rectangle2D bounds = SvgPathGeometry.parse(path.getAttribute("d"))
                    .map(Path2D::getBounds2D).orElse(null);
            if (joined != null && bounds != null && sameAttributesBesidesData(joined, path)
                    && joinedBounds.stream().noneMatch(other -> SvgPathGeometry.intersects(bounds, other))) {
                joined.setAttribute("d", joined.getAttribute("d") + " " + path.getAttribute("d"));
                joinedBounds.add(bounds);
                group.removeChild(path);
                continue;
            }
            joined = bounds == null ? null : path;
            joinedBounds = new ArrayList<>();
            if (bounds != null) {
                joinedBounds.add(bounds);
            }
        }
        if (decimals >= 0) {
            for (org.w3c.dom.Node node = group.getFirstChild(); node != null; node = node.getNextSibling()) {
                if (node instanceof Element path && "path".equals(path.getLocalName())) {
                    path.setAttribute("d", rounded(path.getAttribute("d"), decimals));
                }
            }
        }
    }

    private static boolean sameAttributesBesidesData(Element first, Element second) {
        org.w3c.dom.NamedNodeMap attributes = first.getAttributes();
        if (attributes.getLength() != second.getAttributes().getLength()) {
            return false;
        }
        for (int index = 0; index < attributes.getLength(); index++) {
            org.w3c.dom.Node attribute = attributes.item(index);
            if (!"d".equals(attribute.getNodeName())
                    && !attribute.getNodeValue().equals(second.getAttribute(attribute.getNodeName()))) {
                return false;
            }
        }
        return true;
    }

    private static String rounded(String data, int decimals) {
        java.util.regex.Matcher matcher = NUMBER.matcher(data);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            java.math.BigDecimal value = new java.math.BigDecimal(matcher.group())
                    .setScale(decimals, java.math.RoundingMode.HALF_UP).stripTrailingZeros();
            String text = value.signum() == 0 ? "0" : value.toPlainString();
            matcher.appendReplacement(result, text);
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static void writePngs(List<PreparedIcon> icons, Path directory) throws IOException {
        Files.createDirectories(directory);
        for (PreparedIcon icon : icons) {
            for (int factor : PNG_FACTORS) {
                writePng(SvgRasterizer.render(icon.svg(), factor),
                        directory.resolve(icon.name() + (factor == 1 ? "" : "@" + factor + "x") + ".png"));
            }
        }
    }

    /** Square app icons in the common sizes and a favicon.ico holding the three smallest as PNGs. */
    private static void writeIconSets(List<PreparedIcon> icons, SvgExportSettings settings, Path directory)
            throws IOException {
        Files.createDirectories(directory);
        for (PreparedIcon icon : icons) {
            List<byte[]> favicons = new ArrayList<>();
            for (int size : ICON_SIZES) {
                double factor = (double) size / Math.max(settings.width(), settings.height());
                java.awt.image.BufferedImage rendered = SvgRasterizer.render(icon.svg(), factor);
                java.awt.image.BufferedImage square =
                        new java.awt.image.BufferedImage(size, size, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                java.awt.Graphics2D graphics = square.createGraphics();
                try {
                    graphics.drawImage(rendered, (size - rendered.getWidth()) / 2, (size - rendered.getHeight()) / 2, null);
                } finally {
                    graphics.dispose();
                }
                writePng(square, directory.resolve(icon.name() + "-" + size + ".png"));
                if (java.util.Arrays.stream(FAVICON_SIZES).anyMatch(favicon -> favicon == size)) {
                    favicons.add(pngBytes(square));
                }
            }
            writeBytes(directory.resolve(icon.name() + ".ico"), ico(favicons, FAVICON_SIZES));
        }
    }

    /** An ICO file whose images are stored as PNG, which every current browser and Windows read. */
    private static byte[] ico(List<byte[]> images, int[] sizes) {
        int headerSize = 6 + 16 * images.size();
        int total = headerSize + images.stream().mapToInt(image -> image.length).sum();
        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(total).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        buffer.putShort((short) 0).putShort((short) 1).putShort((short) images.size());
        int offset = headerSize;
        for (int index = 0; index < images.size(); index++) {
            int size = sizes[index];
            buffer.put((byte) (size >= 256 ? 0 : size)).put((byte) (size >= 256 ? 0 : size))
                    .put((byte) 0).put((byte) 0).putShort((short) 1).putShort((short) 32)
                    .putInt(images.get(index).length).putInt(offset);
            offset += images.get(index).length;
        }
        images.forEach(buffer::put);
        return buffer.array();
    }

    private static void writePng(java.awt.image.BufferedImage image, Path destination) throws IOException {
        writeBytes(destination, pngBytes(image));
    }

    private static byte[] pngBytes(java.awt.image.BufferedImage image) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        if (!javax.imageio.ImageIO.write(image, "png", bytes)) {
            throw new IOException("Could not write PNG");
        }
        return bytes.toByteArray();
    }

    private static int layerRank(Element path) {
        try {
            return Integer.parseInt(path.getAttribute(SvgLayers.LAYER_RANK));
        } catch (NumberFormatException exception) {
            return Integer.MAX_VALUE;
        }
    }

    /** Mosaic: subtracts from every path whatever is painted after it, so no two shapes overlap. */
    private static void cutOutLaterLayers(List<Element> painted, Map<Element, Area> cutPaths) {
        Area paintedAfter = new Area();
        for (int index = painted.size() - 1; index >= 0; index--) {
            Element path = painted.get(index);
            Area area = cutPaths.get(path);
            if (area == null) {
                Optional<Path2D> shape = SvgPathGeometry.parse(path.getAttribute("d"));
                if (shape.isEmpty()) {
                    continue;
                }
                area = new Area(shape.get());
            }
            if (!paintedAfter.isEmpty()
                    && SvgPathGeometry.intersects(area.getBounds2D(), paintedAfter.getBounds2D())) {
                Area visible = new Area(area);
                visible.subtract(paintedAfter);
                cutPaths.put(path, visible);
            }
            paintedAfter.add(area);
        }
    }

    /** Writes each layer of each icon to its own file, named after the layer. */
    private static void writeLayerSvgs(List<PreparedIcon> icons, SvgExportSettings settings, Path directory)
            throws IOException {
        Files.createDirectories(directory);
        Set<String> usedNames = new HashSet<>();
        for (PreparedIcon icon : icons) {
            Map<String, String> layers = new java.util.LinkedHashMap<>();
            NodeList groups;
            try {
                groups = secureBuilder().parse(new InputSource(new StringReader(icon.svg())))
                        .getElementsByTagNameNS(SVG_NAMESPACE, "g");
            } catch (Exception exception) {
                throw new IOException("Could not read the exported layers", exception);
            }
            for (int index = 0; index < groups.getLength(); index++) {
                Element group = (Element) groups.item(index);
                layers.putIfAbsent(group.getAttribute("data-color"),
                        group.getAttributeNS(INKSCAPE_NAMESPACE, "label"));
            }
            for (Map.Entry<String, String> layer : layers.entrySet()) {
                String label = layer.getValue().isBlank() ? layer.getKey() : layer.getValue();
                String name = uniqueName(icon.name() + "-" + slug(label), usedNames);
                Files.writeString(directory.resolve(name + ".svg"),
                        individualSvg(icon.source(), settings, icon.name(), layer.getKey()), StandardCharsets.UTF_8);
            }
        }
    }

    private static void writeIndividualSvgs(List<PreparedIcon> icons, Path directory) throws IOException {
        Files.createDirectories(directory);
        for (PreparedIcon icon : icons) {
            Files.writeString(directory.resolve(icon.name() + ".svg"), icon.svg(), StandardCharsets.UTF_8);
        }
    }

    private static void writeSprite(
            List<PreparedIcon> icons, SvgExportSettings settings, Path destination) throws IOException {
        writeString(destination, spriteSvg(icons, settings));
    }

    private static String spriteSvg(List<PreparedIcon> icons, SvgExportSettings settings) throws IOException {
        try {
            Document output = newDocument();
            Element root = output.createElementNS(SVG_NAMESPACE, "svg");
            long columns = (long) Math.ceil(Math.sqrt(icons.size()));
            long rows = (icons.size() + columns - 1) / columns;
            long spriteWidth = settings.width() * columns;
            long spriteHeight = settings.height() * rows;
            root.setAttribute("width", Long.toString(spriteWidth));
            root.setAttribute("height", Long.toString(spriteHeight));
            root.setAttribute("viewBox", "0 0 " + spriteWidth + " " + spriteHeight);
            output.appendChild(root);

            Element definitions = output.createElementNS(SVG_NAMESPACE, "defs");
            root.appendChild(definitions);
            for (int index = 0; index < icons.size(); index++) {
                PreparedIcon icon = icons.get(index);
                Element symbol = output.createElementNS(SVG_NAMESPACE, "symbol");
                symbol.setAttribute("id", icon.name());
                symbol.setAttribute("viewBox", "0 0 " + settings.width() + " " + settings.height());
                definitions.appendChild(symbol);
                appendScaledSource(output, symbol, icon.source(), settings, icon.name(), null);

                long column = index % columns;
                long row = index / columns;
                Element use = output.createElementNS(SVG_NAMESPACE, "use");
                use.setAttribute("href", "#" + icon.name());
                use.setAttribute("x", Long.toString(column * settings.width()));
                use.setAttribute("y", Long.toString(row * settings.height()));
                use.setAttribute("width", Integer.toString(settings.width()));
                use.setAttribute("height", Integer.toString(settings.height()));
                root.appendChild(use);
            }
            return serialize(output);
        } catch (IOException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IOException("Could not build SVG sprite", exception);
        }
    }

    private static void writeZip(
            List<PreparedIcon> icons, SvgExportSettings settings, Path destination) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            for (PreparedIcon icon : icons) {
                addEntry(zip, icon.name() + ".svg", icon.svg());
            }
            addEntry(zip, "sprite.svg", spriteSvg(icons, settings));
        }
        writeBytes(destination, bytes.toByteArray());
    }

    private static void addEntry(ZipOutputStream zip, String name, String value) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(value.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static Document newDocument() throws Exception {
        return secureBuilder().newDocument();
    }

    static String serialize(Document document) throws Exception {
        TransformerFactory factory = TransformerFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
        var transformer = factory.newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, StandardCharsets.UTF_8.name());
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(document), new StreamResult(writer));
        return writer.toString();
    }

    private static void writeString(Path destination, String value) throws IOException {
        writeBytes(destination, value.getBytes(StandardCharsets.UTF_8));
    }

    private static void writeBytes(Path destination, byte[] value) throws IOException {
        Path parent = destination.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.write(destination, value);
    }

    private static String slug(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
        return normalized.isEmpty() ? "icon" : normalized;
    }

    private static String uniqueName(String baseName, Set<String> usedNames) {
        String name = baseName;
        int suffix = 2;
        while (!usedNames.add(name)) {
            name = baseName + "-" + suffix++;
        }
        return name;
    }

    private static String number(double value) {
        if (value == Math.rint(value)) {
            return Long.toString((long) value);
        }
        return Double.toString(value);
    }

    private record SourceSvg(Document document, double x, double y, double width, double height) {
        private String viewBox() {
            return number(x) + " " + number(y) + " " + number(width) + " " + number(height);
        }
    }

    private record PreparedIcon(String name, SourceSvg source, String svg) {
    }
}
