package org.example.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javafx.scene.control.Button;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.example.export.SvgLayer;

/** One row per traced fill color, in paint order; hiding a layer removes that color from preview and export. */
public final class LayerListPane extends VBox {
    private static final int MAX_ROWS = 40;

    /** What the rows can do to a layer; every layer is identified by its traced color. */
    public interface Actions {
        void toggle(String color, boolean hidden);

        /** {@code replacement} is {@code null} to restore the traced color. */
        void recolor(String color, String replacement);

        /** {@code name} is {@code null} to remove the name. */
        void rename(String color, String name);

        void reorder(List<String> colors);

        void merge(String color, String into);
    }

    public static final Actions NO_ACTIONS = new Actions() {
        @Override
        public void toggle(String color, boolean hidden) {
        }

        @Override
        public void recolor(String color, String replacement) {
        }

        @Override
        public void rename(String color, String name) {
        }

        @Override
        public void reorder(List<String> colors) {
        }

        @Override
        public void merge(String color, String into) {
        }
    };

    private final VBox rows = new VBox(2);
    private final Label summary = new Label();
    private final Label empty = new Label("Önizleme hazır olunca katmanlar burada listelenir.");

    public LayerListPane() {
        setSpacing(8);
        Label title = new Label("Katmanlar");
        title.getStyleClass().add("section-title");
        summary.getStyleClass().add("mono");
        Label hint = new Label("Bir rengi gizlemek onu SVG'den siler; altındaki alan saydam olur. "
                + "Liste çizim sırasıdır: üstteki önce çizilir. Sırayı değiştirmek üst üste binen yerlerin "
                + "görünüşünü değiştirebilir.");
        hint.getStyleClass().add("muted");
        hint.setWrapText(true);
        empty.getStyleClass().add("muted");
        empty.setWrapText(true);
        getChildren().addAll(title, summary, hint, rows);
        showLayers(List.of(), Set.of(), Map.of(), Map.of(), NO_ACTIONS);
    }

    /**
     * @param colors replacement colors keyed by traced color
     * @param names  layer names keyed by traced color
     */
    public void showLayers(List<SvgLayer> layers, Set<String> hidden, Map<String, String> colors,
                           Map<String, String> names, Actions actions) {
        rows.getChildren().clear();
        if (layers.isEmpty()) {
            summary.setText("");
            rows.getChildren().add(empty);
            return;
        }
        int paths = layers.stream().mapToInt(SvgLayer::pathCount).sum();
        summary.setText(layers.size() + " katman · " + paths + " yol");
        long visibleCount = layers.stream().filter(layer -> !hidden.contains(layer.color())).count();
        List<String> order = layers.stream().map(SvgLayer::color).toList();
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
            visibility.setOnAction(event -> actions.toggle(layer.color(), visibility.isSelected()));

            String shown = colors.getOrDefault(layer.color(), layer.color());
            ColorPicker picker = new ColorPicker(Color.web(shown));
            picker.getStyleClass().add("layer-color");
            picker.setAccessibleText("Katman rengi: " + shown.toUpperCase());
            picker.setTooltip(new Tooltip("Katmanın rengini değiştir"));
            picker.setOnAction(event -> actions.recolor(layer.color(), hex(picker.getValue())));
            String name = names.get(layer.color());
            Label label = new Label(name == null ? shown.toUpperCase() : name);
            label.getStyleClass().add(name == null ? "mono" : "layer-name");
            if (name == null) {
                label.setMinWidth(Region.USE_PREF_SIZE);
            } else {
                label.setMinWidth(0);
                label.setTooltip(new Tooltip(name + " · " + shown.toUpperCase()));
            }
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            Label count = new Label(Integer.toString(layer.pathCount()));
            count.getStyleClass().add("mono");
            Tooltip.install(count, new Tooltip(layer.pathCount() + " yol"));

            HBox row = new HBox(visibility, picker, label, spacer);
            if (colors.containsKey(layer.color())) {
                Button reset = new Button("↺");
                reset.getStyleClass().add("ghost");
                reset.setTooltip(new Tooltip("Özgün renge dön (" + layer.color().toUpperCase() + ")"));
                reset.setAccessibleText("Özgün renge dön");
                reset.setOnAction(event -> actions.recolor(layer.color(), null));
                row.getChildren().add(reset);
            }
            row.getChildren().addAll(count, layerMenu(layer, index, order, names, colors, actions));
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

    private MenuButton layerMenu(SvgLayer layer, int index, List<String> order, Map<String, String> names,
                                 Map<String, String> colors, Actions actions) {
        MenuItem rename = new MenuItem("Yeniden adlandır…");
        rename.setOnAction(event -> {
            TextInputDialog dialog = new TextInputDialog(names.getOrDefault(layer.color(), ""));
            dialog.initOwner(getScene() == null ? null : getScene().getWindow());
            dialog.setTitle("Katmanı adlandır");
            dialog.setHeaderText("Ad SVG'de katman kimliği ve Inkscape katman adı olur. Boş bırakırsan ad silinir.");
            dialog.showAndWait().ifPresent(name -> actions.rename(layer.color(), name.isBlank() ? null : name));
        });
        MenuItem up = new MenuItem("Yukarı taşı (önce çiz)");
        up.setDisable(index == 0);
        up.setOnAction(event -> actions.reorder(swapped(order, index, index - 1)));
        MenuItem down = new MenuItem("Aşağı taşı (sonra çiz)");
        down.setDisable(index == order.size() - 1);
        down.setOnAction(event -> actions.reorder(swapped(order, index, index + 1)));
        Menu merge = new Menu("Şununla birleştir");
        for (String other : order) {
            if (!other.equals(layer.color())) {
                MenuItem target = new MenuItem(names.getOrDefault(other,
                        colors.getOrDefault(other, other).toUpperCase()));
                target.setOnAction(event -> actions.merge(layer.color(), other));
                merge.getItems().add(target);
            }
        }
        merge.setDisable(merge.getItems().isEmpty());
        MenuButton menu = new MenuButton("⋯", null, rename, up, down, merge);
        menu.getStyleClass().addAll("ghost", "layer-menu");
        menu.setAccessibleText("Katman işlemleri");
        return menu;
    }

    private static List<String> swapped(List<String> order, int first, int second) {
        List<String> copy = new ArrayList<>(order);
        copy.set(first, order.get(second));
        copy.set(second, order.get(first));
        return copy;
    }

    private static String hex(Color color) {
        return String.format(Locale.ROOT, "#%02x%02x%02x", Math.round(color.getRed() * 255),
                Math.round(color.getGreen() * 255), Math.round(color.getBlue() * 255));
    }
}
