package org.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipInputStream;
import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilderFactory;
import org.example.export.ExportFormat;
import org.example.export.SvgExportService;
import org.example.export.SvgExportSettings;
import org.example.model.IconPack;
import org.example.ui.IconPackController;
import org.example.vector.ImageTracerVectorizer;
import org.example.vector.PixelCoordinate;
import org.example.vector.TraceOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

class IconPackIntegrationTest {
    private static final String SVG_NAMESPACE = "http://www.w3.org/2000/svg";

    @Test
    void importsPngsCreatesPaletteVariantsAndExportsValidVectorZip(@TempDir Path directory)
            throws Exception {
        Path first = png(directory.resolve("moon.png"), Color.ORANGE);
        Path second = png(directory.resolve("star.png"), Color.RED);
        IconPackController controller = new IconPackController(
                new IconPack(), new ImageTracerVectorizer(), new SvgExportService());
        controller.addImages(List.of(first, second));
        controller.setPalettes(List.of(List.of(Color.BLUE), List.of(Color.GREEN)));
        controller.setTraceOptions(new TraceOptions(
                TraceOptions.ColorMode.CUSTOM_PALETTE, 1, List.of(Color.BLUE)));
        controller.setExportSettings(new SvgExportSettings(32, 24, 0.8));
        Path archive = directory.resolve("icon-pack.zip");

        controller.export(archive, ExportFormat.ZIP);

        int svgCount = 0;
        int symbolCount = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(Files.readAllBytes(archive)))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                String content = new String(zip.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                Document document = parse(content);
                Element root = document.getDocumentElement();
                assertTrue(document.getElementsByTagNameNS(
                        "http://www.w3.org/2000/svg", "image").getLength() == 0);
                if ("sprite.svg".equals(entry.getName())) {
                    assertEquals("64", root.getAttribute("width"));
                    assertEquals("48", root.getAttribute("height"));
                    symbolCount = document.getElementsByTagNameNS(
                            "http://www.w3.org/2000/svg", "symbol").getLength();
                    assertEquals(4, document.getElementsByTagNameNS(
                            "http://www.w3.org/2000/svg", "use").getLength());
                } else {
                    assertEquals("32", root.getAttribute("width"));
                    assertEquals("24", root.getAttribute("height"));
                    var paths = document.getElementsByTagNameNS(
                            "http://www.w3.org/2000/svg", "path");
                    assertTrue(paths.getLength() > 0);
                    // Transparent pixels stay unpainted: only the palette color is drawn.
                    assertTrue(Set.of("rgb(0,0,255)", "rgb(0,255,0)")
                            .containsAll(visiblePathFills(document)));
                    svgCount++;
                }
                zip.closeEntry();
            }
        }
        assertEquals(4, svgCount);
        assertEquals(4, symbolCount);
    }

    @Test
    void appliesPerIconBackgroundSelectionsToPreviewAndEveryExport(@TempDir Path directory)
            throws Exception {
        Path first = badgePng(directory.resolve("first.png"));
        Path second = badgePng(directory.resolve("second.png"));
        IconPackController controller = new IconPackController(
                new IconPack(), new ImageTracerVectorizer(), new SvgExportService());
        controller.addImages(List.of(first, second));
        controller.setPalettes(List.of(List.of(Color.BLUE)));
        controller.setTraceOptions(new TraceOptions(
                TraceOptions.ColorMode.CUSTOM_PALETTE, 1, List.of(Color.BLUE)));
        var icons = controller.pack().icons();
        controller.addBackgroundRemovalSeed(icons.get(0), new PixelCoordinate(5, 5));

        String firstPreview = controller.preview(icons.get(0)).getFirst().svg();
        String secondPreview = controller.preview(icons.get(1)).getFirst().svg();
        Set<String> firstGeometry = pathGeometry(parse(firstPreview));
        Set<String> secondGeometry = pathGeometry(parse(secondPreview));
        assertNotEquals(firstGeometry, secondGeometry);
        assertEquals(Set.of("rgb(0,0,255)"), visiblePathFills(parse(firstPreview)));
        // The selected interior is cut out of the blue silhouette instead of being painted over.
        assertTrue(firstGeometry.stream().anyMatch(data -> data.chars().filter(c -> c == 'M').count() > 1));

        Path individualDirectory = directory.resolve("individual");
        controller.export(individualDirectory, ExportFormat.INDIVIDUAL_SVGS);
        String individualFirst = Files.readString(
                individualDirectory.resolve("first-palette-1.svg"));
        String individualSecond = Files.readString(
                individualDirectory.resolve("second-palette-1.svg"));
        assertEquals(firstGeometry, pathGeometry(parse(individualFirst)));
        assertEquals(secondGeometry, pathGeometry(parse(individualSecond)));

        Path spritePath = directory.resolve("sprite.svg");
        controller.export(spritePath, ExportFormat.SPRITE_SVG);
        Document sprite = parse(Files.readString(spritePath));
        assertEquals(firstGeometry, pathGeometry(symbol(sprite, "first-palette-1")));
        assertEquals(secondGeometry, pathGeometry(symbol(sprite, "second-palette-1")));

        Path archive = directory.resolve("selected-backgrounds.zip");
        controller.export(archive, ExportFormat.ZIP);
        Map<String, String> entries = zipEntries(archive);
        assertEquals(firstGeometry, pathGeometry(parse(entries.get("first-palette-1.svg"))));
        assertEquals(secondGeometry, pathGeometry(parse(entries.get("second-palette-1.svg"))));
        Document archivedSprite = parse(entries.get("sprite.svg"));
        assertEquals(firstGeometry, pathGeometry(symbol(archivedSprite, "first-palette-1")));
        assertEquals(secondGeometry, pathGeometry(symbol(archivedSprite, "second-palette-1")));
    }

    @Test
    void exportUsesTheBackgroundSelectionSnapshotCapturedBeforeEdits(@TempDir Path directory)
            throws Exception {
        Path source = badgePng(directory.resolve("snapshot.png"));
        IconPackController controller = new IconPackController(
                new IconPack(), new ImageTracerVectorizer(), new SvgExportService());
        controller.addImages(List.of(source));
        controller.setPalettes(List.of(List.of(Color.BLUE)));
        controller.setTraceOptions(new TraceOptions(
                TraceOptions.ColorMode.CUSTOM_PALETTE, 1, List.of(Color.BLUE)));
        var icon = controller.pack().icons().getFirst();
        controller.addBackgroundRemovalSeed(icon, new PixelCoordinate(5, 5));
        Set<String> selectedGeometry = pathGeometry(parse(controller.preview(icon).getFirst().svg()));
        var selectionSnapshot = controller.backgroundRemovalSnapshot();

        controller.clearBackgroundRemovalSeeds(icon);
        Set<String> currentGeometry = pathGeometry(parse(controller.preview(icon).getFirst().svg()));
        assertNotEquals(selectedGeometry, currentGeometry);

        Path individualDirectory = directory.resolve("snapshot-export");
        controller.export(individualDirectory, ExportFormat.INDIVIDUAL_SVGS, selectionSnapshot);

        assertEquals(selectedGeometry, pathGeometry(parse(Files.readString(
                individualDirectory.resolve("snapshot-palette-1.svg")))));
    }

    private static Path badgePng(Path path) throws Exception {
        BufferedImage image = new BufferedImage(12, 12, BufferedImage.TYPE_INT_ARGB);
        fill(image, 2, 2, 8, 8, Color.RED);
        fill(image, 4, 4, 4, 4, Color.GREEN);
        image.setRGB(3, 3, Color.GREEN.getRGB());
        image.setRGB(8, 8, Color.CYAN.getRGB());
        ImageIO.write(image, "png", path.toFile());
        return path;
    }

    private static void fill(BufferedImage image, int x, int y, int width, int height, Color color) {
        for (int row = y; row < y + height; row++) {
            for (int column = x; column < x + width; column++) {
                image.setRGB(column, row, color.getRGB());
            }
        }
    }

    private static Element symbol(Document document, String id) {
        NodeList symbols = document.getElementsByTagNameNS(SVG_NAMESPACE, "symbol");
        for (int index = 0; index < symbols.getLength(); index++) {
            Element symbol = (Element) symbols.item(index);
            if (id.equals(symbol.getAttribute("id"))) {
                return symbol;
            }
        }
        throw new AssertionError("Missing SVG symbol: " + id);
    }

    private static Set<String> pathGeometry(Element root) {
        NodeList paths = root.getElementsByTagNameNS(SVG_NAMESPACE, "path");
        Set<String> geometry = new HashSet<>();
        for (int index = 0; index < paths.getLength(); index++) {
            Element path = (Element) paths.item(index);
            geometry.add(path.getAttribute("d"));
        }
        return geometry;
    }

    private static Set<String> pathGeometry(Document document) {
        return pathGeometry(document.getDocumentElement());
    }

    private static Set<String> visiblePathFills(Document document) {
        NodeList paths = document.getElementsByTagNameNS(SVG_NAMESPACE, "path");
        Set<String> fills = new HashSet<>();
        for (int index = 0; index < paths.getLength(); index++) {
            Element path = (Element) paths.item(index);
            if (!"0.0".equals(path.getAttribute("opacity"))) {
                fills.add(path.getAttribute("fill").toLowerCase());
            }
        }
        return fills;
    }

    private static Map<String, String> zipEntries(Path archive) throws Exception {
        Map<String, String> entries = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(
                new ByteArrayInputStream(Files.readAllBytes(archive)), StandardCharsets.UTF_8)) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                entries.put(entry.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return entries;
    }

    private static Path png(Path path, Color color) throws Exception {
        BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        for (int y = 1; y < 7; y++) {
            for (int x = 1; x < 7; x++) {
                image.setRGB(x, y, color.getRGB());
            }
        }
        ImageIO.write(image, "png", path.toFile());
        return path;
    }

    private static Document parse(String svg) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(svg)));
    }
}
