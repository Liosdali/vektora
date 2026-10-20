package org.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipInputStream;
import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilderFactory;
import org.example.export.ExportFormat;
import org.example.export.SvgExportService;
import org.example.export.SvgExportSettings;
import org.example.model.IconPack;
import org.example.ui.IconPackController;
import org.example.vector.ImageTracerVectorizer;
import org.example.vector.TraceOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import java.io.StringReader;

class IconPackIntegrationTest {
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
                assertEquals("32", root.getAttribute("width"));
                assertEquals("24", root.getAttribute("height"));
                assertTrue(document.getElementsByTagNameNS(
                        "http://www.w3.org/2000/svg", "image").getLength() == 0);
                if ("sprite.svg".equals(entry.getName())) {
                    symbolCount = document.getElementsByTagNameNS(
                            "http://www.w3.org/2000/svg", "symbol").getLength();
                } else {
                    assertTrue(document.getElementsByTagNameNS(
                            "http://www.w3.org/2000/svg", "path").getLength() > 0);
                    svgCount++;
                }
                zip.closeEntry();
            }
        }
        assertEquals(4, svgCount);
        assertEquals(4, symbolCount);
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
