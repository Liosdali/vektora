package org.example.ui;

import org.example.export.ExportFormat;
import org.example.export.IconVariant;
import org.example.export.SvgExportService;
import org.example.export.SvgExportSettings;
import org.example.export.SvgLayer;
import org.example.export.SvgLayers;
import org.example.model.IconAsset;
import org.example.model.IconPack;
import org.example.segmentation.SegmentationException;
import org.example.segmentation.SegmentationHints;
import org.example.segmentation.SubjectSegmenter;
import org.example.vector.*;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.awt.Rectangle;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.List;

public final class IconPackController {
    private static final byte[] PNG_SIGNATURE = {
            (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a
    };

    private static final byte[] JPEG_SIGNATURE = {(byte) 0xff, (byte) 0xd8, (byte) 0xff};
    private static final int MAX_SUGGESTED_REGIONS = 6;
    private static final double MIN_SUGGESTED_SHARE = 0.01;
    /** Beyond this many layers a source-color trace is usually anti-aliasing noise, not design. */
    public static final int MAX_COMFORTABLE_LAYERS = 64;

    private final IconPack pack;
    private final SvgVectorizer vectorizer;
    private final SvgExportService exportService;

    public IconPackController(IconPack pack, SvgVectorizer vectorizer, SvgExportService exportService) {
        this.pack = Objects.requireNonNull(pack, "Icon pack is required");
        this.vectorizer = Objects.requireNonNull(vectorizer, "SVG vectorizer is required");
        this.exportService = Objects.requireNonNull(exportService, "SVG export service is required");
    }

    public IconPack pack() {
        return pack;
    }

    public List<ImportIssue> addImages(List<Path> paths) {
        Objects.requireNonNull(paths, "PNG paths are required");
        List<ImportIssue> issues = new ArrayList<>();
        for (Path path : paths) {
            if (path == null) {
                throw new IllegalArgumentException("PNG path list must not contain null entries");
            }
            try {
                String fileName = path.getFileName().toString();
                String lowerName = fileName.toLowerCase(Locale.ROOT);
                int extensionLength;
                boolean png = lowerName.endsWith(".png");
                if (png) {
                    extensionLength = 4;
                } else if (lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")) {
                    extensionLength = lowerName.endsWith(".jpeg") ? 5 : 4;
                } else {
                    throw new IOException("Yalnızca PNG veya JPG dosyaları eklenebilir");
                }
                byte[] signature = png ? PNG_SIGNATURE : JPEG_SIGNATURE;
                try (InputStream input = Files.newInputStream(path)) {
                    if (!Arrays.equals(signature, input.readNBytes(signature.length))) {
                        throw new IOException("Dosya geçerli bir PNG veya JPG görseli değil");
                    }
                }
                BufferedImage image = ImageIO.read(path.toFile());
                if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
                    throw new IOException("Dosya geçerli bir PNG veya JPG görseli değil");
                }
                String name = fileName.substring(0, fileName.length() - extensionLength);
                pack.addIcon(new IconAsset(path, name, image));
            } catch (IOException | SecurityException exception) {
                String reason = exception.getMessage();
                issues.add(new ImportIssue(path,
                        reason == null || reason.isBlank() ? "PNG dosyası okunamadı" : reason));
            }
        }
        return List.copyOf(issues);
    }

    public void setTraceOptions(TraceOptions options) {
        pack.setTraceOptions(Objects.requireNonNull(options, "Trace options are required"));
    }

    /** A nudge toward reducing colors when keeping every source tone produced too many layers. */
    public Optional<String> colorReductionAdvice(int layerCount) {
        if (pack.traceOptions().mode() != TraceOptions.ColorMode.PRESERVE_SOURCE
                || layerCount <= MAX_COMFORTABLE_LAYERS) {
            return Optional.empty();
        }
        return Optional.of("Bu görselde " + layerCount + " ton var. Renk sayısını azaltmak ister misin?");
    }

    public void setExportSettings(SvgExportSettings settings) {
        pack.setExportSettings(Objects.requireNonNull(settings, "Export settings are required"));
    }

