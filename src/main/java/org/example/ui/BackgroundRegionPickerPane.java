package org.example.ui;

import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import org.example.model.IconAsset;
import org.example.vector.BackgroundSelection;
import org.example.vector.EraseStroke;
import org.example.vector.PixelCoordinate;
import org.example.vector.RegionSuggestion;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

public final class BackgroundRegionPickerPane extends BorderPane {
    private static final Color SURFACE = Color.web("#151619");
    private static final Color CHECKER_LIGHT = Color.web("#24262b");
    private static final Color CHECKER_DARK = Color.web("#1b1d21");
    private static final Color ACCENT = Color.web("#c8f25c");
    private static final Color PROTECT = Color.web("#38bdf8");
    private static final int REMOVED_OVERLAY = 0xb4ef4444;
    private static final double MAX_ZOOM = 16;

    private final Canvas canvas = new Canvas();
    private final StackPane imageSurface = new StackPane(canvas);
    private final ToggleGroup toolGroup = new ToggleGroup();
    private final ToggleButton regionTool = new ToggleButton("Bölge seç");
    private final ToggleButton eraseTool = new ToggleButton("Fırçayla sil");
    private final ToggleButton protectTool = new ToggleButton("Fırçayla koru");
    private final ToggleGroup viewGroup = new ToggleGroup();
    private final ToggleButton maskView = new ToggleButton("Maske");
    private final ToggleButton resultView = new ToggleButton("Sonuç");
    private final Slider brushSize = new Slider(1, 20, 4);
    private final Label hint = new Label();
    private final FlowPane suggestionChips = new FlowPane(6, 6);
    private final List<PixelCoordinate> activeStroke = new ArrayList<>();
    private Image sourceImage;
    private Image resultImage;
    private Image removedOverlay;
    private BackgroundSelection selection = BackgroundSelection.EMPTY;
    private Consumer<PixelCoordinate> onSeedSelected = ignored -> { };
    private Consumer<EraseStroke> onStrokeDrawn = ignored -> { };
    private Consumer<EraseStroke> onProtectDrawn = ignored -> { };
    private double imageX;
    private double imageY;
    private double displayedWidth;
    private double displayedHeight;
    private double zoom = 1;
    private double panX;
    private double panY;
    private double panStartX;
    private double panStartY;

    public BackgroundRegionPickerPane() {
        getStyleClass().add("canvas-panel");
        Label title = new Label("Arka planı ayır");
        title.getStyleClass().add("canvas-title");
        hint.getStyleClass().add("muted");
        hint.setWrapText(true);

        HBox tools = segmented(toolGroup, regionTool, eraseTool, protectTool);
        toolGroup.selectedToggleProperty().addListener((observable, previous, selected) -> updateHint());
        HBox views = segmented(viewGroup, maskView, resultView);
        viewGroup.selectedToggleProperty().addListener((observable, previous, selected) -> draw());

        Label brushLabel = new Label("Fırça");
        brushLabel.getStyleClass().add("muted");
        brushSize.setPrefWidth(120);
        brushSize.disableProperty().bind(regionTool.selectedProperty());
        Button fit = new Button("Sığdır");
        fit.getStyleClass().add("ghost");
        fit.setOnAction(event -> resetView());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox toolbar = new HBox(10, tools, brushLabel, brushSize, spacer, fit, views);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        Label suggestionsTitle = new Label("Önerilen bölgeler");
        suggestionsTitle.getStyleClass().add("muted");
        suggestionsTitle.setMinWidth(Region.USE_PREF_SIZE);
        HBox suggestions = new HBox(10, suggestionsTitle, suggestionChips);
        suggestions.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(suggestionChips, Priority.ALWAYS);

        VBox header = new VBox(8, title, hint, toolbar, suggestions);
        header.setStyle("-fx-padding: 0 0 12 0;");
        setTop(header);
        updateHint();

        imageSurface.setMinSize(100, 100);
        canvas.widthProperty().bind(imageSurface.widthProperty());
        canvas.heightProperty().bind(imageSurface.heightProperty());
        canvas.widthProperty().addListener((observable, oldValue, newValue) -> draw());
        canvas.heightProperty().addListener((observable, oldValue, newValue) -> draw());
        canvas.setFocusTraversable(true);
        canvas.setAccessibleText("Arka plan seçimi. Tıkla veya silinecek ya da korunacak alanı boya.");
        configureMouse();
        setCenter(imageSurface);
    }

