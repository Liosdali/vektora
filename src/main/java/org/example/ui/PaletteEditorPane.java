package org.example.ui;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public final class PaletteEditorPane extends VBox {
    private final VBox paletteRows = new VBox(8);
    private final List<List<ColorPicker>> editors = new ArrayList<>();
    private final Consumer<List<List<Color>>> onChange;

    public PaletteEditorPane(Consumer<List<List<Color>>> onChange) {
        this.onChange = onChange;
        setSpacing(10);
        setPadding(new Insets(12));
        setStyle("-fx-background-color: #151b27; -fx-background-radius: 12;");

        Label title = new Label("Renk paletleri");
        title.setStyle("-fx-text-fill: #e5e7eb; -fx-font-size: 13px; -fx-font-weight: 700;");
        Label description = new Label("Birden fazla palet, ayrı SVG varyantları üretir.");
        description.setStyle("-fx-text-fill: #8b95a7; -fx-font-size: 11px;");
        Button addPalette = new Button("+ Palet ekle");
        addPalette.setStyle(buttonStyle());
        addPalette.setOnAction(event -> addPalette());
        getChildren().addAll(title, description, paletteRows, addPalette);
    }

    public List<List<Color>> palettes() {
        return editors.stream()
                .map(colors -> colors.stream().map(ColorPicker::getValue)
                        .map(PaletteEditorPane::toAwtColor).toList())
                .toList();
    }

    private void addPalette() {
        addPalette(List.of(javafx.scene.paint.Color.web("#7667F3"),
                javafx.scene.paint.Color.web("#FFB454")));
    }

    public void addDefaultPalette() {
        addPalette();
    }

    private void addPalette(List<javafx.scene.paint.Color> colors) {
        List<ColorPicker> pickers = new ArrayList<>();
        editors.add(pickers);
        renderPaletteRows();
        VBox row = (VBox) paletteRows.getChildren().get(paletteRows.getChildren().size() - 1);
        FlowPane colorRow = (FlowPane) row.getChildren().get(1);
        for (javafx.scene.paint.Color color : colors) {
            addColorPicker(pickers, color, colorRow);
        }
        notifyChange();
    }

    private void renderPaletteRows() {
        paletteRows.getChildren().clear();
        for (int index = 0; index < editors.size(); index++) {
            int paletteIndex = index;
            Label title = new Label("Palet " + (index + 1));
            title.setStyle("-fx-text-fill: #cbd5e1; -fx-font-size: 12px; -fx-font-weight: 600;");
            Button remove = new Button("Kaldır");
            remove.setStyle("-fx-background-color: transparent; -fx-text-fill: #a78bfa; -fx-font-size: 10px;");
            remove.setOnAction(event -> {
                editors.remove(paletteIndex);
                renderPaletteRows();
                notifyChange();
            });
            HBox header = new HBox(8, title, remove);
            FlowPane colors = new FlowPane(6, 6);
            VBox card = new VBox(8, header, colors);
            card.setPadding(new Insets(10));
            card.setStyle("-fx-background-color: #1d2533; -fx-background-radius: 8;");
            Button addColor = new Button("+ Renk");
            addColor.setStyle("-fx-background-color: transparent; -fx-text-fill: #a5b4fc; -fx-font-size: 10px;");
            addColor.setOnAction(event -> addColorPicker(editors.get(paletteIndex),
                    javafx.scene.paint.Color.WHITE, colors));
            card.getChildren().add(addColor);
            paletteRows.getChildren().add(card);
        }
    }

    private void addColorPicker(
            List<ColorPicker> pickers, javafx.scene.paint.Color color, FlowPane colorRow) {
        ColorPicker picker = new ColorPicker(color);
        picker.setStyle("-fx-color-label-visible: false;");
        picker.setOnAction(event -> notifyChange());
        pickers.add(picker);
        colorRow.getChildren().add(picker);
        if (pickers.size() > 1) {
            Button remove = new Button("×");
            remove.setStyle("-fx-background-color: transparent; -fx-text-fill: #9ca3af;");
            remove.setOnAction(event -> {
                pickers.remove(picker);
                colorRow.getChildren().removeAll(picker, remove);
                notifyChange();
            });
            colorRow.getChildren().add(remove);
        }
    }

    private void notifyChange() {
        onChange.accept(palettes());
    }

    private static Color toAwtColor(javafx.scene.paint.Color color) {
        return new Color((float) color.getRed(), (float) color.getGreen(),
                (float) color.getBlue(), (float) color.getOpacity());
    }

    private static String buttonStyle() {
        return "-fx-background-color: #7667f3; -fx-text-fill: white; -fx-background-radius: 8; "
                + "-fx-font-weight: 700;";
    }
}
