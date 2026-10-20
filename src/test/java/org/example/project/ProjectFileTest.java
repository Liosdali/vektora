package org.example.project;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;
import org.example.export.ExportFormat;
import org.example.export.SvgExportService;
import org.example.export.SvgExportSettings;
import org.example.model.IconAsset;
import org.example.model.IconPack;
import org.example.ui.IconPackController;
import org.example.ui.IconPreview;
import org.example.vector.EraseStroke;
import org.example.vector.ImageTracerVectorizer;
import org.example.vector.PixelCoordinate;
import org.example.vector.RegionSeed;
import org.example.vector.SubjectMask;
import org.example.vector.TraceOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectFileTest {
    @Test
    void reopenedProjectPreviewsAndExportsTheSameBytes(@TempDir Path directory) throws Exception {
        IconPackController original = controller();
        original.addImages(List.of(badge(directory.resolve("sun.png"), Color.ORANGE),
                badge(directory.resolve("moon.jpg"), new Color(0x1E2A44))));
        IconAsset sun = original.pack().icons().get(0);
        IconAsset moon = original.pack().icons().get(1);
        original.addBackgroundRegion(sun, new RegionSeed(new PixelCoordinate(0, 0), true, true));
        original.addEraseStroke(sun, new EraseStroke(List.of(new PixelCoordinate(20, 20), new PixelCoordinate(25, 22)), 2));
        original.addProtectStroke(sun, new EraseStroke(List.of(new PixelCoordinate(30, 30)), 3));
        original.setBackgroundTolerance(sun, 20);
        original.setDefringe(moon, false);
        original.addBackgroundRegion(moon, new RegionSeed(new PixelCoordinate(1, 1), false, false));
        byte[] maskValues = new byte[48 * 48];
        for (int index = 0; index < maskValues.length; index++) {
            maskValues[index] = (byte) (index % 48 > 8 ? 255 : index % 7);
        }
        original.detectSubject(moon, image -> new SubjectMask(48, 48, maskValues));
        original.setSubjectFrame(moon, new Rectangle(4, 4, 40, 40));
        original.setTraceOptions(new TraceOptions(TraceOptions.ColorMode.REDUCE_SOURCE, 6, List.of(), 2));
        original.setExportSettings(new SvgExportSettings(96, 64, 0.8, true));
        original.setPalettes(List.of(List.of(Color.RED, new Color(10, 20, 30, 128))));
        String sunLayer = original.previewWithLayers(sun).layers().getFirst().color();
        original.setLayerColor(sun, sunLayer, "#c8f25c");
        original.setLayerName(sun, sunLayer, "Güneş");
        List<String> sunLayers = original.previewWithLayers(sun).layers().stream()
                .map(org.example.export.SvgLayer::color).toList();
        original.setLayerOrder(sun, sunLayers.reversed());
        original.setLayerHidden(moon, original.previewWithLayers(moon).layers().getFirst().color(), true);
        Path project = directory.resolve("logo.vektora");

        ProjectFile.save(original.pack(), project);
        IconPackController reopened = controller();
        ProjectFile.load(project, reopened.pack());

        assertEquals(2, reopened.pack().icons().size());
        for (int index = 0; index < 2; index++) {
            IconPreview before = original.previewWithLayers(original.pack().icons().get(index));
            IconPreview after = reopened.previewWithLayers(reopened.pack().icons().get(index));
            assertEquals(before.variants(), after.variants());
            assertEquals(before.sourceSvg(), after.sourceSvg());
        }
        IconAsset reopenedMoon = reopened.pack().icons().get(1);
        assertEquals(new Rectangle(4, 4, 40, 40), reopened.subjectFrame(reopenedMoon));
        assertEquals(original.backgroundSelection(moon), reopened.backgroundSelection(reopenedMoon));
        assertEquals(original.pack().palettes(), reopened.pack().palettes());
        assertEquals(original.pack().exportSettings(), reopened.pack().exportSettings());
        IconAsset reopenedSun = reopened.pack().icons().getFirst();
        assertEquals(original.layerNames(sun), reopened.layerNames(reopenedSun));
        assertEquals(original.layerOrder(sun), reopened.layerOrder(reopenedSun));
        assertTrue(!reopened.canUndoBackgroundEdit(reopenedMoon));
        Path first = directory.resolve("first.svg");
        Path second = directory.resolve("second.svg");
        original.export(first, ExportFormat.SPRITE_SVG);
        reopened.export(second, ExportFormat.SPRITE_SVG);
        assertArrayEquals(Files.readAllBytes(first), Files.readAllBytes(second));
    }

    @Test
    void refusesProjectsFromANewerVersion(@TempDir Path directory) throws IOException {
        Path project = directory.resolve("future.vektora");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(project))) {
            zip.putNextEntry(new ZipEntry("project.json"));
            zip.write("{\"format\":\"vektora-project\",\"version\":99,\"icons\":[]}".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }

        IOException failure = assertThrows(IOException.class, () -> ProjectFile.load(project, new IconPack()));
        assertTrue(failure.getMessage().contains("daha yeni"), failure.getMessage());
    }

    @Test
    void refusesFilesThatAreNotProjects(@TempDir Path directory) throws IOException {
        Path notAProject = directory.resolve("photo.vektora");
        try (OutputStream output = Files.newOutputStream(notAProject)) {
            output.write(new byte[] {1, 2, 3});
        }

        assertThrows(IOException.class, () -> ProjectFile.load(notAProject, new IconPack()));
    }

    private static IconPackController controller() {
        return new IconPackController(new IconPack(), new ImageTracerVectorizer(), new SvgExportService());
    }

    private static Path badge(Path path, Color color) throws IOException {
        BufferedImage image = new BufferedImage(48, 48, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(250, 248, 240));
        graphics.fillRect(0, 0, 48, 48);
        graphics.setColor(color);
        graphics.fillOval(6, 6, 36, 36);
        graphics.setColor(Color.WHITE);
        graphics.fillRect(18, 18, 12, 12);
        graphics.dispose();
        ImageIO.write(image, path.toString().endsWith(".jpg") ? "jpeg" : "png", path.toFile());
        return path;
    }
}
