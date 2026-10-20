package org.example.ui;

import org.example.export.ExportFormat;
import org.example.export.SvgExportService;
import org.example.export.SvgExportSettings;
import org.example.model.IconAsset;
import org.example.model.IconPack;
import org.example.vector.ImageTracerVectorizer;
import org.example.vector.PixelCoordinate;
import org.example.vector.TraceOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class IconPackControllerTest {
    @Test
    void importsJpgAndJpegFilesAndStripsTheirExtensions(@TempDir Path directory) throws Exception {
        Path jpg = directory.resolve("photo.jpg");
        Path jpeg = directory.resolve("shot.JPEG");
        ImageIO.write(new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "jpeg", jpg.toFile());
        ImageIO.write(new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "jpeg", jpeg.toFile());
        IconPackController controller = controller();

        List<ImportIssue> issues = controller.addImages(List.of(jpg, jpeg));

        assertTrue(issues.isEmpty());
        assertEquals(List.of("photo", "shot"),
                controller.pack().icons().stream().map(IconAsset::name).toList());
    }

    @Test
    void appliesDrawnEraseStrokesToPreview(@TempDir Path directory) throws Exception {
        Path source = writePng(directory.resolve("sun.png"), Color.ORANGE);
        IconPackController controller = controller();
        controller.addImages(List.of(source));
        IconAsset icon = controller.pack().icons().getFirst();
        String before = controller.preview(icon).getFirst().svg();

        controller.addEraseStroke(icon, new org.example.vector.EraseStroke(
                List.of(new PixelCoordinate(0, 0), new PixelCoordinate(3, 3)), 2));

        assertEquals(1, controller.eraseStrokes(icon).size());
        assertFalse(before.equals(controller.preview(icon).getFirst().svg()));
        assertThrows(IllegalArgumentException.class, () -> controller.addEraseStroke(icon,
                new org.example.vector.EraseStroke(List.of(new PixelCoordinate(999, 0)), 1)));
        controller.clearBackgroundRemovalSeeds(icon);
        assertTrue(controller.eraseStrokes(icon).isEmpty());
    }

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

    @Test
    void rejectsNonPngImageContentEvenWhenTheFileExtensionIsPng(@TempDir Path directory)
            throws Exception {
        Path mislabeled = directory.resolve("photo.png");
        ImageIO.write(new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "jpeg", mislabeled.toFile());
        IconPackController controller = controller();

        List<ImportIssue> issues = controller.addImages(List.of(mislabeled));

        assertEquals(1, issues.size());
        assertTrue(controller.pack().icons().isEmpty());
    }

    @Test
    void storesBackgroundSeedsPerIconAndClearsOnlyTheRequestedIcon(@TempDir Path directory)
            throws Exception {
        Path first = writePng(directory.resolve("first.png"), Color.RED);
        Path second = writePng(directory.resolve("second.png"), Color.BLUE);
        IconPackController controller = controller();
        controller.addImages(List.of(first, second));
        IconAsset firstIcon = controller.pack().icons().get(0);
        IconAsset secondIcon = controller.pack().icons().get(1);
        PixelCoordinate firstSeed = new PixelCoordinate(2, 2);
        PixelCoordinate secondSeed = new PixelCoordinate(3, 3);

        controller.addBackgroundRemovalSeed(firstIcon, firstSeed);
        controller.addBackgroundRemovalSeed(firstIcon, firstSeed);
        controller.addBackgroundRemovalSeed(secondIcon, secondSeed);

        assertEquals(List.of(firstSeed), controller.backgroundRemovalSeeds(firstIcon));
        assertEquals(List.of(secondSeed), controller.backgroundRemovalSeeds(secondIcon));

        controller.clearBackgroundRemovalSeeds(firstIcon);

        assertEquals(List.of(), controller.backgroundRemovalSeeds(firstIcon));
        assertEquals(List.of(secondSeed), controller.backgroundRemovalSeeds(secondIcon));
    }

    @Test
    void rejectsAnEqualButDistinctIconInstance(@TempDir Path directory) throws Exception {
        Path source = writePng(directory.resolve("same.png"), Color.RED);
        IconPackController controller = controller();
        controller.addImages(List.of(source));
        IconAsset imported = controller.pack().icons().getFirst();
        IconAsset equalButDistinct = new IconAsset(imported.source(), imported.name(), imported.image());
        assertEquals(imported, equalButDistinct);
        assertNotSame(imported, equalButDistinct);

        assertThrows(IllegalArgumentException.class,
                () -> controller.backgroundRemovalSeeds(equalButDistinct));
    }

    @Test
    void undoRevertsBackgroundEditsOneStepAtATime(@TempDir Path directory) throws Exception {
        Path source = writePng(directory.resolve("sun.png"), Color.ORANGE);
        IconPackController controller = controller();
        controller.addImages(List.of(source));
        IconAsset icon = controller.pack().icons().getFirst();
        PixelCoordinate seed = new PixelCoordinate(2, 2);
        org.example.vector.EraseStroke stroke =
                new org.example.vector.EraseStroke(List.of(new PixelCoordinate(1, 1)), 1);

        assertFalse(controller.canUndoBackgroundEdit(icon));
        controller.addBackgroundRemovalSeed(icon, seed);
        controller.addEraseStroke(icon, stroke);
        controller.setBackgroundTolerance(icon, 60);
        controller.clearBackgroundRemovalSeeds(icon);

        assertTrue(controller.undoBackgroundEdit(icon));
        assertEquals(List.of(seed), controller.backgroundRemovalSeeds(icon));
        assertEquals(List.of(stroke), controller.eraseStrokes(icon));
        assertEquals(60, controller.backgroundSelection(icon).tolerance());
        assertTrue(controller.undoBackgroundEdit(icon));
        assertTrue(controller.eraseStrokes(icon).isEmpty());
        assertTrue(controller.undoBackgroundEdit(icon));
        assertTrue(controller.backgroundRemovalSeeds(icon).isEmpty());
        assertFalse(controller.undoBackgroundEdit(icon));
    }

    @Test
    void protectStrokesRestorePixelsInTheProcessedSource(@TempDir Path directory) throws Exception {
        Path source = writePng(directory.resolve("sun.png"), Color.ORANGE);
        IconPackController controller = controller();
        controller.addImages(List.of(source));
        IconAsset icon = controller.pack().icons().getFirst();

        controller.addBackgroundRemovalSeed(icon, new PixelCoordinate(3, 3));
        assertEquals(0, controller.processedSource(icon).getRGB(3, 3) >>> 24);
        controller.addProtectStroke(icon,
                new org.example.vector.EraseStroke(List.of(new PixelCoordinate(3, 3)), 1));

        assertEquals(255, controller.processedSource(icon).getRGB(3, 3) >>> 24);
        assertEquals(0, controller.processedSource(icon).getRGB(5, 5) >>> 24);
    }

    @Test
    void hiddenLayersAreListedButLeftOutOfPreviewAndExport(@TempDir Path directory) throws Exception {
        BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        for (int y = 1; y < 7; y++) {
            for (int x = 1; x < 7; x++) {
                image.setRGB(x, y, (x < 4 ? Color.RED : Color.BLUE).getRGB());
            }
        }
        Path source = directory.resolve("split.png");
        ImageIO.write(image, "png", source.toFile());
        IconPackController controller = controller();
        controller.addImages(List.of(source));
        IconAsset icon = controller.pack().icons().getFirst();

        controller.setLayerHidden(icon, "#ff0000", true);
        IconPreview preview = controller.previewWithLayers(icon);

        assertEquals(List.of("#ff0000", "#0000ff"),
                preview.layers().stream().map(org.example.export.SvgLayer::color).toList());
        String svg = preview.variants().getFirst().svg();
        assertFalse(svg.contains("rgb(255,0,0)"));
        assertTrue(svg.contains("rgb(0,0,255)"));

        Path exported = directory.resolve("out");
        controller.export(exported, ExportFormat.INDIVIDUAL_SVGS);
        String exportedSvg = Files.readString(exported.resolve("split-default.svg"));
        assertFalse(exportedSvg.contains("rgb(255,0,0)"));
        assertTrue(exportedSvg.contains("data-color=\"#0000ff\""));
    }

    @Test
    void suggestedRegionsLeaveOutAreasThatAreAlreadyRemoved(@TempDir Path directory) throws Exception {
        // A transparent margin keeps automatic edge cleanup out of the way.
        BufferedImage image = new BufferedImage(14, 14, BufferedImage.TYPE_INT_ARGB);
        for (int y = 1; y < 13; y++) {
            for (int x = 1; x < 13; x++) {
                image.setRGB(x, y, (x < 7 ? Color.GREEN : Color.BLUE).getRGB());
            }
        }
        for (int y = 4; y < 8; y++) {
            image.setRGB(2, y, Color.RED.getRGB());
            image.setRGB(11, y, Color.RED.getRGB());
        }
        Path source = directory.resolve("halves.png");
        ImageIO.write(image, "png", source.toFile());
        IconPackController controller = controller();
        controller.addImages(List.of(source));
        IconAsset icon = controller.pack().icons().getFirst();
        int green = Color.GREEN.getRGB() & 0xffffff;

        assertTrue(controller.suggestedRegions(icon).stream().anyMatch(region -> region.rgb() == green));
        controller.addBackgroundRemovalSeed(icon, controller.suggestedRegions(icon).stream()
                .filter(region -> region.rgb() == green).findFirst().orElseThrow().seed());

        assertTrue(controller.suggestedRegions(icon).stream().noneMatch(region -> region.rgb() == green));
    }

    @Test
    void detectedSubjectKeepsOnlyTheSubjectAndCanBeUndone(@TempDir Path directory) throws Exception {
        Path source = writePng(directory.resolve("sun.png"), Color.ORANGE);
        IconPackController controller = controller();
        controller.addImages(List.of(source));
        IconAsset icon = controller.pack().icons().getFirst();
        byte[] foreground = new byte[64];
        for (int y = 2; y < 6; y++) {
            for (int x = 2; x < 6; x++) {
                foreground[y * 8 + x] = (byte) 255;
            }
        }

        assertFalse(controller.hasSubjectMask(icon));
        assertThrows(IllegalStateException.class, () -> controller.setKeepSubjectOnly(icon, true));
        controller.detectSubject(icon, image -> new org.example.vector.SubjectMask(8, 8, foreground));

        assertTrue(controller.hasSubjectMask(icon));
        assertTrue(controller.backgroundSelection(icon).keepSubjectOnly());
        assertEquals(0, controller.processedSource(icon).getRGB(1, 1) >>> 24);
        assertEquals(255, controller.processedSource(icon).getRGB(3, 3) >>> 24);

        assertTrue(controller.undoBackgroundEdit(icon));
        assertFalse(controller.backgroundSelection(icon).keepSubjectOnly());
        assertEquals(255, controller.processedSource(icon).getRGB(1, 1) >>> 24);
        assertTrue(controller.hasSubjectMask(icon));
    }

    @Test
    void recoloredLayersReachPreviewAndExportAndCanBeRestored(@TempDir Path directory) throws Exception {
        Path source = writePng(directory.resolve("sun.png"), Color.RED);
        IconPackController controller = controller();
        controller.addImages(List.of(source));
        IconAsset icon = controller.pack().icons().getFirst();

        controller.setLayerColor(icon, "#ff0000", "#00ff00");
        IconPreview preview = controller.previewWithLayers(icon);

        assertEquals("#ff0000", preview.layers().getFirst().color());
        assertTrue(preview.variants().getFirst().svg().contains("data-color=\"#00ff00\""));
        Path exported = directory.resolve("out");
        controller.export(exported, ExportFormat.INDIVIDUAL_SVGS);
        assertTrue(Files.readString(exported.resolve("sun-default.svg")).contains("#00ff00"));

        controller.setLayerColor(icon, "#ff0000", null);
        assertTrue(controller.layerColors(icon).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> controller.setLayerColor(icon, "#ff0000", "green"));
    }

    @Test
    void sourcePreviewUsesTheSameCanvasAndCropAsTheVector(@TempDir Path directory) throws Exception {
        Path source = writePng(directory.resolve("sun.png"), Color.RED);
        IconPackController controller = controller();
        controller.addImages(List.of(source));
        controller.setExportSettings(new SvgExportSettings(64, 32, 0.5));
        IconAsset icon = controller.pack().icons().getFirst();

        String sourceSvg = controller.previewWithLayers(icon).sourceSvg();

        assertTrue(sourceSvg.contains("viewBox=\"0 0 64 32\""));
        assertTrue(sourceSvg.contains("x=\"16.0\" y=\"8.0\" width=\"32.0\" height=\"16.0\""));
        // The 6x6 square plus the one-pixel margin the tracer keeps.
        assertTrue(sourceSvg.contains("viewBox=\"0 0 8 8\""));
        assertTrue(sourceSvg.contains("data:image/png;base64,"));
    }

    @Test
    void storesHowEachRegionClickGrowsAndUndoesItLikeAnyEdit(@TempDir Path directory) throws Exception {
        Path source = writePng(directory.resolve("sun.png"), Color.ORANGE);
        IconPackController controller = controller();
        controller.addImages(List.of(source));
        IconAsset icon = controller.pack().icons().getFirst();
        org.example.vector.RegionSeed gradient =
                new org.example.vector.RegionSeed(new PixelCoordinate(0, 0), true, false);

        controller.addBackgroundRegion(icon, gradient);

        assertEquals(List.of(gradient), controller.backgroundSelection(icon).regions());
        assertEquals(List.of(new PixelCoordinate(0, 0)), controller.backgroundRemovalSeeds(icon));
        assertTrue(controller.undoBackgroundEdit(icon));
        assertTrue(controller.backgroundSelection(icon).regions().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> controller.addBackgroundRegion(icon,
                new org.example.vector.RegionSeed(new PixelCoordinate(8, 0), false, true)));
    }

    @Test
    void detectionHandsTheFrameAndBrushStrokesToTheSegmenter(@TempDir Path directory) throws Exception {
        Path source = writePng(directory.resolve("sun.png"), Color.ORANGE);
        IconPackController controller = controller();
        controller.addImages(List.of(source));
        IconAsset icon = controller.pack().icons().getFirst();
        org.example.vector.EraseStroke erase =
                new org.example.vector.EraseStroke(List.of(new PixelCoordinate(1, 1)), 1);
        org.example.vector.EraseStroke protect =
                new org.example.vector.EraseStroke(List.of(new PixelCoordinate(4, 4)), 1);
        controller.addEraseStroke(icon, erase);
        controller.addProtectStroke(icon, protect);
        controller.setSubjectFrame(icon, new java.awt.Rectangle(1, 1, 6, 6));
        List<org.example.segmentation.SegmentationHints> seen = new java.util.ArrayList<>();

        controller.detectSubject(icon, new org.example.segmentation.SubjectSegmenter() {
            @Override
            public org.example.vector.SubjectMask segment(BufferedImage image) {
                throw new AssertionError("hints must be passed");
            }

            @Override
            public org.example.vector.SubjectMask segment(
                    BufferedImage image, org.example.segmentation.SegmentationHints hints) {
                seen.add(hints);
                return new org.example.vector.SubjectMask(8, 8, new byte[64]);
            }
        });

        assertEquals(new java.awt.Rectangle(1, 1, 6, 6), seen.getFirst().frame());
        assertEquals(List.of(protect), seen.getFirst().keep());
        assertEquals(List.of(erase), seen.getFirst().remove());
        assertThrows(IllegalArgumentException.class,
                () -> controller.setSubjectFrame(icon, new java.awt.Rectangle(4, 4, 10, 10)));
    }

    @Test
    void redoBringsBackWhatUndoRemovedUntilANewEditIsMade(@TempDir Path directory) throws Exception {
        Path source = writePng(directory.resolve("sun.png"), Color.ORANGE);
        IconPackController controller = controller();
        controller.addImages(List.of(source));
        IconAsset icon = controller.pack().icons().getFirst();
        controller.addBackgroundRemovalSeed(icon, new PixelCoordinate(0, 0));
        controller.addBackgroundRemovalSeed(icon, new PixelCoordinate(7, 7));
        assertFalse(controller.canRedoBackgroundEdit(icon));

        controller.undoBackgroundEdit(icon);
        controller.undoBackgroundEdit(icon);
        assertTrue(controller.redoBackgroundEdit(icon));

        assertEquals(List.of(new PixelCoordinate(0, 0)), controller.backgroundRemovalSeeds(icon));
        assertTrue(controller.canRedoBackgroundEdit(icon));
        controller.addBackgroundRemovalSeed(icon, new PixelCoordinate(3, 3));
        assertFalse(controller.canRedoBackgroundEdit(icon));
        assertFalse(controller.redoBackgroundEdit(icon));
    }

    @Test
    void addsAPastedImageUnderItsOwnName() {
        IconPackController controller = controller();
        BufferedImage pasted = new BufferedImage(5, 4, BufferedImage.TYPE_INT_ARGB);

        IconAsset icon = controller.addImage("pano-1", pasted);

        assertEquals(List.of(icon), controller.pack().icons());
        assertEquals("pano-1", icon.name());
        assertEquals(5, icon.image().getWidth());
    }

    @Test
    void layersCanBeNamedReorderedAndMerged(@TempDir Path directory) throws Exception {
        BufferedImage image = new BufferedImage(12, 12, BufferedImage.TYPE_INT_ARGB);
        for (int y = 1; y < 11; y++) {
            for (int x = 1; x < 11; x++) {
                image.setRGB(x, y, (x < 6 ? Color.RED : Color.BLUE).getRGB());
            }
        }
        Path source = directory.resolve("flag.png");
        ImageIO.write(image, "png", source.toFile());
        IconPackController controller = controller();
        controller.addImages(List.of(source));
        IconAsset icon = controller.pack().icons().getFirst();
        List<String> traced = controller.previewWithLayers(icon).layers().stream()
                .map(org.example.export.SvgLayer::color).toList();
        assertEquals(2, traced.size());

        controller.setLayerOrder(icon, List.of(traced.get(1), traced.get(0)));
        controller.setLayerName(icon, traced.get(1), "Sağ");
        org.example.ui.IconPreview reordered = controller.previewWithLayers(icon);

        assertEquals(List.of(traced.get(1), traced.get(0)),
                reordered.layers().stream().map(org.example.export.SvgLayer::color).toList());
        assertTrue(reordered.variants().getFirst().svg().contains("inkscape:label=\"Sağ\""));
        assertEquals(java.util.Map.of(traced.get(1), "Sağ"), controller.layerNames(icon));

        controller.mergeLayer(icon, traced.get(0), traced.get(1));

        String merged = controller.previewWithLayers(icon).variants().getFirst().svg();
        assertEquals(1, merged.split("<g ", -1).length - 1);
        assertEquals(traced.get(1), controller.layerColors(icon).get(traced.get(0)));
    }

    @Test
    void aColorRuleRemovesMatchingEdgeRegionsInEveryIconAsOneEditEach(@TempDir Path directory) throws Exception {
        IconPackController controller = controller();
        controller.addImages(List.of(framed(directory.resolve("a.png"), new Color(250, 250, 250), Color.RED),
                framed(directory.resolve("b.png"), new Color(244, 246, 248), Color.BLUE),
                framed(directory.resolve("c.png"), new Color(20, 120, 40), Color.ORANGE)));
        List<IconAsset> icons = controller.pack().icons();

        int changed = controller.applyColorRule(0xffffff, 12);

        assertEquals(2, changed);
        assertEquals(0, controller.processedSource(icons.get(0)).getRGB(0, 0) >>> 24);
        assertEquals(0, controller.processedSource(icons.get(1)).getRGB(11, 11) >>> 24);
        assertEquals(255, controller.processedSource(icons.get(0)).getRGB(6, 6) >>> 24);
        assertTrue(controller.backgroundSelection(icons.get(2)).regions().isEmpty());
        assertTrue(controller.undoBackgroundEdit(icons.get(0)));
        assertFalse(controller.canUndoBackgroundEdit(icons.get(0)));
    }

    @Test
    void copiesBackgroundAndLayerSettingsToSimilarIcons(@TempDir Path directory) throws Exception {
        IconPackController controller = controller();
        controller.addImages(List.of(framed(directory.resolve("a.png"), Color.WHITE, new Color(200, 30, 30)),
                framed(directory.resolve("b.png"), Color.WHITE, new Color(196, 34, 28))));
        IconAsset source = controller.pack().icons().get(0);
        IconAsset target = controller.pack().icons().get(1);
        controller.setBackgroundTolerance(source, 20);
        controller.setDefringe(source, false);
        String red = controller.previewWithLayers(source).layers().stream()
                .map(org.example.export.SvgLayer::color).filter(color -> !color.equals("#ffffff")).findFirst().orElseThrow();
        controller.setLayerColor(source, red, "#c8f25c");
        controller.setLayerName(source, red, "Kalp");

        controller.copySettings(source, List.of(target));

        assertEquals(20, controller.backgroundSelection(target).tolerance());
        assertFalse(controller.backgroundSelection(target).defringe());
        String targetRed = controller.previewWithLayers(target).layers().stream()
                .map(org.example.export.SvgLayer::color).filter(color -> !color.equals("#ffffff")).findFirst().orElseThrow();
        assertEquals("#c8f25c", controller.layerColors(target).get(targetRed));
        assertEquals("Kalp", controller.layerNames(target).get(targetRed));
    }

    @Test
    void reportsHowFarEachIconHasBeenProcessed(@TempDir Path directory) throws Exception {
        IconPackController controller = controller();
        controller.addImages(List.of(writePng(directory.resolve("sun.png"), Color.ORANGE)));
        IconAsset icon = controller.pack().icons().getFirst();
        assertEquals(IconStatus.UNTOUCHED, controller.status(icon));

        controller.addBackgroundRemovalSeed(icon, new PixelCoordinate(0, 0));
        assertEquals(IconStatus.BACKGROUND_SEPARATED, controller.status(icon));

        controller.setLayerHidden(icon, "#ffa500", true);
        assertEquals(IconStatus.LAYERS_EDITED, controller.status(icon));
    }

    private static Path framed(Path path, Color background, Color artwork) throws Exception {
        BufferedImage image = new BufferedImage(12, 12, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 12; y++) {
            for (int x = 0; x < 12; x++) {
                image.setRGB(x, y, (x > 2 && x < 9 && y > 2 && y < 9 ? artwork : background).getRGB());
            }
        }
        ImageIO.write(image, "png", path.toFile());
        return path;
    }

    @Test
    void tracesEachSettingOnlyOnceUntilSomethingChanges(@TempDir Path directory) throws Exception {
        java.util.concurrent.atomic.AtomicInteger traces = new java.util.concurrent.atomic.AtomicInteger();
        ImageTracerVectorizer tracer = new ImageTracerVectorizer();
        IconPackController controller = new IconPackController(new IconPack(), (image, options) -> {
            traces.incrementAndGet();
            return tracer.vectorize(image, options);
        }, new SvgExportService());
        controller.addImages(List.of(writePng(directory.resolve("sun.png"), Color.ORANGE)));
        IconAsset icon = controller.pack().icons().getFirst();

        controller.previewWithLayers(icon);
        controller.previewWithLayers(icon);
        controller.setLayerHidden(icon, "#ffa500", false);
        controller.previewWithLayers(icon);
        assertEquals(1, traces.get());

        controller.setBackgroundTolerance(icon, 20);
        controller.previewWithLayers(icon);
        assertEquals(2, traces.get());
    }

    @Test
    void previewsLargeImagesFromASmallerCopyWithTheSameLayerColors(@TempDir Path directory) throws Exception {
        BufferedImage wide = new BufferedImage(2400, 60, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D graphics = wide.createGraphics();
        graphics.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, 2400, 60);
        graphics.setColor(new Color(0x1E2A44));
        graphics.fillOval(100, 5, 900, 50);
        graphics.setColor(new Color(0xE9B949));
        graphics.fillOval(1300, 5, 900, 50);
        graphics.dispose();
        Path source = directory.resolve("wide.png");
        ImageIO.write(wide, "png", source.toFile());
        IconPackController controller = controller();
        controller.addImages(List.of(source));
        IconAsset icon = controller.pack().icons().getFirst();

        org.example.ui.IconPreview preview = controller.previewWithLayers(icon);
        Path exported = directory.resolve("out");
        controller.export(exported, ExportFormat.INDIVIDUAL_SVGS);
        String full = Files.readString(exported.resolve("wide-default.svg"));

        assertTrue(viewBoxWidth(preview.variants().getFirst().svg()) <= IconPackController.MAX_PREVIEW_SIDE + 2);
        assertTrue(viewBoxWidth(full) > 2000);
        assertEquals(preview.layers().stream().map(org.example.export.SvgLayer::color).sorted().toList(),
                org.example.export.SvgLayers.layers(full).stream().map(org.example.export.SvgLayer::color).sorted().toList());
    }

    private static double viewBoxWidth(String svg) {
        java.util.regex.Matcher inner = java.util.regex.Pattern.compile("<svg[^>]*viewBox=\"[-0-9.]+ [-0-9.]+ ([0-9.]+)")
                .matcher(svg.substring(svg.indexOf('>') + 1));
        assertTrue(inner.find(), svg);
        return Double.parseDouble(inner.group(1));
    }

    @Test
    void paletteVariantsKeepShapeFittingAndSymmetry(@TempDir Path directory) throws Exception {
        List<TraceOptions> seen = new java.util.concurrent.CopyOnWriteArrayList<>();
        ImageTracerVectorizer tracer = new ImageTracerVectorizer();
        IconPackController controller = new IconPackController(new IconPack(), (image, options) -> {
            seen.add(options);
            return tracer.vectorize(image, options);
        }, new SvgExportService());
        controller.addImages(List.of(writePng(directory.resolve("sun.png"), Color.ORANGE)));
        controller.setPalettes(List.of(List.of(Color.RED), List.of(Color.BLUE)));
        controller.setTraceOptions(new TraceOptions(TraceOptions.ColorMode.CUSTOM_PALETTE, 1, List.of(Color.RED), 0,
                true, TraceOptions.Symmetry.HORIZONTAL));

        controller.previewWithLayers(controller.pack().icons().getFirst());

        assertEquals(2, seen.size());
        assertTrue(seen.stream().allMatch(options -> options.fitShapes()
                && options.symmetry() == TraceOptions.Symmetry.HORIZONTAL));
    }

    @Test
    void startingOverForgetsCachedTracesOfTheOldImages(@TempDir Path directory) throws Exception {
        IconPackController controller = controller();
        controller.addImages(List.of(writePng(directory.resolve("sun.png"), Color.ORANGE)));
        controller.previewWithLayers(controller.pack().icons().getFirst());
        java.util.Map<?, ?> traces = FxTestSupport.field(controller, "traces");
        assertFalse(traces.isEmpty());

        controller.newProject();

        assertTrue(traces.isEmpty());
    }

    @Test
    void newPackReducesToEightColorsAtTheDetailedLevel() {
        TraceOptions options = controller().pack().traceOptions();

        assertEquals(TraceOptions.ColorMode.REDUCE_SOURCE, options.mode());
        assertEquals(8, options.maxColors());
        assertEquals(1, options.simplification());
    }

    @Test
    void suggestsReducingColorsOnlyWhenSourceColorsProduceTooManyLayers() {
        IconPackController controller = controller();
        assertTrue(controller.colorReductionAdvice(365).isEmpty());

        controller.setTraceOptions(TraceOptions.preserveSourceColors());

        assertEquals("Bu görselde 365 ton var. Renk sayısını azaltmak ister misin?",
                controller.colorReductionAdvice(365).orElseThrow());
        assertTrue(controller.colorReductionAdvice(IconPackController.MAX_COMFORTABLE_LAYERS).isEmpty());
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
