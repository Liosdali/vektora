package org.example.project;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.awt.Color;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;
import org.example.export.SvgExportSettings;
import org.example.model.IconAsset;
import org.example.model.IconPack;
import org.example.vector.BackgroundSelection;
import org.example.vector.EraseStroke;
import org.example.vector.PixelCoordinate;
import org.example.vector.RegionSeed;
import org.example.vector.SubjectMask;
import org.example.vector.TraceOptions;

/**
 * Saves and opens {@code .vektora} projects: a ZIP holding {@code project.json} with every edit and
 * setting, each source image as {@code images/N.png} and each subject mask as a grayscale
 * {@code masks/N.png}. Undo history is not saved; an opened project starts with none.
 */
public final class ProjectFile {
    public static final String EXTENSION = ".vektora";
    private static final String FORMAT = "vektora-project";
    private static final int VERSION = 1;
    private static final String MANIFEST = "project.json";
    private static final ObjectMapper JSON = new ObjectMapper();

    private ProjectFile() {
    }

    public static void save(IconPack pack, Path file) throws IOException {
        ObjectNode root = JSON.createObjectNode();
        root.put("format", FORMAT);
        root.put("version", VERSION);
        root.set("traceOptions", traceOptions(pack.traceOptions()));
        SvgExportSettings export = pack.exportSettings();
        root.putObject("exportSettings").put("width", export.width()).put("height", export.height())
                .put("scale", export.scale()).put("mosaic", export.mosaic()).put("compact", export.compact())
                .put("decimals", export.decimals());
        ArrayNode palettes = root.putArray("palettes");
        pack.palettes().forEach(palette -> palettes.add(colors(palette)));

        Map<String, byte[]> entries = new LinkedHashMap<>();
        ArrayNode icons = root.putArray("icons");
        List<IconAsset> assets = pack.icons();
        for (int index = 0; index < assets.size(); index++) {
            IconAsset icon = assets.get(index);
            ObjectNode node = icons.addObject();
            node.put("name", icon.name());
            node.put("source", icon.source().toString());
            String image = "images/" + index + ".png";
            node.put("image", image);
            entries.put(image, png(icon.image()));
            node.set("background", selection(pack.backgroundSelection(icon)));
            ArrayNode hidden = node.putArray("hiddenLayers");
            pack.hiddenLayerColors(icon).stream().sorted().forEach(hidden::add);
            ObjectNode layerColors = node.putObject("layerColors");
            new TreeMap<>(pack.layerColors(icon)).forEach(layerColors::put);
            ObjectNode layerNames = node.putObject("layerNames");
            new TreeMap<>(pack.layerNames(icon)).forEach(layerNames::put);
            ArrayNode layerOrder = node.putArray("layerOrder");
            pack.layerOrder(icon).forEach(layerOrder::add);
            SubjectMask mask = pack.subjectMask(icon);
            if (mask != null) {
                String maskEntry = "masks/" + index + ".png";
                node.put("subjectMask", maskEntry);
                entries.put(maskEntry, png(maskImage(mask)));
            }
            Rectangle frame = pack.subjectFrame(icon);
            if (frame != null) {
                node.putObject("subjectFrame").put("x", frame.x).put("y", frame.y)
                        .put("width", frame.width).put("height", frame.height);
            }
        }

        Path directory = file.toAbsolutePath().getParent();
        Files.createDirectories(directory);
        Path partial = Files.createTempFile(directory, file.getFileName().toString(), ".part");
        try {
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(partial))) {
                zip.putNextEntry(new ZipEntry(MANIFEST));
                zip.write(JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(root));
                zip.closeEntry();
                for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                    zip.putNextEntry(new ZipEntry(entry.getKey()));
                    zip.write(entry.getValue());
                    zip.closeEntry();
                }
            }
            Files.move(partial, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(partial);
        }
    }

    /** Replaces everything in {@code pack} with the project; on failure the pack is left as it was. */
    public static void load(Path file, IconPack pack) throws IOException {
        Map<String, byte[]> entries = readEntries(file);
        byte[] manifest = entries.get(MANIFEST);
        if (manifest == null) {
            throw new IOException("Dosya bir Vektora projesi değil");
        }
        JsonNode root;
        try {
            root = JSON.readTree(manifest);
        } catch (JsonProcessingException exception) {
            throw new IOException("Proje dosyası bozuk", exception);
        }
        if (!FORMAT.equals(root.path("format").asText())) {
            throw new IOException("Dosya bir Vektora projesi değil");
        }
        if (root.path("version").asInt(0) > VERSION) {
            throw new IOException("Bu proje Vektora'nın daha yeni bir sürümüyle kaydedilmiş");
        }

        try {
            List<Runnable> restore = new ArrayList<>();
            TraceOptions traceOptions = root.has("traceOptions")
                    ? traceOptions(root.get("traceOptions")) : TraceOptions.recommended();
            JsonNode export = root.path("exportSettings");
            SvgExportSettings exportSettings = export.isMissingNode() ? new SvgExportSettings(128, 128, 1)
                    : new SvgExportSettings(export.get("width").asInt(), export.get("height").asInt(),
                    export.get("scale").asDouble(), export.path("mosaic").asBoolean(false),
                    export.path("compact").asBoolean(false), export.path("decimals").asInt(-1));
            List<List<Color>> palettes = new ArrayList<>();
            root.path("palettes").forEach(palette -> palettes.add(colors(palette)));
            for (JsonNode node : root.path("icons")) {
                BufferedImage image = image(entries, node.get("image").asText());
                IconAsset icon = new IconAsset(Path.of(node.get("source").asText()), node.get("name").asText(), image);
                BackgroundSelection selection = selection(node.get("background"));
                SubjectMask mask = node.hasNonNull("subjectMask")
                        ? mask(image(entries, node.get("subjectMask").asText()), image) : null;
                JsonNode frameNode = node.path("subjectFrame");
                Rectangle frame = frameNode.isObject() ? new Rectangle(frameNode.get("x").asInt(),
                        frameNode.get("y").asInt(), frameNode.get("width").asInt(), frameNode.get("height").asInt())
                        : null;
                List<String> hidden = new ArrayList<>();
                node.path("hiddenLayers").forEach(color -> hidden.add(color.asText()));
                Map<String, String> layerColors = new LinkedHashMap<>();
                node.path("layerColors").fields().forEachRemaining(
                        entry -> layerColors.put(entry.getKey(), entry.getValue().asText()));
                Map<String, String> layerNames = new LinkedHashMap<>();
                node.path("layerNames").fields().forEachRemaining(
                        entry -> layerNames.put(entry.getKey(), entry.getValue().asText()));
                List<String> layerOrder = new ArrayList<>();
                node.path("layerOrder").forEach(color -> layerOrder.add(color.asText()));
                restore.add(() -> {
                    pack.addIcon(icon);
                    pack.restoreBackgroundSelection(icon, selection);
                    hidden.forEach(color -> pack.setLayerHidden(icon, color, true));
                    layerColors.forEach((color, replacement) -> pack.setLayerColor(icon, color, replacement));
                    layerNames.forEach((color, name) -> pack.setLayerName(icon, color, name));
                    pack.setLayerOrder(icon, layerOrder);
                    if (mask != null) {
                        pack.setSubjectMask(icon, mask);
                    }
                    pack.setSubjectFrame(icon, frame);
                });
            }
            pack.clear();
            pack.setTraceOptions(traceOptions);
            pack.setExportSettings(exportSettings);
            pack.setPalettes(palettes);
            restore.forEach(Runnable::run);
        } catch (RuntimeException exception) {
            throw new IOException("Proje dosyası okunamadı: " + exception.getMessage(), exception);
        }
    }

    private static Map<String, byte[]> readEntries(Path file) throws IOException {
        Map<String, byte[]> entries = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(file))) {
            for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (!entry.isDirectory()) {
                    entries.put(entry.getName(), zip.readAllBytes());
                }
            }
        }
        return entries;
    }

    private static ObjectNode traceOptions(TraceOptions options) {
        ObjectNode node = JSON.createObjectNode();
        node.put("mode", options.mode().name());
        node.put("maxColors", options.maxColors());
        node.set("palette", colors(options.palette()));
        node.put("simplification", options.simplification());
        node.put("fitShapes", options.fitShapes());
        node.put("symmetry", options.symmetry().name());
        return node;
    }

    private static TraceOptions traceOptions(JsonNode node) {
        return new TraceOptions(TraceOptions.ColorMode.valueOf(node.get("mode").asText()),
                node.get("maxColors").asInt(), colors(node.path("palette")), node.path("simplification").asInt(0),
                node.path("fitShapes").asBoolean(false),
                TraceOptions.Symmetry.valueOf(node.path("symmetry").asText("NONE")));
    }

    private static ObjectNode selection(BackgroundSelection selection) {
        ObjectNode node = JSON.createObjectNode();
        ArrayNode regions = node.putArray("regions");
        for (RegionSeed region : selection.regions()) {
            regions.addObject().put("x", region.point().x()).put("y", region.point().y())
                    .put("followGradient", region.followGradient()).put("contiguous", region.contiguous());
        }
        node.set("strokes", strokes(selection.strokes()));
        node.set("protectStrokes", strokes(selection.protectStrokes()));
        node.put("tolerance", selection.tolerance());
        node.put("defringe", selection.defringe());
        node.put("keepSubjectOnly", selection.keepSubjectOnly());
        return node;
    }

    private static BackgroundSelection selection(JsonNode node) {
        List<RegionSeed> regions = new ArrayList<>();
        for (JsonNode region : node.path("regions")) {
            regions.add(new RegionSeed(new PixelCoordinate(region.get("x").asInt(), region.get("y").asInt()),
                    region.path("followGradient").asBoolean(false), region.path("contiguous").asBoolean(true)));
        }
        return new BackgroundSelection(regions, strokes(node.path("strokes")), strokes(node.path("protectStrokes")),
                node.path("tolerance").asInt(BackgroundSelection.DEFAULT_TOLERANCE),
                node.path("defringe").asBoolean(true), node.path("keepSubjectOnly").asBoolean(false));
    }

    private static ArrayNode strokes(List<EraseStroke> strokes) {
        ArrayNode array = JSON.createArrayNode();
        for (EraseStroke stroke : strokes) {
            ObjectNode node = array.addObject();
            node.put("radius", stroke.radius());
            ArrayNode points = node.putArray("points");
            stroke.points().forEach(point -> points.addArray().add(point.x()).add(point.y()));
        }
        return array;
    }

    private static List<EraseStroke> strokes(JsonNode array) {
        List<EraseStroke> strokes = new ArrayList<>();
        for (JsonNode node : array) {
            List<PixelCoordinate> points = new ArrayList<>();
            node.path("points").forEach(point -> points.add(new PixelCoordinate(point.get(0).asInt(), point.get(1).asInt())));
            strokes.add(new EraseStroke(points, node.get("radius").asInt()));
        }
        return strokes;
    }

    /** Colors as {@code #rrggbbaa}, so palette transparency survives. */
    private static ArrayNode colors(List<Color> colors) {
        ArrayNode array = JSON.createArrayNode();
        colors.forEach(color -> array.add(String.format(Locale.ROOT, "#%02x%02x%02x%02x",
                color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha())));
        return array;
    }

    private static List<Color> colors(JsonNode array) {
        List<Color> colors = new ArrayList<>();
        for (JsonNode node : array) {
            String hex = node.asText();
            if (!hex.matches("#[0-9a-fA-F]{8}")) {
                throw new IllegalArgumentException("Geçersiz renk: " + hex);
            }
            long value = Long.parseLong(hex.substring(1), 16);
            colors.add(new Color((int) (value >>> 24), (int) (value >>> 16 & 0xff), (int) (value >>> 8 & 0xff),
                    (int) (value & 0xff)));
        }
        return colors;
    }

    private static byte[] png(BufferedImage image) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", bytes)) {
            throw new IOException("Görsel PNG olarak yazılamadı");
        }
        return bytes.toByteArray();
    }

    private static BufferedImage image(Map<String, byte[]> entries, String name) {
        byte[] bytes = entries.get(name);
        if (bytes == null) {
            throw new IllegalArgumentException("Projede " + name + " eksik");
        }
        try (InputStream input = new ByteArrayInputStream(bytes)) {
            BufferedImage image = ImageIO.read(input);
            if (image == null) {
                throw new IllegalArgumentException(name + " okunamadı");
            }
            return image;
        } catch (IOException exception) {
            throw new IllegalArgumentException(name + " okunamadı", exception);
        }
    }

    private static BufferedImage maskImage(SubjectMask mask) {
        BufferedImage image = new BufferedImage(mask.width(), mask.height(), BufferedImage.TYPE_BYTE_GRAY);
        byte[] values = new byte[mask.width() * mask.height()];
        for (int index = 0; index < values.length; index++) {
            values[index] = (byte) mask.foreground(index % mask.width(), index / mask.width());
        }
        image.getRaster().setDataElements(0, 0, mask.width(), mask.height(), values);
        return image;
    }

    private static SubjectMask mask(BufferedImage stored, BufferedImage source) {
        if (stored.getWidth() != source.getWidth() || stored.getHeight() != source.getHeight()
                || stored.getRaster().getNumBands() != 1) {
            throw new IllegalArgumentException("Konu maskesi görselle uyuşmuyor");
        }
        byte[] values = new byte[stored.getWidth() * stored.getHeight()];
        stored.getRaster().getDataElements(0, 0, stored.getWidth(), stored.getHeight(), values);
        return new SubjectMask(stored.getWidth(), stored.getHeight(), values);
    }
}
