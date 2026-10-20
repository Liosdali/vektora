package org.example.ui;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Objects;
import javax.imageio.ImageIO;
import org.example.export.ExportFormat;
import org.example.export.IconVariant;
import org.example.export.SvgExportService;
import org.example.export.SvgExportSettings;
import org.example.model.IconAsset;
import org.example.model.IconPack;
import org.example.vector.BackgroundRegionProcessor;
import org.example.vector.PixelCoordinate;
import org.example.vector.SvgVectorizer;
import org.example.vector.TraceOptions;
import org.example.vector.VectorizationException;

public final class IconPackController {
    private static final byte[] PNG_SIGNATURE = {
            (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a
    };

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
                if (!path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png")) {
                    throw new IOException("Yalnızca PNG dosyaları eklenebilir");
                }
                try (InputStream input = Files.newInputStream(path)) {
                    if (!Arrays.equals(PNG_SIGNATURE, input.readNBytes(PNG_SIGNATURE.length))) {
                        throw new IOException("Dosya geçerli bir PNG görseli değil");
                    }
                }
                BufferedImage image = ImageIO.read(path.toFile());
                if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
                    throw new IOException("Dosya geçerli bir PNG görseli değil");
                }
                String fileName = path.getFileName().toString();
                String name = fileName.substring(0, fileName.length() - 4);
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

    public void clearBackgroundRemovalSeeds(IconAsset icon) {
        requireOwnedIcon(icon);
        pack.clearBackgroundRemovalSeeds(icon);
    }

    public List<IconVariant> preview(IconAsset icon) throws VectorizationException, IOException {
        requireOwnedIcon(icon);
        List<IconVariant> variants = createVariants(icon);
        List<IconVariant> previews = new ArrayList<>(variants.size());
        for (IconVariant variant : variants) {
            String svg = exportService.renderSvg(variant, pack.exportSettings());
            previews.add(new IconVariant(variant.iconName(), variant.variantName(), svg));
        }
        return List.copyOf(previews);
    }

    public void export(Path destination, ExportFormat format) throws IOException, VectorizationException {
        export(destination, format, backgroundRemovalSnapshot());
    }

    public Map<IconAsset, List<PixelCoordinate>> backgroundRemovalSnapshot() {
        Map<IconAsset, List<PixelCoordinate>> snapshot = new IdentityHashMap<>();
        for (IconAsset icon : pack.icons()) {
            snapshot.put(icon, pack.backgroundRemovalSeeds(icon));
        }
        return Collections.unmodifiableMap(snapshot);
    }

    public void export(
            Path destination,
            ExportFormat format,
            Map<IconAsset, List<PixelCoordinate>> backgroundSelections)
            throws IOException, VectorizationException {
        Objects.requireNonNull(backgroundSelections, "Background selections are required");
        List<IconVariant> variants = new ArrayList<>();
        for (IconAsset icon : pack.icons()) {
            List<PixelCoordinate> seeds = backgroundSelections.getOrDefault(icon, List.of());
            variants.addAll(createVariants(icon, seeds));
        }
        exportService.export(variants, pack.exportSettings(), destination, format);
    }

    private List<IconVariant> createVariants(IconAsset icon) throws VectorizationException {
        return createVariants(icon, pack.backgroundRemovalSeeds(icon));
    }

    private List<IconVariant> createVariants(IconAsset icon, List<PixelCoordinate> seeds)
            throws VectorizationException {
        BufferedImage image = BackgroundRegionProcessor.removeSelectedRegions(
                icon.image(), seeds);
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