    private static HBox segmented(ToggleGroup group, ToggleButton... buttons) {
        for (ToggleButton button : buttons) {
            button.setToggleGroup(group);
        }
        buttons[0].setSelected(true);
        // Keep one option selected: clicking the active option must not clear the group.
        group.selectedToggleProperty().addListener((observable, previous, selected) -> {
            if (selected == null && previous != null) {
                previous.setSelected(true);
            }
        });
        HBox box = new HBox(buttons);
        box.getStyleClass().add("segmented");
        return box;
    }

    private void configureMouse() {
        canvas.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && regionTool.isSelected()
                    && event.isStillSincePress()) {
                coordinateAt(event.getX(), event.getY()).ifPresent(onSeedSelected);
            }
        });
        canvas.setOnMousePressed(event -> {
            canvas.requestFocus();
            if (event.getButton() != MouseButton.PRIMARY) {
                panStartX = event.getX() - panX;
                panStartY = event.getY() - panY;
            } else if (isBrushTool()) {
                activeStroke.clear();
                coordinateAt(event.getX(), event.getY()).ifPresent(activeStroke::add);
                draw();
            }
        });
        canvas.setOnMouseDragged(event -> {
            if (event.getButton() != MouseButton.PRIMARY) {
                panX = event.getX() - panStartX;
                panY = event.getY() - panStartY;
                draw();
            } else if (isBrushTool()) {
                coordinateAt(event.getX(), event.getY()).ifPresent(point -> {
                    if (activeStroke.isEmpty() || !activeStroke.getLast().equals(point)) {
                        activeStroke.add(point);
                        draw();
                    }
                });
            }
        });
        canvas.setOnMouseReleased(event -> {
            if (event.getButton() == MouseButton.PRIMARY && isBrushTool() && !activeStroke.isEmpty()) {
                EraseStroke stroke = new EraseStroke(activeStroke, brushRadius());
                activeStroke.clear();
                (protectTool.isSelected() ? onProtectDrawn : onStrokeDrawn).accept(stroke);
            }
        });
        canvas.setOnScroll(event -> {
            if (sourceImage == null || event.getDeltaY() == 0) {
                return;
            }
            double nextZoom = Math.max(1, Math.min(MAX_ZOOM, zoom * (event.getDeltaY() > 0 ? 1.2 : 1 / 1.2)));
            double applied = nextZoom / zoom;
            // Keep the pixel under the pointer in place while zooming.
            double centerX = canvas.getWidth() / 2 + panX;
            double centerY = canvas.getHeight() / 2 + panY;
            panX += (event.getX() - centerX) * (1 - applied);
            panY += (event.getY() - centerY) * (1 - applied);
            zoom = nextZoom;
            if (zoom == 1) {
                panX = 0;
                panY = 0;
            }
            draw();
        });
    }

    private boolean isBrushTool() {
        return eraseTool.isSelected() || protectTool.isSelected();
    }

    private void updateHint() {
        hint.setText(regionTool.isSelected()
                ? "Silmek istediğin bağlı renk alanına tıkla; kırmızı alanlar silinecek. "
                        + "Tekerlek: yakınlaştır · sağ tık sürükle: kaydır."
                : eraseTool.isSelected()
                ? "Silinecek kısmı boya; boyadığın alan renginden bağımsız olarak saydam yapılır."
                : "Yanlışlıkla silinen yeri boya; korunan alan hiçbir araçla silinmez.");
    }

    private void resetView() {
        zoom = 1;
        panX = 0;
        panY = 0;
        draw();
    }

    private Optional<PixelCoordinate> coordinateAt(double viewX, double viewY) {
        return sourceCoordinate(
                viewX, viewY, imageX, imageY, displayedWidth, displayedHeight,
                sourceImage == null ? 0 : (int) sourceImage.getWidth(),
                sourceImage == null ? 0 : (int) sourceImage.getHeight());
    }

    private int brushRadius() {
        return (int) Math.max(1, Math.round(brushSize.getValue()));
    }

    public void setSource(
            IconAsset icon, BackgroundSelection currentSelection, BufferedImage processed,
            List<RegionSuggestion> suggestions,
            Consumer<PixelCoordinate> seedSelected, Consumer<EraseStroke> strokeDrawn,
            Consumer<EraseStroke> protectDrawn) {
        Objects.requireNonNull(icon, "Selected icon is required");
        Objects.requireNonNull(processed, "Processed image is required");
        selection = Objects.requireNonNull(currentSelection, "Background selection is required");
        onSeedSelected = Objects.requireNonNull(seedSelected, "Seed callback is required");
        onStrokeDrawn = Objects.requireNonNull(strokeDrawn, "Stroke callback is required");
        onProtectDrawn = Objects.requireNonNull(protectDrawn, "Protect callback is required");
        activeStroke.clear();
        int largestBrush = Math.max(5, Math.min(icon.image().getWidth(), icon.image().getHeight()) / 4);
        brushSize.setMax(largestBrush);
        if (brushSize.getValue() > largestBrush) {
            brushSize.setValue(largestBrush);
        }
        boolean sameSize = sourceImage != null && sourceImage.getWidth() == icon.image().getWidth()
                && sourceImage.getHeight() == icon.image().getHeight();
        if (!sameSize) {
            zoom = 1;
            panX = 0;
            panY = 0;
        }
        sourceImage = FxImages.toFxImage(icon.image());
        resultImage = FxImages.toFxImage(processed);
        removedOverlay = removedOverlay(icon.image(), processed);
        showSuggestions(Objects.requireNonNull(suggestions, "Suggestions are required"));
        draw();
    }

    private void showSuggestions(List<RegionSuggestion> suggestions) {
        suggestionChips.getChildren().clear();
        if (suggestions.isEmpty()) {
            Label none = new Label("Belirgin bir bölge kalmadı");
            none.getStyleClass().add("muted");
            suggestionChips.getChildren().add(none);
            return;
        }
        for (RegionSuggestion suggestion : suggestions) {
            String color = String.format("#%06X", suggestion.rgb());
            long percent = Math.max(1, Math.round(suggestion.share() * 100));
            Region swatch = new Region();
            swatch.getStyleClass().add("layer-swatch");
            swatch.setStyle("-fx-background-color: " + color + ";");
            Button chip = new Button("%" + percent + (suggestion.touchesBorder() ? " · kenar" : ""), swatch);
            chip.getStyleClass().add("chip");
            chip.setTooltip(new Tooltip("Tıkla: bu bölgeyi sil (" + color + ", "
                    + suggestion.pixelCount() + " piksel)"));
            chip.setAccessibleText("Bölgeyi sil: " + color + ", görünür alanın yüzde " + percent);
            chip.setOnAction(event -> onSeedSelected.accept(suggestion.seed()));
            suggestionChips.getChildren().add(chip);
        }
    }

    static Optional<PixelCoordinate> sourceCoordinate(
            double viewX, double viewY, double imageX, double imageY,
            double displayedWidth, double displayedHeight, int sourceWidth, int sourceHeight) {
        if (displayedWidth <= 0 || displayedHeight <= 0 || sourceWidth <= 0 || sourceHeight <= 0
                || viewX < imageX || viewY < imageY
                || viewX >= imageX + displayedWidth || viewY >= imageY + displayedHeight) {
            return Optional.empty();
        }
        int x = (int) ((viewX - imageX) * sourceWidth / displayedWidth);
        int y = (int) ((viewY - imageY) * sourceHeight / displayedHeight);
        return Optional.of(new PixelCoordinate(
                Math.min(sourceWidth - 1, x), Math.min(sourceHeight - 1, y)));
    }

    /** Red wherever an originally visible pixel ends up transparent. */
    private static Image removedOverlay(BufferedImage source, BufferedImage processed) {
        int width = source.getWidth();
        int height = source.getHeight();
        int[] before = source.getRGB(0, 0, width, height, null, 0, width);
        int[] after = processed.getRGB(0, 0, width, height, null, 0, width);
        int[] overlay = new int[before.length];
        for (int index = 0; index < overlay.length; index++) {
            if ((before[index] >>> 24) != 0 && (after[index] >>> 24) == 0) {
                overlay[index] = REMOVED_OVERLAY;
            }
        }
        return FxImages.toFxImage(overlay, width, height);
    }

    private void draw() {
        double canvasWidth = canvas.getWidth();
        double canvasHeight = canvas.getHeight();
        if (canvasWidth <= 0 || canvasHeight <= 0) {
            return;
        }

        GraphicsContext graphics = canvas.getGraphicsContext2D();
        graphics.setImageSmoothing(false);
        graphics.setFill(SURFACE);
        graphics.fillRect(0, 0, canvasWidth, canvasHeight);
        if (sourceImage == null) {
            return;
        }

        double scale = Math.min(
                canvasWidth / sourceImage.getWidth(), canvasHeight / sourceImage.getHeight()) * zoom;
        displayedWidth = sourceImage.getWidth() * scale;
        displayedHeight = sourceImage.getHeight() * scale;
        imageX = (canvasWidth - displayedWidth) / 2 + panX;
        imageY = (canvasHeight - displayedHeight) / 2 + panY;
        drawChecker(graphics, canvasWidth, canvasHeight);

        boolean showResult = resultView.isSelected();
        graphics.drawImage(showResult ? resultImage : sourceImage,
                imageX, imageY, displayedWidth, displayedHeight);
        if (showResult) {
            return;
        }
        graphics.drawImage(removedOverlay, imageX, imageY, displayedWidth, displayedHeight);

        double sx = displayedWidth / sourceImage.getWidth();
        double sy = displayedHeight / sourceImage.getHeight();
        graphics.setLineCap(StrokeLineCap.ROUND);
        graphics.setLineJoin(StrokeLineJoin.ROUND);
        graphics.setStroke(PROTECT.deriveColor(0, 1, 1, 0.5));
        for (EraseStroke stroke : selection.protectStrokes()) {
            drawStroke(graphics, stroke.points(), stroke.radius(), sx, sy);
        }
        graphics.setStroke(protectTool.isSelected()
                ? PROTECT.deriveColor(0, 1, 1, 0.7) : Color.web("#facc15", 0.7));
        drawStroke(graphics, activeStroke, brushRadius(), sx, sy);

        double radius = Math.max(4, Math.min(9, scale * 2));
        for (PixelCoordinate seed : selection.seeds()) {
            double markerX = imageX + (seed.x() + 0.5) * sx;
            double markerY = imageY + (seed.y() + 0.5) * sy;
            graphics.setFill(SURFACE);
            graphics.fillOval(markerX - radius - 2, markerY - radius - 2, radius * 2 + 4, radius * 2 + 4);
            graphics.setFill(ACCENT);
            graphics.fillOval(markerX - radius, markerY - radius, radius * 2, radius * 2);
        }
    }

    private void drawChecker(GraphicsContext graphics, double canvasWidth, double canvasHeight) {
        double left = Math.max(0, imageX);
        double top = Math.max(0, imageY);
        double right = Math.min(canvasWidth, imageX + displayedWidth);
        double bottom = Math.min(canvasHeight, imageY + displayedHeight);
        double cell = 10;
        for (double y = top; y < bottom; y += cell) {
            for (double x = left; x < right; x += cell) {
                boolean light = ((int) ((x - imageX) / cell) + (int) ((y - imageY) / cell)) % 2 == 0;
                graphics.setFill(light ? CHECKER_LIGHT : CHECKER_DARK);
                graphics.fillRect(x, y, Math.min(cell, right - x), Math.min(cell, bottom - y));
            }
        }
    }

    private void drawStroke(
            GraphicsContext graphics, List<PixelCoordinate> points, int radius, double sx, double sy) {
        if (points.isEmpty()) {
            return;
        }
        graphics.setLineWidth(Math.max(1, (radius * 2 + 1) * Math.min(sx, sy)));
        graphics.beginPath();
        PixelCoordinate first = points.getFirst();
        graphics.moveTo(imageX + (first.x() + 0.5) * sx, imageY + (first.y() + 0.5) * sy);
        if (points.size() == 1) {
            graphics.lineTo(imageX + (first.x() + 0.5) * sx + 0.01, imageY + (first.y() + 0.5) * sy);
        }
        for (PixelCoordinate point : points.subList(1, points.size())) {
            graphics.lineTo(imageX + (point.x() + 0.5) * sx, imageY + (point.y() + 0.5) * sy);
        }
        graphics.stroke();
    }
}