    public void setPalettes(List<List<Color>> palettes) {
        Objects.requireNonNull(palettes, "Palettes are required");
        for (List<Color> palette : palettes) {
            if (palette == null || palette.isEmpty()) {
                throw new IllegalArgumentException("Every custom palette must contain at least one color");
            }
        }
        pack.setPalettes(palettes);
    }

    public void addBackgroundRemovalSeed(IconAsset icon, PixelCoordinate seed) {
        addBackgroundRegion(icon, RegionSeed.at(Objects.requireNonNull(seed, "Background seed is required")));
    }

    /** Removes the region grown from a click, as the click's gradient and contiguity modes say. */
    public void addBackgroundRegion(IconAsset icon, RegionSeed region) {
        requireOwnedIcon(icon);
        PixelCoordinate seed = Objects.requireNonNull(region, "Background region is required").point();
        if (seed.x() < 0 || seed.y() < 0
                || seed.x() >= icon.image().getWidth() || seed.y() >= icon.image().getHeight()) {
            throw new IllegalArgumentException("Background seed must be inside the source image");
        }
        pack.addBackgroundRegion(icon, region);
    }

    public List<PixelCoordinate> backgroundRemovalSeeds(IconAsset icon) {
        requireOwnedIcon(icon);
        return pack.backgroundRemovalSeeds(icon);
    }

    public void addEraseStroke(IconAsset icon, EraseStroke stroke) {
        requireOwnedIcon(icon);
        requireStrokeInside(icon, stroke);
        pack.addEraseStroke(icon, stroke);
    }

    public void addProtectStroke(IconAsset icon, EraseStroke stroke) {
        requireOwnedIcon(icon);
        requireStrokeInside(icon, stroke);
        pack.addProtectStroke(icon, stroke);
    }

    /**
     * Runs the segmenter on the icon (slow; call off the UI thread), stores its mask and switches
     * the icon to keeping only the subject. The switch is one undoable edit.
     */
    public void detectSubject(IconAsset icon, SubjectSegmenter segmenter) throws SegmentationException {
        requireOwnedIcon(icon);
        BackgroundSelection selection = pack.backgroundSelection(icon);
        SubjectMask mask = Objects.requireNonNull(segmenter, "Segmenter is required").segment(icon.image(),
                new SegmentationHints(pack.subjectFrame(icon), selection.protectStrokes(), selection.strokes()));
        if (mask.width() != icon.image().getWidth() || mask.height() != icon.image().getHeight()) {
            throw new SegmentationException("Konu maskesi görselle aynı boyutta değil");
        }
        pack.setSubjectMask(icon, mask);
        pack.setKeepSubjectOnly(icon, true);
    }

    /** Frames the subject for segmenters that use it; {@code null} goes back to the default guess. */
    public void setSubjectFrame(IconAsset icon, Rectangle frame) {
        requireOwnedIcon(icon);
        if (frame != null && (frame.isEmpty()
                || !new Rectangle(0, 0, icon.image().getWidth(), icon.image().getHeight()).contains(frame))) {
            throw new IllegalArgumentException("Subject frame must be a non-empty area inside the image");
        }
        pack.setSubjectFrame(icon, frame);
    }

    public Rectangle subjectFrame(IconAsset icon) {
        requireOwnedIcon(icon);
        return pack.subjectFrame(icon);
    }

    public boolean hasSubjectMask(IconAsset icon) {
        requireOwnedIcon(icon);
        return pack.subjectMask(icon) != null;
    }

    public void setKeepSubjectOnly(IconAsset icon, boolean keep) {
        requireOwnedIcon(icon);
        if (keep && pack.subjectMask(icon) == null) {
            throw new IllegalStateException("Run subject detection before keeping only the subject");
        }
        pack.setKeepSubjectOnly(icon, keep);
    }

    public BackgroundSelection backgroundSelection(IconAsset icon) {
        requireOwnedIcon(icon);
        return pack.backgroundSelection(icon);
    }

    public void setBackgroundTolerance(IconAsset icon, int tolerance) {
        requireOwnedIcon(icon);
        if (tolerance < 0 || tolerance > BackgroundSelection.MAX_TOLERANCE) {
            throw new IllegalArgumentException("Tolerance is out of range");
        }
        pack.setBackgroundTolerance(icon, tolerance);
    }

