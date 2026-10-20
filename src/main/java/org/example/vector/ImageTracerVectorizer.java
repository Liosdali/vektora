package org.example.vector;

import java.awt.Color;
import java.awt.image.BufferedImage;
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

        HashMap<String, Float> tracerOptions = new HashMap<>();
        tracerOptions.put("colorsampling",
                options.mode() == TraceOptions.ColorMode.REDUCE_SOURCE ? 1f : 0f);
        tracerOptions.put("numberofcolors", (float) switch (options.mode()) {
            case PRESERVE_SOURCE -> sourcePalette(image).size();
            case REDUCE_SOURCE, CUSTOM_PALETTE -> options.maxColors();
        });
        if (options.mode() != TraceOptions.ColorMode.REDUCE_SOURCE) {
            tracerOptions.put("colorquantcycles", 1f);
        }
        tracerOptions.put("pathomit", 0f);
        tracerOptions.put("roundcoords", 1f);
        tracerOptions.put("viewbox", 1f);

        byte[][] palette = switch (options.mode()) {
            case PRESERVE_SOURCE -> encodePalette(sourcePalette(image));
            case CUSTOM_PALETTE -> encodePalette(options.palette());
            case REDUCE_SOURCE -> null;
        };
        try {
            return ImageTracer.imageToSVG(image, tracerOptions, palette);
        } catch (Exception exception) {
            throw new VectorizationException("Could not convert the PNG to SVG paths", exception);
        }
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
