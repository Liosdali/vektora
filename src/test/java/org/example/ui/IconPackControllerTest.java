package org.example.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.example.export.ExportFormat;
import org.example.export.SvgExportService;
import org.example.export.SvgExportSettings;
import org.example.model.IconAsset;
import org.example.model.IconPack;
import org.example.vector.ImageTracerVectorizer;
import org.example.vector.TraceOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IconPackControllerTest {
    @Test
    void addsValidPngsAndReportsInvalidFilesWithoutLosingGoodImages(@TempDir Path directory)
            throws Exception {
        Path valid = writePng(directory.resolve("sun.png"), Color.ORANGE);
        Path invalid = Files.writeString(directory.resolve("broken.png"), "not a PNG");
        BufferedImage original = ImageIO.read(valid.toFile());
        IconPackController controller = controller();

        List<ImportIssue> issues = controller.addImages(List.of(valid, invalid));

        assertEquals(1, controller.pack().icons().size());
        assertEquals("sun", controller.pack().icons().getFirst().name());
        assertEquals(1, issues.size());
        assertEquals(invalid, issues.getFirst().path());
        assertFalse(issues.getFirst().message().isBlank());
        assertEquals(original.getRGB(0, 0), controller.pack().icons().getFirst().image().getRGB(0, 0));
    }

    @Test
    void previewsOneVariantForEachCustomPalette(@TempDir Path directory) throws Exception {
        Path source = writePng(directory.resolve("star.png"), Color.RED);
        IconPackController controller = controller();
        controller.addImages(List.of(source));
        controller.setPalettes(List.of(List.of(Color.BLUE), List.of(Color.GREEN)));
        controller.setTraceOptions(new TraceOptions(
                TraceOptions.ColorMode.CUSTOM_PALETTE, 1, List.of(Color.BLUE)));
        IconAsset icon = controller.pack().icons().getFirst();

        var variants = controller.preview(icon);

        assertEquals(2, variants.size());
        assertEquals("palette-1", variants.get(0).variantName());
        assertEquals("palette-2", variants.get(1).variantName());
        assertTrue(variants.get(0).svg().contains("rgb(0,0,255)"));
        assertTrue(variants.get(1).svg().contains("rgb(0,255,0)"));
    }

    @Test
    void usesSourceColorsWhenPaletteModeIsNotSelected(@TempDir Path directory) throws Exception {
        Path source = writePng(directory.resolve("star.png"), Color.RED);
        IconPackController controller = controller();
        controller.addImages(List.of(source));
        controller.setPalettes(List.of(List.of(Color.BLUE), List.of(Color.GREEN)));

        var variants = controller.preview(controller.pack().icons().getFirst());

        assertEquals(1, variants.size());
        assertEquals("default", variants.getFirst().variantName());
        assertTrue(variants.getFirst().svg().contains("rgb(255,0,0)"));
    }

    @Test
    void previewUsesThePackageCanvasAndScale(@TempDir Path directory) throws Exception {
        Path source = writePng(directory.resolve("star.png"), Color.RED);
        IconPackController controller = controller();
        controller.addImages(List.of(source));
        controller.setExportSettings(new SvgExportSettings(64, 32, 0.5));

        var preview = controller.preview(controller.pack().icons().getFirst()).getFirst();

        assertTrue(preview.svg().contains("viewBox=\"0 0 64 32\""));
        assertTrue(preview.svg().contains("x=\"16\""));
        assertTrue(preview.svg().contains("width=\"32\""));
    }

    @Test
    void usesOnePackageWideCanvasAndScaleForEveryExportedIcon(@TempDir Path directory)
            throws Exception {
        Path first = writePng(directory.resolve("first.png"), Color.RED);
        Path second = writePng(directory.resolve("second.png"), Color.BLUE);
        IconPackController controller = controller();
        controller.addImages(List.of(first, second));
        controller.setExportSettings(new SvgExportSettings(48, 24, 0.75));

        Path destination = directory.resolve("output");
        controller.export(destination, ExportFormat.INDIVIDUAL_SVGS);

        try (Stream<Path> exportedFiles = Files.list(destination)) {
            List<Path> exported = exportedFiles.toList();
            assertEquals(2, exported.size());
            for (Path svgFile : exported) {
                String svg = Files.readString(svgFile);
                assertTrue(svg.contains("width=\"48\""));
                assertTrue(svg.contains("height=\"24\""));
                assertTrue(svg.contains("viewBox=\"0 0 48 24\""));
            }
        }
        assertTrue(controller.pack().exportSettings().scale() == 0.75);
    }

    @Test
    void reportsNonPngInputsAsImportIssues(@TempDir Path directory) throws Exception {
        Path jpeg = Files.writeString(directory.resolve("icon.jpg"), "not a PNG");
        IconPackController controller = controller();

        List<ImportIssue> issues = controller.addImages(List.of(jpeg));

        assertEquals(1, issues.size());
        assertTrue(issues.getFirst().message().toLowerCase().contains("png"));
        assertTrue(controller.pack().icons().isEmpty());
    }

    private static IconPackController controller() {
        return new IconPackController(new IconPack(), new ImageTracerVectorizer(), new SvgExportService());
    }

    private static Path writePng(Path path, Color color) throws Exception {
        BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        for (int y = 1; y < 7; y++) {
            for (int x = 1; x < 7; x++) {
                image.setRGB(x, y, color.getRGB());
            }
        }
        ImageIO.write(image, "png", path.toFile());
        return path;
    }
}
