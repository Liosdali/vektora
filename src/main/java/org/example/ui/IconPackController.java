package org.example.ui;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import javax.imageio.ImageIO;
import org.example.export.ExportFormat;
import org.example.export.IconVariant;
import org.example.export.SvgExportService;
import org.example.export.SvgExportSettings;
import org.example.model.IconAsset;
import org.example.model.IconPack;
import org.example.vector.SvgVectorizer;
import org.example.vector.TraceOptions;
import org.example.vector.VectorizationException;

public final class IconPackController {
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

    public List<IconVariant> preview(IconAsset icon) throws VectorizationException, IOException {
        if (!pack.icons().contains(icon)) {
            throw new IllegalArgumentException("Icon does not belong to this package");
        }
        List<IconVariant> variants = createVariants(icon);
        List<IconVariant> previews = new ArrayList<>(variants.size());
        for (IconVariant variant : variants) {
            String svg = exportService.renderSvg(variant, pack.exportSettings());
            previews.add(new IconVariant(variant.iconName(), variant.variantName(), svg));
        }
        return List.copyOf(previews);
    }

    public void export(Path destination, ExportFormat format) throws IOException, VectorizationException {
        List<IconVariant> variants = new ArrayList<>();
        for (IconAsset icon : pack.icons()) {
            variants.addAll(createVariants(icon));
        }
        exportService.export(variants, pack.exportSettings(), destination, format);
    }

    private List<IconVariant> createVariants(IconAsset icon) throws VectorizationException {
        List<List<Color>> palettes = pack.palettes();
        if (pack.traceOptions().mode() == TraceOptions.ColorMode.CUSTOM_PALETTE && !palettes.isEmpty()) {
            List<IconVariant> variants = new ArrayList<>(palettes.size());
            for (int index = 0; index < palettes.size(); index++) {
                List<Color> palette = palettes.get(index);
                TraceOptions options = new TraceOptions(
                        TraceOptions.ColorMode.CUSTOM_PALETTE, palette.size(), palette);
                String svg = vectorizer.vectorize(icon.image(), options);
                variants.add(new IconVariant(icon.name(), "palette-" + (index + 1), svg));
            }
            return List.copyOf(variants);
        }

        String variantName = pack.traceOptions().mode() == TraceOptions.ColorMode.CUSTOM_PALETTE
                ? "custom" : "default";
        String svg = vectorizer.vectorize(icon.image(), pack.traceOptions());
        return List.of(new IconVariant(icon.name(), variantName, svg));
    }
}
