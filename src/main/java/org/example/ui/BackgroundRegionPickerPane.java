package org.example.ui;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.example.model.IconAsset;
import org.example.vector.PixelCoordinate;

public final class BackgroundRegionPickerPane extends BorderPane {
    private final Canvas canvas = new Canvas();
    private final StackPane imageSurface = new StackPane(canvas);
    private Image sourceImage;
    private List<PixelCoordinate> seeds = List.of();
    private Consumer<PixelCoordinate> onSeedSelected = ignored -> { };
    private double imageX;
    private double imageY;
    private double displayedWidth;
    private double displayedHeight;

    public BackgroundRegionPickerPane() {
        setStyle("-fx-background-color: #111827; -fx-background-radius: 16; -fx-padding: 20;");
        Label title = new Label("Arka plan bölgesini seç");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: 700; -fx-text-fill: #f3f4f6;");
        Label hint = new Label(
                "Silmek istediğin bağlı alana tıkla. Benzer renkli bağlı pikseller kaldırılır; "
                        + "farklı bölgeler için tekrar tıkla.");
        hint.setWrapText(true);
        hint.setStyle("-fx-font-size: 12px; -fx-text-fill: #9ca3af;");
        VBox header = new VBox(5, title, hint);
        header.setPadding(new Insets(0, 0, 16, 0));
        setTop(header);
        imageSurface.setStyle("-fx-background-color: #151b27;");
        imageSurface.setMinSize(100, 100);
        canvas.widthProperty().bind(imageSurface.widthProperty());
        canvas.heightProperty().bind(imageSurface.heightProperty());
        canvas.widthProperty().addListener((observable, oldValue, newValue) -> draw());
        canvas.heightProperty().addListener((observable, oldValue, newValue) -> draw());
        canvas.setFocusTraversable(true);
        canvas.setAccessibleText("PNG arka plan seçimi. Silinecek bağlı renk alanına tıkla.");
        canvas.setOnMouseClicked(event -> sourceCoordinate(
                        event.getX(), event.getY(), imageX, imageY, displayedWidth, displayedHeight,
                        sourceImage == null ? 0 : (int) sourceImage.getWidth(),
                        sourceImage == null ? 0 : (int) sourceImage.getHeight())
                .ifPresent(onSeedSelected));
        setCenter(imageSurface);
    }

    public void setSource(
            IconAsset icon, List<PixelCoordinate> selectedSeeds, Consumer<PixelCoordinate> seedSelected) {
        Objects.requireNonNull(icon, "Selected icon is required");
        seeds = List.copyOf(Objects.requireNonNull(selectedSeeds, "Selected seeds are required"));
        onSeedSelected = Objects.requireNonNull(seedSelected, "Seed callback is required");
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
    }
}
