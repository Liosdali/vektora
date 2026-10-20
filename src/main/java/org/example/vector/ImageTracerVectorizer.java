package org.example.vector;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import jankovicsandras.imagetracer.ImageTracer;

public final class ImageTracerVectorizer implements SvgVectorizer {
    @Override
    public String vectorize(BufferedImage image, TraceOptions options) throws VectorizationException {
        if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
            throw new VectorizationException("A non-empty PNG image is required");
        }
        if (options == null) {
            throw new VectorizationException("Vectorization options are required");
        }

        BufferedImage artwork = cropToArtwork(BackgroundRegionProcessor.removeBorderBackground(image));
        boolean hasTransparency = hasTransparentPixels(artwork);
        List<Color> sourceColors = options.mode() == TraceOptions.ColorMode.PRESERVE_SOURCE
                ? sourcePalette(artwork) : List.of();
        List<Color> customColors = options.mode() == TraceOptions.ColorMode.CUSTOM_PALETTE
                ? customPalette(options.palette(), hasTransparency) : List.of();

        HashMap<String, Float> tracerOptions = new HashMap<>();
        tracerOptions.put("colorsampling",
                options.mode() == TraceOptions.ColorMode.REDUCE_SOURCE ? 1f : 0f);
        tracerOptions.put("numberofcolors", (float) switch (options.mode()) {
            case PRESERVE_SOURCE -> sourceColors.size();
            case REDUCE_SOURCE -> options.maxColors();
            case CUSTOM_PALETTE -> customColors.size();
        });
        if (options.mode() != TraceOptions.ColorMode.REDUCE_SOURCE) {
            tracerOptions.put("colorquantcycles", 1f);
        }
        tracerOptions.put("pathomit", 0f);
        tracerOptions.put("roundcoords", 1f);
        tracerOptions.put("viewbox", 1f);

        byte[][] palette = switch (options.mode()) {
            case PRESERVE_SOURCE -> encodePalette(sourceColors);
            case CUSTOM_PALETTE -> encodePalette(customColors);
            case REDUCE_SOURCE -> null;
        };
        try {
            return ImageTracer.imageToSVG(artwork, tracerOptions, palette);
        } catch (Exception exception) {
            throw new VectorizationException("Could not convert the PNG to SVG paths", exception);
        }
    }

    private static List<Color> customPalette(List<Color> colors, boolean hasTransparency) {
        if (!hasTransparency) {
            return colors;
        }
        List<Color> palette = new ArrayList<>(colors);
        palette.add(new Color(0, 0, 0, 0));
        return List.copyOf(palette);
    }

    private static BufferedImage cropToArtwork(BufferedImage source) {
        int minX = source.getWidth();
        int minY = source.getHeight();
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                if ((source.getRGB(x, y) >>> 24) != 0) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        if (maxX < 0) {
            return new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        }

        int left = Math.max(0, minX - 1);
        int top = Math.max(0, minY - 1);
        int right = Math.min(source.getWidth() - 1, maxX + 1);
        int bottom = Math.min(source.getHeight() - 1, maxY + 1);
        BufferedImage cropped = new BufferedImage(right - left + 1, bottom - top + 1,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = cropped.createGraphics();
        try {
            graphics.drawImage(source, -left, -top, null);
        } finally {
            graphics.dispose();
        }
        return cropped;
    }

    private static boolean hasTransparentPixels(BufferedImage image) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) >>> 24) != 255) {
                    return true;
                }
            }
        }
        return false;
    }

    private static List<Color> sourcePalette(BufferedImage image) {
        LinkedHashMap<Integer, Color> colors = new LinkedHashMap<>();
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                Color color = new Color(image.getRGB(x, y), true);
                if (color.getAlpha() == 0) {
                    color = new Color(0, 0, 0, 0);
                }
                colors.putIfAbsent(color.getRGB(), color);
            }
        }
        return List.copyOf(colors.values());
    }

    private static byte[][] encodePalette(List<Color> colors) {
        return colors.stream()
                .map(color -> new byte[] {
                        (byte) (color.getRed() - 128),
                        (byte) (color.getGreen() - 128),
                        (byte) (color.getBlue() - 128),
                        (byte) (color.getAlpha() - 128)
                })
                .toArray(byte[][]::new);
    }
}
