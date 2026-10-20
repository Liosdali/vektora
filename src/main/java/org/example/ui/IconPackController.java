package org.example.ui;

import org.example.export.ExportFormat;
import org.example.export.IconVariant;
import org.example.export.SvgExportService;
import org.example.export.SvgExportSettings;
import org.example.export.SvgLayer;
import org.example.export.SvgLayers;
import org.example.model.IconAsset;
import org.example.model.IconPack;
import org.example.vector.*;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
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
        requireOwnedIcon(icon);
        Objects.requireNonNull(seed, "Background seed is required");
        if (seed.x() < 0 || seed.y() < 0
                || seed.x() >= icon.image().getWidth() || seed.y() >= icon.image().getHeight()) {
            throw new IllegalArgumentException("Background seed must be inside the source image");
        }
        pack.addBackgroundRemovalSeed(icon, seed);
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
        return BackgroundRegionProcessor.removeBorderBackground(
                BackgroundRegionProcessor.removeSelection(icon.image(), pack.backgroundSelection(icon)));
    }

    public void setLayerHidden(IconAsset icon, String color, boolean hidden) {
        requireOwnedIcon(icon);
        Objects.requireNonNull(color, "Layer color is required");
        pack.setLayerHidden(icon, color, hidden);
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
        List<IconVariant> previews = new ArrayList<>(traced.size());
        for (IconVariant variant : traced) {
            IconVariant visible = withoutHiddenLayers(variant, hidden);
            String svg = exportService.renderSvg(visible, pack.exportSettings());
            previews.add(new IconVariant(variant.iconName(), variant.variantName(), svg));
        }
        List<SvgLayer> layers = traced.isEmpty() ? List.of() : SvgLayers.layers(traced.getFirst().svg());
        return new IconPreview(previews, layers);
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
            for (IconVariant variant : traceVariants(icon, selection)) {
                variants.add(withoutHiddenLayers(variant, hidden));
            }
        }
        exportService.export(variants, pack.exportSettings(), destination, format);
    }

    private static IconVariant withoutHiddenLayers(IconVariant variant, Set<String> hidden) throws IOException {
        if (hidden.isEmpty()) {
            return variant;
        }
        return new IconVariant(variant.iconName(), variant.variantName(),
                SvgLayers.withColorsRemoved(variant.svg(), hidden));
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
        BufferedImage image = BackgroundRegionProcessor.removeSelection(icon.image(), selection);
        List<List<Color>> palettes = pack.palettes();
        if (pack.traceOptions().mode() == TraceOptions.ColorMode.CUSTOM_PALETTE && !palettes.isEmpty()) {
            List<IconVariant> variants = new ArrayList<>(palettes.size());
            for (int index = 0; index < palettes.size(); index++) {
                List<Color> palette = palettes.get(index);
                TraceOptions options = new TraceOptions(
                        TraceOptions.ColorMode.CUSTOM_PALETTE, palette.size(), palette);
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
