package org.example.ui;

import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.example.export.SvgLayer;

/** One row per traced fill color; hiding a layer removes that color from preview and export. */
public final class LayerListPane extends VBox {
    private static final int MAX_ROWS = 40;

    private final VBox rows = new VBox(2);
    private final Label summary = new Label();
    private final Label empty = new Label("Önizleme hazır olunca katmanlar burada listelenir.");

    public LayerListPane() {
        setSpacing(8);
        Label title = new Label("Katmanlar");
        title.getStyleClass().add("section-title");
        summary.getStyleClass().add("mono");
        Label hint = new Label("Bir rengi gizlemek onu SVG'den siler; altındaki alan saydam olur.");
        hint.getStyleClass().add("muted");
        hint.setWrapText(true);
        empty.getStyleClass().add("muted");
        empty.setWrapText(true);
        getChildren().addAll(title, summary, hint, rows);
        showLayers(List.of(), Set.of(), (color, hidden) -> { });
    }

    public void showLayers(List<SvgLayer> layers, Set<String> hidden, BiConsumer<String, Boolean> onToggle) {
        rows.getChildren().clear();
        if (layers.isEmpty()) {
            summary.setText("");
            rows.getChildren().add(empty);
            return;
        }
        int paths = layers.stream().mapToInt(SvgLayer::pathCount).sum();
        summary.setText(layers.size() + " katman · " + paths + " yol");
        long visibleCount = layers.stream().filter(layer -> !hidden.contains(layer.color())).count();
        for (int index = 0; index < Math.min(layers.size(), MAX_ROWS); index++) {
            SvgLayer layer = layers.get(index);
            boolean isHidden = hidden.contains(layer.color());
            ToggleButton visibility = new ToggleButton(isHidden ? "GİZLİ" : "AÇIK");
            visibility.getStyleClass().add("eye-toggle");
            visibility.setSelected(isHidden);
            visibility.setMinWidth(54);
            visibility.setAccessibleText((isHidden ? "Göster: " : "Gizle: ") + layer.color());
            // Hiding every layer would leave nothing to export.
            visibility.setDisable(!isHidden && visibleCount == 1);
            visibility.setOnAction(event -> onToggle.accept(layer.color(), visibility.isSelected()));

            Region swatch = new Region();
            swatch.getStyleClass().add("layer-swatch");
            swatch.setStyle("-fx-background-color: " + layer.color() + ";");
            Label color = new Label(layer.color().toUpperCase());
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            Label count = new Label(Integer.toString(layer.pathCount()));
            count.getStyleClass().add("mono");
            Tooltip.install(count, new Tooltip(layer.pathCount() + " yol"));

            HBox row = new HBox(visibility, swatch, color, spacer, count);
            row.getStyleClass().add("layer-row");
            if (isHidden) {
                row.getStyleClass().add("hidden-layer");
            }
            rows.getChildren().add(row);
        }
        if (layers.size() > MAX_ROWS) {
            Label more = new Label("+" + (layers.size() - MAX_ROWS) + " katman daha. Kenar yumuşatması çok "
                    + "sayıda ara ton üretmiş; \"Renk sayısını azalt\" ile birleştirebilirsin.");
            more.getStyleClass().add("muted");
            more.setWrapText(true);
            rows.getChildren().add(more);
        }
    }
}
