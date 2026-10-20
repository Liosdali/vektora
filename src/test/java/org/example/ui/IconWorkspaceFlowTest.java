package org.example.ui;

import static org.example.ui.FxTestSupport.call;
import static org.example.ui.FxTestSupport.field;
import static org.example.ui.FxTestSupport.onFx;
import static org.example.ui.FxTestSupport.runOnFx;
import static org.example.ui.FxTestSupport.waitUntil;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.TabPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
import javax.imageio.ImageIO;
import org.example.export.IconVariant;
import org.example.export.SvgExportService;
import org.example.model.IconPack;
import org.example.vector.ImageTracerVectorizer;
import org.example.vector.PixelCoordinate;
import org.example.vector.RegionSeed;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Drives the real workspace on the JavaFX thread through the steps a user takes. */
class IconWorkspaceFlowTest {
    private static final Duration WAIT = Duration.ofSeconds(20);

    private IconPackController controller;
    private IconWorkspace workspace;

    @BeforeEach
    void openWorkspace() throws Exception {
        FxTestSupport.startToolkit();
        controller = new IconPackController(new IconPack(), new ImageTracerVectorizer(), new SvgExportService());
        workspace = onFx(() -> {
            IconWorkspace created = new IconWorkspace(controller);
            new Scene(created, 1200, 800);
            return created;
        });
    }

    @Test
    void addingAnImageShowsItsPreviewAndLayersAndEnablesExport(@TempDir Path directory) throws Exception {
        Path badge = badge(directory.resolve("badge.png"));

        runOnFx(() -> call(workspace, "importPaths", List.of(badge)));

        waitUntil(() -> status().contains("önizlemesi hazır"), WAIT);
        assertEquals(1, controller.pack().icons().size());
        assertTrue(onFx(() -> layerRows().size()) >= 2);
        assertFalse(onFx(() -> ((MenuButton) field(workspace, "exportButton")).isDisabled()));
    }

    @Test
    void clickingTheBackgroundRemovesItAndUndoBringsItBack(@TempDir Path directory) throws Exception {
        runOnFx(() -> call(workspace, "importPaths", List.of(badge(directory.resolve("badge.png")))));
        waitUntil(() -> status().contains("önizlemesi hazır"), WAIT);
        runOnFx(() -> call(workspace, "showBackgroundPicker"));

        runOnFx(() -> {
            Consumer<RegionSeed> click = field(field(workspace, "backgroundPicker"), "onSeedSelected");
            click.accept(RegionSeed.at(new PixelCoordinate(0, 0)));
        });

        waitUntil(() -> status().contains("önizlemesi hazır")
                && !controller.backgroundSelection(controller.pack().icons().getFirst()).regions().isEmpty(), WAIT);
        assertFalse(onFx(() -> ((Button) field(workspace, "undoButton")).isDisabled()));
        runOnFx(() -> call(workspace, "undoBackgroundEdit"));
        waitUntil(() -> controller.backgroundSelection(controller.pack().icons().getFirst()).regions().isEmpty(), WAIT);
        assertFalse(onFx(() -> ((Button) field(workspace, "redoButton")).isDisabled()));
    }

    @Test
    void hidingALayerFromItsRowUpdatesThePackage(@TempDir Path directory) throws Exception {
        runOnFx(() -> call(workspace, "importPaths", List.of(badge(directory.resolve("badge.png")))));
        waitUntil(() -> status().contains("önizlemesi hazır"), WAIT);

        runOnFx(() -> ((ToggleButton) layerRows().getFirst().getChildren().getFirst()).fire());

        waitUntil(() -> status().contains("gizlendi"), WAIT);
        assertEquals(1, controller.hiddenLayerColors(controller.pack().icons().getFirst()).size());
    }

    @Test
    void onlyTheSelectedPreviewTabHoldsAWebView() throws Exception {
        String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 4 4\"><path d=\"M 0 0 L 4 4 Z\"/></svg>";
        IconPreviewPane pane = onFx(IconPreviewPane::new);

        runOnFx(() -> pane.showVariants(List.of(new IconVariant("a", "one", svg), new IconVariant("a", "two", svg),
                new IconVariant("a", "three", svg)), svg));
        TabPane tabs = field(pane, "previews");

        assertTrue(onFx(() -> tabs.getTabs().getFirst().getContent() instanceof WebView));
        assertNull(onFx(() -> tabs.getTabs().get(2).getContent()));
        runOnFx(() -> tabs.getSelectionModel().select(2));
        assertTrue(onFx(() -> tabs.getTabs().get(2).getContent() instanceof WebView));
        assertNull(onFx(() -> tabs.getTabs().getFirst().getContent()));
    }

    private String status() {
        try {
            return ((Label) field(workspace, "status")).getText();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private List<HBox> layerRows() throws ReflectiveOperationException {
        LayerListPane layers = field(workspace, "layerList");
        VBox rows = field(layers, "rows");
        return rows.getChildren().stream().filter(HBox.class::isInstance).map(HBox.class::cast).toList();
    }

    private static Path badge(Path path) throws Exception {
        BufferedImage image = new BufferedImage(40, 40, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 40; y++) {
            for (int x = 0; x < 40; x++) {
                Color color = x > 8 && x < 20 && y > 8 && y < 32 ? Color.RED
                        : x >= 20 && x < 32 && y > 8 && y < 32 ? Color.BLUE : Color.WHITE;
                image.setRGB(x, y, color.getRGB());
            }
        }
        ImageIO.write(image, "png", path.toFile());
        return path;
    }
}