    public void setDefringe(IconAsset icon, boolean defringe) {
        requireOwnedIcon(icon);
        pack.setDefringe(icon, defringe);
    }

    public boolean canUndoBackgroundEdit(IconAsset icon) {
        requireOwnedIcon(icon);
        return pack.canUndoBackgroundEdit(icon);
    }

    public boolean undoBackgroundEdit(IconAsset icon) {
        requireOwnedIcon(icon);
        return pack.undoBackgroundEdit(icon);
    }

    /** The source image after manual and automatic background removal, as the tracer will see it. */
    public BufferedImage processedSource(IconAsset icon) {
        requireOwnedIcon(icon);
        return BackgroundRegionProcessor.removeBorderBackground(BackgroundRegionProcessor.removeSelection(
                icon.image(), pack.backgroundSelection(icon), pack.subjectMask(icon)));
    }

    /** Large regions still visible after the current removals, likely candidates to remove next. */
    public List<RegionSuggestion> suggestedRegions(IconAsset icon) {
        requireOwnedIcon(icon);
        return BackgroundRegionProcessor.suggestRegions(processedSource(icon),
                pack.backgroundSelection(icon).tolerance(), MAX_SUGGESTED_REGIONS, MIN_SUGGESTED_SHARE);
    }

    public void setLayerHidden(IconAsset icon, String color, boolean hidden) {
        requireOwnedIcon(icon);
        Objects.requireNonNull(color, "Layer color is required");
        pack.setLayerHidden(icon, color, hidden);
    }

    public void setLayerColor(IconAsset icon, String color, String replacement) {
        requireOwnedIcon(icon);
        SvgLayers.requireHexColor(color);
        if (replacement != null) {
            SvgLayers.requireHexColor(replacement);
        }
        pack.setLayerColor(icon, color, replacement);
    }

    public Map<String, String> layerColors(IconAsset icon) {
        requireOwnedIcon(icon);
        return pack.layerColors(icon);
    }

    public Set<String> hiddenLayerColors(IconAsset icon) {
        requireOwnedIcon(icon);
        return pack.hiddenLayerColors(icon);
    }

    public List<EraseStroke> eraseStrokes(IconAsset icon) {
        requireOwnedIcon(icon);
        return pack.eraseStrokes(icon);
    }

    public void clearBackgroundRemovalSeeds(IconAsset icon) {
        requireOwnedIcon(icon);
        pack.clearBackgroundRemovalSeeds(icon);
    }

    public List<IconVariant> preview(IconAsset icon) throws VectorizationException, IOException {
        return previewWithLayers(icon).variants();
    }

    /** Preview variants with hidden layers left out, plus every layer of the first variant. */
    public IconPreview previewWithLayers(IconAsset icon) throws VectorizationException, IOException {
        requireOwnedIcon(icon);
        List<IconVariant> traced = traceVariants(icon, pack.backgroundSelection(icon));
        Set<String> hidden = pack.hiddenLayerColors(icon);
        Map<String, String> colors = pack.layerColors(icon);
        List<IconVariant> previews = new ArrayList<>(traced.size());
        for (IconVariant variant : traced) {
            IconVariant visible = withLayerChanges(variant, hidden, colors);
            String svg = exportService.renderSvg(visible, pack.exportSettings());
            previews.add(new IconVariant(variant.iconName(), variant.variantName(), svg));
        }
        List<SvgLayer> layers = traced.isEmpty() ? List.of() : SvgLayers.layers(traced.getFirst().svg());
        return new IconPreview(previews, layers, sourcePreviewSvg(icon));
    }

    /**
     * The untouched source pixels of the traced area, placed on the package canvas the same way
     * the vector is, so the two can be laid on top of each other.
     */
    String sourcePreviewSvg(IconAsset icon) throws IOException {
        Rectangle bounds = ImageTracerVectorizer.artworkBounds(processedSource(icon));
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(icon.image().getSubimage(bounds.x, bounds.y, bounds.width, bounds.height), "png", png);
        SvgExportSettings settings = pack.exportSettings();
        double width = settings.width() * settings.scale();
        double height = settings.height() * settings.scale();
        return String.format(Locale.ROOT, """
                <svg xmlns="http://www.w3.org/2000/svg" width="%d" height="%d" viewBox="0 0 %d %d">\
                <svg x="%s" y="%s" width="%s" height="%s" viewBox="0 0 %d %d" preserveAspectRatio="xMidYMid meet">\
                <image width="%d" height="%d" style="image-rendering:pixelated" \
                href="data:image/png;base64,%s"/></svg></svg>""",
                settings.width(), settings.height(), settings.width(), settings.height(),
                (settings.width() - width) / 2, (settings.height() - height) / 2, width, height,
                bounds.width, bounds.height, bounds.width, bounds.height,
                Base64.getEncoder().encodeToString(png.toByteArray()));
    }

