package org.example.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.geom.Path2D;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

class LayerEditsTest {
    private static final String INKSCAPE = "http://www.inkscape.org/namespaces/inkscape";
    private static final String STACKED = """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 10 10">
              <path fill="rgb(0,0,255)" d="M 0 0 L 10 0 L 10 10 L 0 10 Z"/>
              <path fill="rgb(255,0,0)" d="M 4 4 L 6 4 L 6 6 L 4 6 Z"/>
            </svg>
            """;
    private static final String INTERLEAVED = """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 10 10">
              <path fill="rgb(0,0,255)" d="M 0 0 L 10 0 L 10 10 Z"/>
              <path fill="rgb(255,0,0)" d="M 1 1 L 9 1 L 9 9 Z"/>
              <path fill="rgb(0,0,255)" d="M 2 2 L 8 2 L 8 8 Z"/>
            </svg>
            """;
    private final SvgExportService service = new SvgExportService();

    @Test
    void namedLayersBecomeLabelledInkscapeLayers() throws Exception {
        String edited = SvgLayers.withLayerEdits(STACKED,
                new LayerEdits(Set.of(), Map.of(), Map.of("#0000ff", "Kuş"), List.of()));

        Element root = render(edited, new SvgExportSettings(16, 16, 1));

        Element bird = (Element) root.getElementsByTagName("g").item(0);
        assertEquals("badge-kus", bird.getAttribute("id"));
        assertEquals("Kuş", bird.getAttributeNS(INKSCAPE, "label"));
        assertEquals("layer", bird.getAttributeNS(INKSCAPE, "groupmode"));
        assertFalse(((Element) root.getElementsByTagName("g").item(1)).hasAttributeNS(INKSCAPE, "label"));
        assertFalse(((Element) root.getElementsByTagName("path").item(0)).hasAttribute("data-layer-name"));
    }

    @Test
    void aChosenLayerOrderRepaintsEachColorAsOneGroup() throws Exception {
        String edited = SvgLayers.withLayerEdits(INTERLEAVED,
                new LayerEdits(Set.of(), Map.of(), Map.of(), List.of("#ff0000", "#0000ff")));

        NodeList groups = render(edited, new SvgExportSettings(16, 16, 1)).getElementsByTagName("g");

        assertEquals(2, groups.getLength());
        assertEquals("#ff0000", ((Element) groups.item(0)).getAttribute("data-color"));
        assertEquals(2, ((Element) groups.item(1)).getElementsByTagName("path").getLength());
    }

    @Test
    void aLayerRecoloredToAnotherLayersColorJoinsThatLayer() throws Exception {
        String edited = SvgLayers.withLayerEdits(INTERLEAVED, new LayerEdits(Set.of(), Map.of("#ff0000", "#0000ff"),
                Map.of("#0000ff", "Zemin"), List.of("#0000ff", "#ff0000")));

        NodeList groups = render(edited, new SvgExportSettings(16, 16, 1)).getElementsByTagName("g");

        assertEquals(1, groups.getLength());
        assertEquals("Zemin", ((Element) groups.item(0)).getAttributeNS(INKSCAPE, "label"));
        assertEquals(3, ((Element) groups.item(0)).getElementsByTagName("path").getLength());
    }

    @Test
    void aLayerMergedIntoARecoloredLayerTakesItsNewColor() throws Exception {
        String edited = SvgLayers.withLayerEdits(INTERLEAVED, new LayerEdits(Set.of(),
                Map.of("#ff0000", "#0000ff", "#0000ff", "#00ff00"), Map.of(), List.of()));

        NodeList groups = render(edited, new SvgExportSettings(16, 16, 1)).getElementsByTagName("g");

        assertEquals(1, groups.getLength());
        assertEquals("#00ff00", ((Element) groups.item(0)).getAttribute("data-color"));
    }

    @Test
    void mosaicModeCutsLaterLayersOutOfEarlierOnes() throws Exception {
        Path2D stacked = firstPath(render(STACKED, new SvgExportSettings(16, 16, 1)));
        Path2D mosaic = firstPath(render(STACKED, new SvgExportSettings(16, 16, 1, true)));

        assertTrue(stacked.contains(5, 5));
        assertFalse(mosaic.contains(5, 5));
        assertTrue(mosaic.contains(1, 1));
    }

    @Test
    void layerFilesHoldOnlyWhatIsVisibleInTheirColor(@TempDir Path directory) throws Exception {
        service.export(List.of(new IconVariant("Badge", "", STACKED)), new SvgExportSettings(16, 16, 1),
                directory, ExportFormat.LAYER_SVGS);

        Element blue = parse(Files.readString(directory.resolve("badge-0000ff.svg")));
        Element red = parse(Files.readString(directory.resolve("badge-ff0000.svg")));
        assertEquals(1, blue.getElementsByTagName("path").getLength());
        assertFalse(firstPath(blue).contains(5, 5));
        assertEquals(1, red.getElementsByTagName("path").getLength());
        assertTrue(firstPath(red).contains(5, 5));
        try (var files = Files.list(directory)) {
            assertEquals(2, files.count());
        }
    }

    private Element render(String svg, SvgExportSettings settings) throws Exception {
        return parse(service.renderSvg(new IconVariant("Badge", "", svg), settings));
    }

    private static Path2D firstPath(Element root) {
        Element path = (Element) root.getElementsByTagName("path").item(0);
        return SvgPathGeometry.parse(path.getAttribute("d")).orElseThrow();
    }

    private static Element parse(String svg) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(svg))).getDocumentElement();
    }
}
