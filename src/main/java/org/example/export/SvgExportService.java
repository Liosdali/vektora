package org.example.export;

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
import java.util.List;
import java.util.Locale;
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
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

public final class SvgExportService {
    private static final String SVG_NAMESPACE = "http://www.w3.org/2000/svg";

    public String renderSvg(IconVariant icon, SvgExportSettings settings) throws IOException {
        if (icon == null || settings == null) {
            throw new IllegalArgumentException("Icon and export settings are required");
        }
        SourceSvg source = parseSource(icon.svg());
        return individualSvg(source, settings);
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
            String baseName = slug(icon.iconName())
                    + (icon.variantName().isBlank() ? "" : "-" + slug(icon.variantName()));
            String uniqueName = uniqueName(baseName, usedNames);
            prepared.add(new PreparedIcon(uniqueName, source, individualSvg(source, settings)));
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

    private static DocumentBuilder secureBuilder() throws Exception {
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

    private static String individualSvg(SourceSvg source, SvgExportSettings settings) throws IOException {
        try {
            Document output = newDocument();
            Element root = output.createElementNS(SVG_NAMESPACE, "svg");
            root.setAttribute("width", Integer.toString(settings.width()));
            root.setAttribute("height", Integer.toString(settings.height()));
            root.setAttribute("viewBox", "0 0 " + settings.width() + " " + settings.height());
            root.setAttribute("preserveAspectRatio", "xMidYMid meet");
            output.appendChild(root);
            appendScaledSource(output, root, source, settings);
            return serialize(output);
        } catch (IOException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IOException("Could not build scaled SVG", exception);
        }
    }

    private static void appendScaledSource(
            Document output, Element parent, SourceSvg source, SvgExportSettings settings) {
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

        NodeList paths = source.document().getElementsByTagNameNS(SVG_NAMESPACE, "path");
        for (int index = 0; index < paths.getLength(); index++) {
            Node imported = output.importNode(paths.item(index), true);
            viewport.appendChild(imported);
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
                appendScaledSource(output, symbol, icon.source(), settings);

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

    private static String serialize(Document document) throws Exception {
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