    public void export(Path destination, ExportFormat format) throws IOException, VectorizationException {
        export(destination, format, backgroundRemovalSnapshot());
    }

    public Map<IconAsset, BackgroundSelection> backgroundRemovalSnapshot() {
        Map<IconAsset, BackgroundSelection> snapshot = new IdentityHashMap<>();
        for (IconAsset icon : pack.icons()) {
            snapshot.put(icon, pack.backgroundSelection(icon));
        }
        return Collections.unmodifiableMap(snapshot);
    }

    public void export(
            Path destination,
            ExportFormat format,
            Map<IconAsset, BackgroundSelection> backgroundSelections)
            throws IOException, VectorizationException {
        Objects.requireNonNull(backgroundSelections, "Background selections are required");
        List<IconVariant> variants = new ArrayList<>();
        for (IconAsset icon : pack.icons()) {
            BackgroundSelection selection =
                    backgroundSelections.getOrDefault(icon, BackgroundSelection.EMPTY);
            Set<String> hidden = pack.hiddenLayerColors(icon);
            Map<String, String> colors = pack.layerColors(icon);
            for (IconVariant variant : traceVariants(icon, selection)) {
                variants.add(withLayerChanges(variant, hidden, colors));
            }
        }
        exportService.export(variants, pack.exportSettings(), destination, format);
    }

    private static IconVariant withLayerChanges(
            IconVariant variant, Set<String> hidden, Map<String, String> colors) throws IOException {
        if (hidden.isEmpty() && colors.isEmpty()) {
            return variant;
        }
        return new IconVariant(variant.iconName(), variant.variantName(),
                SvgLayers.withLayerChanges(variant.svg(), hidden, colors));
    }

    private static void requireStrokeInside(IconAsset icon, EraseStroke stroke) {
        Objects.requireNonNull(stroke, "Stroke is required");
        for (PixelCoordinate point : stroke.points()) {
            if (point.x() < 0 || point.y() < 0
                    || point.x() >= icon.image().getWidth() || point.y() >= icon.image().getHeight()) {
                throw new IllegalArgumentException("Erase stroke must be inside the source image");
            }
        }
    }

    private List<IconVariant> traceVariants(IconAsset icon, BackgroundSelection selection)
            throws VectorizationException {
        BufferedImage image = BackgroundRegionProcessor.removeSelection(
                icon.image(), selection, pack.subjectMask(icon));
        List<List<Color>> palettes = pack.palettes();
        if (pack.traceOptions().mode() == TraceOptions.ColorMode.CUSTOM_PALETTE && !palettes.isEmpty()) {
            List<IconVariant> variants = new ArrayList<>(palettes.size());
            for (int index = 0; index < palettes.size(); index++) {
                List<Color> palette = palettes.get(index);
                TraceOptions options = new TraceOptions(TraceOptions.ColorMode.CUSTOM_PALETTE,
                        palette.size(), palette, pack.traceOptions().simplification());
                String svg = vectorizer.vectorize(image, options);
                variants.add(new IconVariant(icon.name(), "palette-" + (index + 1), svg));
            }
            return List.copyOf(variants);
        }

        String variantName = pack.traceOptions().mode() == TraceOptions.ColorMode.CUSTOM_PALETTE
                ? "custom" : "default";
        String svg = vectorizer.vectorize(image, pack.traceOptions());
        return List.of(new IconVariant(icon.name(), variantName, svg));
    }

    private void requireOwnedIcon(IconAsset icon) {
        if (icon == null || pack.icons().stream().noneMatch(owned -> owned == icon)) {
            throw new IllegalArgumentException("Icon does not belong to this package");
        }
    }
}
