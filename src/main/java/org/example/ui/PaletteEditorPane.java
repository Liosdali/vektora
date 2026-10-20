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
        setPadding(new Insets(4, 0, 0, 0));

        Label title = new Label("Renk paletleri");
        title.getStyleClass().add("section-title");
        Label description = new Label("Birden fazla palet, ayrı SVG varyantları üretir.");
        description.getStyleClass().add("muted");
        description.setWrapText(true);
        Button addPalette = new Button("+ Palet ekle");
        addPalette.getStyleClass().add("secondary");
        addPalette.setMaxWidth(Double.MAX_VALUE);
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
        addPalette(List.of(javafx.scene.paint.Color.web("#1E2A44"),
                javafx.scene.paint.Color.web("#C8F25C")));
    }

    public void addDefaultPalette() {
        addPalette();
    }

    /** Shows the given palettes, e.g. from an opened project, without reporting a change. */
    public void setPalettes(List<List<Color>> palettes) {
        editors.clear();
        for (List<Color> palette : palettes) {
            List<ColorPicker> pickers = new ArrayList<>();
            palette.forEach(color -> pickers.add(picker(toFxColor(color))));
            editors.add(pickers);
        }
        renderPaletteRows();
    }

    private void addPalette(List<javafx.scene.paint.Color> colors) {
        List<ColorPicker> pickers = new ArrayList<>();
        colors.forEach(color -> pickers.add(picker(color)));
        editors.add(pickers);
        renderPaletteRows();
        notifyChange();
    }

    private void renderPaletteRows() {
        paletteRows.getChildren().clear();
        for (int index = 0; index < editors.size(); index++) {
            int paletteIndex = index;
            Label title = new Label("Palet " + (index + 1));
            title.setStyle("-fx-font-weight: 600;");
            Button remove = new Button("Kaldır");
            remove.getStyleClass().setAll("link-button");
            remove.setOnAction(event -> {
                editors.remove(paletteIndex);
                renderPaletteRows();
                notifyChange();
            });
            HBox header = new HBox(8, title, remove);
            FlowPane colors = new FlowPane(6, 6);
            List<ColorPicker> pickers = editors.get(index);
            for (int position = 0; position < pickers.size(); position++) {
                ColorPicker picker = pickers.get(position);
                colors.getChildren().add(picker);
                if (position > 0) {
                    Button removeColor = new Button("×");
                    removeColor.getStyleClass().add("ghost");
                    removeColor.setAccessibleText("Rengi kaldır");
                    removeColor.setOnAction(event -> {
                        pickers.remove(picker);
                        renderPaletteRows();
                        notifyChange();
                    });
                    colors.getChildren().add(removeColor);
                }
            }
            VBox card = new VBox(8, header, colors);
            card.setPadding(new Insets(10));
            card.getStyleClass().add("palette-card");
            Button addColor = new Button("+ Renk");
            addColor.getStyleClass().setAll("link-button");
            addColor.setOnAction(event -> {
                editors.get(paletteIndex).add(picker(javafx.scene.paint.Color.WHITE));
                renderPaletteRows();
                notifyChange();
            });
            card.getChildren().add(addColor);
            paletteRows.getChildren().add(card);
        }
    }

    private ColorPicker picker(javafx.scene.paint.Color color) {
        ColorPicker picker = new ColorPicker(color);
        picker.setStyle("-fx-color-label-visible: false;");
        picker.setOnAction(event -> notifyChange());
        return picker;
    }

    private void notifyChange() {
        onChange.accept(palettes());
    }

    private static javafx.scene.paint.Color toFxColor(Color color) {
        return javafx.scene.paint.Color.rgb(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha() / 255.0);
    }

    private static Color toAwtColor(javafx.scene.paint.Color color) {
        return new Color((float) color.getRed(), (float) color.getGreen(),
                (float) color.getBlue(), (float) color.getOpacity());
    }
}
