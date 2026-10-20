package org.example.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.ToggleButton;
import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import org.example.model.IconAsset;
import org.example.vector.EraseStroke;
import org.example.vector.PixelCoordinate;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

public final class BackgroundRegionPickerPane extends BorderPane {
    private final Canvas canvas = new Canvas();
    private final StackPane imageSurface = new StackPane(canvas);
    private final ToggleButton drawMode = new ToggleButton("Çizerek sil");
    private final Slider brushSize = new Slider(1, 20, 4);
    private final List<PixelCoordinate> activeStroke = new ArrayList<>();
    private Image sourceImage;
    private List<PixelCoordinate> seeds = List.of();
    private List<EraseStroke> strokes = List.of();
    private Consumer<PixelCoordinate> onSeedSelected = ignored -> { };
    private Consumer<EraseStroke> onStrokeDrawn = ignored -> {
    };
    private double imageX;
    private double imageY;
    private double displayedWidth;
    private double displayedHeight;

    public BackgroundRegionPickerPane() {
        setStyle("-fx-background-color: #111827; -fx-background-radius: 16; -fx-padding: 20;");
        Label title = new Label("Arka plan bölgesini seç");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: 700; -fx-text-fill: #f3f4f6;");
        Label hint = new Label(
                "Bölge modu: silmek istediğin bağlı alana tıkla. Çizim modu: silinecek kısmı fareyle boya; "
                        + "boyadığın alan saydam yapılır.");
        hint.setWrapText(true);
        hint.setStyle("-fx-font-size: 12px; -fx-text-fill: #9ca3af;");
        drawMode.setStyle("-fx-background-color: #1d2533; -fx-text-fill: #e2e8f0;");
        brushSize.setPrefWidth(160);
        Label brushLabel = new Label("Fırça");
        brushLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #9ca3af;");
        HBox tools = new HBox(10, drawMode, brushLabel, brushSize);
        tools.setAlignment(Pos.CENTER_LEFT);
        VBox header = new VBox(5, title, hint, tools);
        header.setPadding(new Insets(0, 0, 16, 0));
        setTop(header);
        imageSurface.setStyle("-fx-background-color: #151b27;");
        imageSurface.setMinSize(100, 100);
        canvas.widthProperty().bind(imageSurface.widthProperty());
        canvas.heightProperty().bind(imageSurface.heightProperty());
        canvas.widthProperty().addListener((observable, oldValue, newValue) -> draw());
        canvas.heightProperty().addListener((observable, oldValue, newValue) -> draw());
        canvas.setFocusTraversable(true);
        canvas.setAccessibleText("Arka plan seçimi. Tıkla veya silinecek alanı çiz.");
        canvas.setOnMouseClicked(event -> {
            if (!drawMode.isSelected()) {
                coordinateAt(event.getX(), event.getY()).ifPresent(onSeedSelected);
            }
        });
        canvas.setOnMousePressed(event -> {
            if (drawMode.isSelected()) {
                activeStroke.clear();
                coordinateAt(event.getX(), event.getY()).ifPresent(activeStroke::add);
                draw();
            }
        });
        canvas.setOnMouseDragged(event -> {
            if (drawMode.isSelected()) {
                coordinateAt(event.getX(), event.getY()).ifPresent(point -> {
                    if (activeStroke.isEmpty() || !activeStroke.getLast().equals(point)) {
                        activeStroke.add(point);
                        draw();
                    }
                });
            }
        });
        canvas.setOnMouseReleased(event -> {
            if (drawMode.isSelected() && !activeStroke.isEmpty()) {
                EraseStroke stroke = new EraseStroke(activeStroke, brushRadius());
                activeStroke.clear();
                onStrokeDrawn.accept(stroke);
            }
        });
        setCenter(imageSurface);
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
            IconAsset icon, List<PixelCoordinate> selectedSeeds, List<EraseStroke> selectedStrokes,
            Consumer<PixelCoordinate> seedSelected, Consumer<EraseStroke> strokeDrawn) {
        Objects.requireNonNull(icon, "Selected icon is required");
        seeds = List.copyOf(Objects.requireNonNull(selectedSeeds, "Selected seeds are required"));
        strokes = List.copyOf(Objects.requireNonNull(selectedStrokes, "Selected strokes are required"));
        onSeedSelected = Objects.requireNonNull(seedSelected, "Seed callback is required");
        onStrokeDrawn = Objects.requireNonNull(strokeDrawn, "Stroke callback is required");
        activeStroke.clear();
        int largestBrush = Math.max(5, Math.min(icon.image().getWidth(), icon.image().getHeight()) / 4);
        brushSize.setMax(largestBrush);
        if (brushSize.getValue() > largestBrush) {
            brushSize.setValue(largestBrush);
        }
        sourceImage = toFxImage(icon.image());
        draw();
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

    private static Image toFxImage(BufferedImage source) {
        int width = source.getWidth();
        int height = source.getHeight();
        int[] pixels = source.getRGB(0, 0, width, height, null, 0, width);
        WritableImage image = new WritableImage(width, height);
        image.getPixelWriter().setPixels(
                0, 0, width, height, PixelFormat.getIntArgbInstance(), pixels, 0, width);
        return image;
    }

    private void draw() {
        double canvasWidth = canvas.getWidth();
        double canvasHeight = canvas.getHeight();
        if (canvasWidth <= 0 || canvasHeight <= 0) {
            return;
        }

        GraphicsContext graphics = canvas.getGraphicsContext2D();
        graphics.setFill(Color.web("#151b27"));
        graphics.fillRect(0, 0, canvasWidth, canvasHeight);
        if (sourceImage == null) {
            return;
        }

        double scale = Math.min(
                canvasWidth / sourceImage.getWidth(), canvasHeight / sourceImage.getHeight());
        displayedWidth = sourceImage.getWidth() * scale;
        displayedHeight = sourceImage.getHeight() * scale;
        imageX = (canvasWidth - displayedWidth) / 2;
        imageY = (canvasHeight - displayedHeight) / 2;
        graphics.drawImage(sourceImage, imageX, imageY, displayedWidth, displayedHeight);
        graphics.setFill(Color.web("#f97316"));
        for (PixelCoordinate seed : seeds) {
            double markerX = imageX + (seed.x() + 0.5) * displayedWidth / sourceImage.getWidth();
            double markerY = imageY + (seed.y() + 0.5) * displayedHeight / sourceImage.getHeight();
            double radius = Math.max(5, Math.min(12, scale * 3));
            graphics.fillOval(markerX - radius, markerY - radius, radius * 2, radius * 2);
        }
        double sx = displayedWidth / sourceImage.getWidth();
        double sy = displayedHeight / sourceImage.getHeight();
        graphics.setLineCap(StrokeLineCap.ROUND);
        graphics.setLineJoin(StrokeLineJoin.ROUND);
        graphics.setStroke(Color.rgb(239, 68, 68, 0.6));
        for (EraseStroke stroke : strokes) {
            drawStroke(graphics, stroke.points(), stroke.radius(), sx, sy);
        }
        graphics.setStroke(Color.rgb(250, 204, 21, 0.6));
        drawStroke(graphics, activeStroke, brushRadius(), sx, sy);
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
