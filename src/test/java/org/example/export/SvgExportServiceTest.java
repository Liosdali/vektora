package org.example.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipInputStream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import java.io.StringReader;

class SvgExportServiceTest {
    private static final String SOURCE_SVG = """
            <svg xmlns="http://www.w3.org/2000/svg" width="10" height="5" viewBox="2 3 10 5">
              <path d="M 2 3 L 12 8 Z" fill="rgb(255,0,0)"/>
            </svg>
            """;
    private final SvgExportService service = new SvgExportService();

    @Test
    void exportsSvgOnRequestedCanvasWithCenteredScale(@TempDir Path directory) throws Exception {
        Path outputDirectory = directory.resolve("icons");

        service.export(
                List.of(new IconVariant("Arrow", "Blue", SOURCE_SVG)),
                new SvgExportSettings(64, 32, 0.5),
                outputDirectory,
                ExportFormat.INDIVIDUAL_SVGS);

        Document document = parse(Files.readString(outputDirectory.resolve("arrow-blue.svg")));
        Element root = document.getDocumentElement();
        assertEquals("64", root.getAttribute("width"));
        assertEquals("32", root.getAttribute("height"));
        assertEquals("0 0 64 32", root.getAttribute("viewBox"));
        assertEquals("16 8 32 16", ((Element) root.getElementsByTagName("svg").item(0))
                .getAttribute("x") + " "
                + ((Element) root.getElementsByTagName("svg").item(0)).getAttribute("y") + " "
                + ((Element) root.getElementsByTagName("svg").item(0)).getAttribute("width") + " "
                + ((Element) root.getElementsByTagName("svg").item(0)).getAttribute("height"));
        assertEquals("2 3 10 5", ((Element) root.getElementsByTagName("svg").item(0))
                .getAttribute("viewBox"));
        assertEquals(1, root.getElementsByTagName("path").getLength());
        assertEquals(0, root.getElementsByTagName("image").getLength());
    }

