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

    static BufferedImage toBufferedImage(Image source) {
        int width = (int) source.getWidth();
        int height = (int) source.getHeight();
        int[] pixels = new int[width * height];
        source.getPixelReader().getPixels(0, 0, width, height, PixelFormat.getIntArgbInstance(), pixels, 0, width);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, width, height, pixels, 0, width);
        return image;
    }

    static Image toFxImage(int[] argbPixels, int width, int height) {
        WritableImage image = new WritableImage(width, height);
        image.getPixelWriter().setPixels(
                0, 0, width, height, PixelFormat.getIntArgbInstance(), argbPixels, 0, width);
        return image;
    }
}
