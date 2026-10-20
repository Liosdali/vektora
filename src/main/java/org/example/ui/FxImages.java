package org.example.ui;

import java.awt.image.BufferedImage;
import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;

final class FxImages {
    private FxImages() {
    }

    static Image toFxImage(BufferedImage source) {
        int width = source.getWidth();
        int height = source.getHeight();
        return toFxImage(source.getRGB(0, 0, width, height, null, 0, width), width, height);
    }

    static Image toFxImage(int[] argbPixels, int width, int height) {
        WritableImage image = new WritableImage(width, height);
        image.getPixelWriter().setPixels(
                0, 0, width, height, PixelFormat.getIntArgbInstance(), argbPixels, 0, width);
        return image;
    }
}