    @Test
    void spriteAndZipContainEveryNamedVariant(@TempDir Path directory) throws Exception {
        List<IconVariant> icons = List.of(
                new IconVariant("Arrow", "Blue", SOURCE_SVG),
                new IconVariant("Arrow", "Green", SOURCE_SVG));
        SvgExportSettings settings = new SvgExportSettings(24, 24, 1);

        Path sprite = directory.resolve("icons.svg");
        service.export(icons, settings, sprite, ExportFormat.SPRITE_SVG);
        Document spriteDocument = parse(Files.readString(sprite));
        Element spriteRoot = spriteDocument.getDocumentElement();
        assertEquals("48", spriteRoot.getAttribute("width"));
        assertEquals("24", spriteRoot.getAttribute("height"));
        assertEquals("0 0 48 24", spriteRoot.getAttribute("viewBox"));
        NodeList symbols = spriteDocument.getElementsByTagName("symbol");
        assertEquals(2, symbols.getLength());
        assertEquals("arrow-blue", ((Element) symbols.item(0)).getAttribute("id"));
        assertEquals("arrow-green", ((Element) symbols.item(1)).getAttribute("id"));
        NodeList uses = spriteDocument.getElementsByTagName("use");
        assertEquals(2, uses.getLength());
        assertEquals("#arrow-blue", ((Element) uses.item(0)).getAttribute("href"));
        assertEquals("#arrow-green", ((Element) uses.item(1)).getAttribute("href"));
        assertEquals("0", ((Element) uses.item(0)).getAttribute("x"));
        assertEquals("24", ((Element) uses.item(1)).getAttribute("x"));
        assertEquals(2, spriteDocument.getElementsByTagName("path").getLength());

        Path zip = directory.resolve("icons.zip");
        service.export(icons, settings, zip, ExportFormat.ZIP);
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(Files.readAllBytes(zip)))) {
            Set<String> entries = new java.util.HashSet<>();
            for (var entry = input.getNextEntry(); entry != null; entry = input.getNextEntry()) {
                entries.add(entry.getName());
            }
            assertEquals(Set.of("arrow-blue.svg", "arrow-green.svg", "sprite.svg"), entries);
        }
    }

    @Test
    void groupsPathsIntoOneLayerPerFillColorAndDropsInvisiblePaths() throws Exception {
        String layered = """
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 10 10">
                  <path fill="rgb(0,0,0)" opacity="0.0" d="M 0 0 L 10 0 Z"/>
                  <path fill="rgb(30,42,68)" opacity="1.0" d="M 1 1 L 2 2 Z"/>
                  <path fill="rgb(30,42,68)" opacity="1.0" d="M 3 3 L 4 4 Z"/>
                  <path fill="rgb(201,206,214)" opacity="1.0" d="M 5 5 L 6 6 Z"/>
                </svg>
                """;

        Element root = parse(service.renderSvg(new IconVariant("Badge", "default", layered),
                new SvgExportSettings(16, 16, 1))).getDocumentElement();

        NodeList groups = root.getElementsByTagName("g");
        assertEquals(2, groups.getLength());
        Element navy = (Element) groups.item(0);
        assertEquals("badge-default-1e2a44", navy.getAttribute("id"));
        assertEquals("#1e2a44", navy.getAttribute("data-color"));
        assertEquals(2, navy.getElementsByTagName("path").getLength());
        assertEquals(1, ((Element) groups.item(1)).getElementsByTagName("path").getLength());
        assertEquals(3, root.getElementsByTagName("path").getLength());
    }

    @Test
    void turnsInvisiblePathsIntoHolesInEverythingPaintedBeforeThem() throws Exception {
        String stacked = """
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 10 10">
                  <path fill="rgb(0,0,255)" opacity="1.0" d="M 0 0 L 10 0 L 10 10 L 0 10 Z"/>
                  <path fill="rgb(0,0,0)" opacity="0.0" d="M 2 2 L 8 2 L 8 8 L 2 8 Z"/>
                  <path fill="rgb(255,0,0)" opacity="1.0" d="M 4 4 L 6 4 L 6 6 L 4 6 Z"/>
                </svg>
                """;

        Element root = parse(service.renderSvg(new IconVariant("Badge", "", stacked),
                new SvgExportSettings(16, 16, 1))).getDocumentElement();

        assertEquals(0, root.getElementsByTagName("mask").getLength());
        NodeList groups = root.getElementsByTagName("g");
        assertEquals(2, groups.getLength());
        Element blue = (Element) ((Element) groups.item(0)).getElementsByTagName("path").item(0);
        assertEquals("#0000ff", ((Element) groups.item(0)).getAttribute("data-color"));
        assertEquals(2, blue.getAttribute("d").chars().filter(character -> character == 'M').count());
        assertTrue(blue.getAttribute("d").contains("8 8"));
        Element red = (Element) ((Element) groups.item(1)).getElementsByTagName("path").item(0);
        assertEquals("#ff0000", ((Element) groups.item(1)).getAttribute("data-color"));
        assertEquals("M 4 4 L 6 4 L 6 6 L 4 6 Z", red.getAttribute("d"));
        assertEquals(2, root.getElementsByTagName("path").getLength());
    }

    @Test
    void keepsPaintOrderWhenAColorReturnsAboveAnotherColor() throws Exception {
        String interleaved = """
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 10 10">
                  <path fill="rgb(0,0,255)" d="M 0 0 L 10 0 L 10 10 Z"/>
                  <path fill="rgb(255,0,0)" d="M 1 1 L 9 1 L 9 9 Z"/>
                  <path fill="rgb(0,0,255)" d="M 2 2 L 8 2 L 8 8 Z"/>
                </svg>
                """;

        Element root = parse(service.renderSvg(new IconVariant("Mark", "", interleaved),
                new SvgExportSettings(16, 16, 1))).getDocumentElement();

        NodeList groups = root.getElementsByTagName("g");
        assertEquals(3, groups.getLength());
        assertEquals("mark-0000ff", ((Element) groups.item(0)).getAttribute("id"));
        assertEquals("mark-ff0000", ((Element) groups.item(1)).getAttribute("id"));
        assertEquals("mark-0000ff-2", ((Element) groups.item(2)).getAttribute("id"));
    }

    @Test
    void duplicateVariantNamesReceiveStableUniqueFileNames(@TempDir Path directory) throws Exception {
        service.export(
                List.of(
                        new IconVariant("home", "default", SOURCE_SVG),
                        new IconVariant("home", "default", SOURCE_SVG)),
                new SvgExportSettings(16, 16, 1),
                directory,
                ExportFormat.INDIVIDUAL_SVGS);

        assertTrue(Files.exists(directory.resolve("home-default.svg")));
        assertTrue(Files.exists(directory.resolve("home-default-2.svg")));
    }

    @Test
    void rejectsNonPositiveOrNonFiniteDimensionsAndScale() {
        assertThrows(IllegalArgumentException.class, () -> new SvgExportSettings(0, 16, 1));
        assertThrows(IllegalArgumentException.class, () -> new SvgExportSettings(16, -1, 1));
        assertThrows(IllegalArgumentException.class, () -> new SvgExportSettings(16, 16, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new SvgExportSettings(16, 16, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new SvgExportSettings(16, 16, Double.MAX_VALUE));
    }

    @Test
    void reportsMalformedSvgInsteadOfWritingBrokenExports(@TempDir Path directory) {
        assertThrows(IOException.class, () -> service.export(
                List.of(new IconVariant("bad", "", "<svg><path></svg>")),
                new SvgExportSettings(16, 16, 1),
                directory,
                ExportFormat.INDIVIDUAL_SVGS));
    }

    private static Document parse(String svg) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(svg)));
    }
}
