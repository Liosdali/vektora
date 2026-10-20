package org.example.ui;

import org.example.export.ExportFormat;
import org.example.export.IconVariant;
import org.example.export.LayerEdits;
import org.example.export.SvgExportService;
import org.example.export.SvgExportSettings;
import org.example.export.SvgLayer;
import org.example.export.SvgLayers;
import org.example.model.IconAsset;
import org.example.model.IconPack;
import org.example.project.ProjectFile;
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
    /** Previews of images larger than this on either side are traced from a smaller copy. */
    public static final int LARGE_IMAGE_SIDE = 2000;
    public static final int MAX_PREVIEW_SIDE = 1000;
    /** Traced layers of similar images this close (ΔE) count as the same layer when copying edits. */
    private static final double SAME_LAYER_DISTANCE = 10;

    private final IconPack pack;
    private final SvgVectorizer vectorizer;
    private final SvgExportService exportService;
    /** Recent traces, so toggling layers or reopening a preview does not trace again. */
    private final Map<TraceKey, List<IconVariant>> traces = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<TraceKey, List<IconVariant>> eldest) {
            return size() > 32;
        }
    };

    public IconPackController(IconPack pack, SvgVectorizer vectorizer, SvgExportService exportService) {
        this.pack = Objects.requireNonNull(pack, "Icon pack is required");
        this.vectorizer = Objects.requireNonNull(vectorizer, "SVG vectorizer is required");
        this.exportService = Objects.requireNonNull(exportService, "SVG export service is required");
    }

    public IconPack pack() {
        return pack;
    }

    public void saveProject(Path file) throws IOException {
        ProjectFile.save(pack, Objects.requireNonNull(file, "Project file is required"));
    }

    /** Replaces the package with the saved project; a failed read leaves it untouched. */
    public void openProject(Path file) throws IOException {
        ProjectFile.load(Objects.requireNonNull(file, "Project file is required"), pack);
        forgetTraces();
    }

    /** Starts over with an empty package and default settings. */
    public void newProject() {
        pack.clear();
        forgetTraces();
    }

    /** Cached traces hold their icons, and so the full images, until they are dropped. */
    private void forgetTraces() {
        synchronized (traces) {
            traces.clear();
        }
    }

    /** Adds an image that has no file of its own, such as one pasted from the clipboard. */
    public IconAsset addImage(String name, BufferedImage image) {
        Objects.requireNonNull(image, "Image is required");
        IconAsset icon = new IconAsset(Path.of(name + ".png"), name, image);
        pack.addIcon(icon);
        return icon;
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

    /**
     * Removes, in every icon, the regions along the image edge close to {@code rgb}; each icon
     * gets one undoable edit. Returns how many icons changed.
     */
    public int applyColorRule(int rgb, int tolerance) {
        if (tolerance < 0 || tolerance > BackgroundSelection.MAX_TOLERANCE) {
            throw new IllegalArgumentException("Tolerance is out of range");
        }
        int changed = 0;
        for (IconAsset icon : pack.icons()) {
            List<RegionSeed> regions = BackgroundRegionProcessor.edgeRegionSeeds(icon.image(), rgb, tolerance)
                    .stream().map(RegionSeed::at).toList();
            if (!regions.isEmpty()) {
                pack.addBackgroundRegions(icon, regions);
                changed++;
            }
        }
        return changed;
    }

    /**
     * Gives the targets the source's tolerance and halo cleanup, and its layer edits (hidden,
     * recolored, named, ordered) on each target's closest traced layer. Traces every icon, so call
     * it off the UI thread.
     */
    public void copySettings(IconAsset source, List<IconAsset> targets) throws VectorizationException, IOException {
        requireOwnedIcon(source);
        BackgroundSelection settings = pack.backgroundSelection(source);
        List<String> sourceLayers = tracedLayerColors(source);
        for (IconAsset target : targets) {
            requireOwnedIcon(target);
            if (target == source) {
                continue;
            }
            pack.setBackgroundTolerance(target, settings.tolerance());
            pack.setDefringe(target, settings.defringe());
            Map<String, String> sameLayer = matchLayers(sourceLayers, tracedLayerColors(target));
            for (String hidden : pack.hiddenLayerColors(source)) {
                if (sameLayer.containsKey(hidden)) {
                    pack.setLayerHidden(target, sameLayer.get(hidden), true);
                }
            }
            pack.layerColors(source).forEach((color, replacement) -> {
                if (sameLayer.containsKey(color)) {
                    // A merge points at a traced color; follow it to the target's version of that layer.
                    pack.setLayerColor(target, sameLayer.get(color), sameLayer.getOrDefault(replacement, replacement));
                }
            });
            pack.layerNames(source).forEach((color, name) -> {
                if (sameLayer.containsKey(color)) {
                    pack.setLayerName(target, sameLayer.get(color), name);
                }
            });
            List<String> order = pack.layerOrder(source).stream()
                    .filter(sameLayer::containsKey).map(sameLayer::get).distinct().toList();
            if (!order.isEmpty()) {
                pack.setLayerOrder(target, order);
            }
        }
    }

    public IconStatus status(IconAsset icon) {
        requireOwnedIcon(icon);
        boolean layersEdited = !pack.hiddenLayerColors(icon).isEmpty() || !pack.layerColors(icon).isEmpty()
                || !pack.layerNames(icon).isEmpty() || !pack.layerOrder(icon).isEmpty();
        if (layersEdited) {
            return IconStatus.LAYERS_EDITED;
        }
        return pack.backgroundSelection(icon).hasEdits() ? IconStatus.BACKGROUND_SEPARATED : IconStatus.UNTOUCHED;
    }

    private List<String> tracedLayerColors(IconAsset icon) throws VectorizationException, IOException {
        List<IconVariant> traced = traceVariants(icon, pack.backgroundSelection(icon), true);
        return traced.isEmpty() ? List.of()
                : SvgLayers.layers(traced.getFirst().svg()).stream().map(SvgLayer::color).toList();
    }

    /** Each source layer's closest target layer, when one is close enough to be the same. */
    private static Map<String, String> matchLayers(List<String> source, List<String> target) {
        Map<String, String> matches = new HashMap<>();
        for (String color : source) {
            int rgb = Integer.parseInt(color.substring(1), 16);
            target.stream()
                    .min(Comparator.comparingDouble(other -> LabColor.deltaE(rgb, Integer.parseInt(other.substring(1), 16))))
                    .filter(other -> LabColor.deltaE(rgb, Integer.parseInt(other.substring(1), 16)) <= SAME_LAYER_DISTANCE)
                    .ifPresent(other -> matches.put(color, other));
        }
        return matches;
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

    public boolean canRedoBackgroundEdit(IconAsset icon) {
        requireOwnedIcon(icon);
        return pack.canRedoBackgroundEdit(icon);
    }

    public boolean redoBackgroundEdit(IconAsset icon) {
        requireOwnedIcon(icon);
        return pack.redoBackgroundEdit(icon);
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

    /** Names the layer traced as {@code color}; a blank name removes it. */
    public void setLayerName(IconAsset icon, String color, String name) {
        requireOwnedIcon(icon);
        SvgLayers.requireHexColor(color);
        pack.setLayerName(icon, color, name == null || name.isBlank() ? null : name.strip());
    }

    public Map<String, String> layerNames(IconAsset icon) {
        requireOwnedIcon(icon);
        return pack.layerNames(icon);
    }

    /** Paint order of the traced layers, first painted first; an empty list keeps the traced order. */
    public void setLayerOrder(IconAsset icon, List<String> colors) {
        requireOwnedIcon(icon);
        colors.forEach(SvgLayers::requireHexColor);
        if (new HashSet<>(colors).size() != colors.size()) {
            throw new IllegalArgumentException("A layer can appear only once in the order");
        }
        pack.setLayerOrder(icon, colors);
    }

    public List<String> layerOrder(IconAsset icon) {
        requireOwnedIcon(icon);
        return pack.layerOrder(icon);
    }

    /** Paints the layer with the other layer's current color, so both export as one layer. */
    public void mergeLayer(IconAsset icon, String color, String into) {
        requireOwnedIcon(icon);
        SvgLayers.requireHexColor(into);
        if (color.equals(into)) {
            throw new IllegalArgumentException("A layer cannot be merged into itself");
        }
        // Keyed to the other layer's traced color, so it also follows that layer's recoloring.
        setLayerColor(icon, color, into);
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
        List<IconVariant> traced = traceVariants(icon, pack.backgroundSelection(icon), true);
        LayerEdits edits = layerEdits(icon);
        List<IconVariant> previews = new ArrayList<>(traced.size());
        for (IconVariant variant : traced) {
            IconVariant visible = withLayerChanges(variant, edits);
            String svg = exportService.renderSvg(visible, pack.exportSettings());
            previews.add(new IconVariant(variant.iconName(), variant.variantName(), svg));
        }
        List<SvgLayer> layers = new ArrayList<>(
                traced.isEmpty() ? List.of() : SvgLayers.layers(traced.getFirst().svg()));
        List<String> order = pack.layerOrder(icon);
        // Listed layers first in the chosen order, the rest after them in traced order.
        layers.sort(Comparator.comparingInt(layer -> order.contains(layer.color())
                ? order.indexOf(layer.color()) : order.size()));
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
            LayerEdits edits = layerEdits(icon);
            for (IconVariant variant : traceVariants(icon, selection)) {
                variants.add(withLayerChanges(variant, edits));
            }
        }
        exportService.export(variants, pack.exportSettings(), destination, format);
    }

    private LayerEdits layerEdits(IconAsset icon) {
        return new LayerEdits(pack.hiddenLayerColors(icon), pack.layerColors(icon), pack.layerNames(icon),
                pack.layerOrder(icon));
    }

    private static IconVariant withLayerChanges(IconVariant variant, LayerEdits edits) throws IOException {
        if (edits.isEmpty()) {
            return variant;
        }
        return new IconVariant(variant.iconName(), variant.variantName(),
                SvgLayers.withLayerEdits(variant.svg(), edits));
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
        return traceVariants(icon, selection, false);
    }

    /**
     * Traces the icon, or returns the result of an earlier trace with the same inputs. Previews
     * of large images trace a smaller copy, colored from the full image so layers keep their keys.
     */
    private List<IconVariant> traceVariants(IconAsset icon, BackgroundSelection selection, boolean preview)
            throws VectorizationException {
        TraceOptions traceOptions = pack.traceOptions();
        List<List<Color>> palettes = pack.palettes();
        SubjectMask mask = pack.subjectMask(icon);
        TraceKey key = new TraceKey(icon, selection, traceOptions, palettes, mask, preview);
        synchronized (traces) {
            List<IconVariant> cached = traces.get(key);
            if (cached != null) {
                return cached;
            }
        }
        BufferedImage full = BackgroundRegionProcessor.removeSelection(icon.image(), selection, mask);
        BufferedImage image = preview ? previewCopy(full) : full;
        List<IconVariant> variants;
        if (traceOptions.mode() == TraceOptions.ColorMode.CUSTOM_PALETTE && !palettes.isEmpty()) {
            List<IconVariant> traced = new ArrayList<>(palettes.size());
            for (int index = 0; index < palettes.size(); index++) {
                List<Color> palette = palettes.get(index);
                TraceOptions options = new TraceOptions(TraceOptions.ColorMode.CUSTOM_PALETTE, palette.size(),
                        palette, traceOptions.simplification(), traceOptions.fitShapes(), traceOptions.symmetry());
                String svg = vectorizer.vectorize(image, options, full);
                traced.add(new IconVariant(icon.name(), "palette-" + (index + 1), svg));
            }
            variants = List.copyOf(traced);
        } else {
            String variantName = traceOptions.mode() == TraceOptions.ColorMode.CUSTOM_PALETTE ? "custom" : "default";
            variants = List.of(new IconVariant(icon.name(), variantName, vectorizer.vectorize(image, traceOptions, full)));
        }
        synchronized (traces) {
            traces.put(key, variants);
        }
        return variants;
    }

    /** Everything a trace depends on; icons and masks compare by identity. */
    private record TraceKey(IconAsset icon, BackgroundSelection selection, TraceOptions options,
                            List<List<Color>> palettes, SubjectMask mask, boolean preview) {
        @Override
        public boolean equals(Object other) {
            return other instanceof TraceKey key && key.icon == icon && key.mask == mask
                    && key.preview == preview && key.selection.equals(selection)
                    && key.options.equals(options) && key.palettes.equals(palettes);
        }

        @Override
        public int hashCode() {
            return Objects.hash(System.identityHashCode(icon), selection, options, palettes,
                    System.identityHashCode(mask), preview);
        }
    }

    /** A copy at most {@link #MAX_PREVIEW_SIDE} wide and high when the image is large, else the image. */
    private static BufferedImage previewCopy(BufferedImage image) {
        int side = Math.max(image.getWidth(), image.getHeight());
        if (side <= LARGE_IMAGE_SIDE) {
            return image;
        }
        double scale = (double) MAX_PREVIEW_SIDE / side;
        int width = Math.max(1, (int) Math.round(image.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(image.getHeight() * scale));
        BufferedImage copy = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = copy.createGraphics();
        try {
            graphics.drawImage(image.getScaledInstance(width, height, Image.SCALE_AREA_AVERAGING), 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return copy;
    }

    private void requireOwnedIcon(IconAsset icon) {
        if (icon == null || pack.icons().stream().noneMatch(owned -> owned == icon)) {
            throw new IllegalArgumentException("Icon does not belong to this package");
        }
    }
}
