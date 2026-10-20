package org.example.vector;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
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
        boolean mergeBlends = options.simplification() >= 1;
        List<Color> sourceColors = options.mode() == TraceOptions.ColorMode.PRESERVE_SOURCE
                ? sourcePalette(artwork, mergeBlends) : List.of();
        List<Color> customColors = options.mode() == TraceOptions.ColorMode.CUSTOM_PALETTE
                ? customPalette(options.palette(), hasTransparency) : List.of();
        // ImageTracer's own color reduction reseeds clusters at random, so the same image could
        // trace to different colors each time; a palette computed here keeps it reproducible.
        List<Color> reducedColors = options.mode() == TraceOptions.ColorMode.REDUCE_SOURCE
                ? customPalette(reducedPalette(artwork, options.maxColors(), mergeBlends), hasTransparency)
                : List.of();

        HashMap<String, Float> tracerOptions = new HashMap<>();
        tracerOptions.put("colorsampling", 0f);
        tracerOptions.put("numberofcolors", (float) switch (options.mode()) {
            case PRESERVE_SOURCE -> sourceColors.size();
            case REDUCE_SOURCE -> reducedColors.size();
            case CUSTOM_PALETTE -> customColors.size();
        });
        tracerOptions.put("colorquantcycles", 1f);
        Simplification simplification = Simplification.LEVELS[options.simplification()];
        tracerOptions.put("pathomit", simplification.omittedPathLength());
        tracerOptions.put("ltres", simplification.lineError());
        tracerOptions.put("qtres", simplification.curveError());
        tracerOptions.put("roundcoords", 1f);
        tracerOptions.put("viewbox", 1f);

        byte[][] palette = switch (options.mode()) {
            case PRESERVE_SOURCE -> encodePalette(sourceColors);
            case CUSTOM_PALETTE -> encodePalette(customColors);
            case REDUCE_SOURCE -> encodePalette(reducedColors);
        };
        try {
            return ImageTracer.imageToSVG(artwork, tracerOptions, palette);
        } catch (Exception exception) {
            throw new VectorizationException("Could not convert the PNG to SVG paths", exception);
        }
    }

    /**
     * ImageTracer settings per simplification level: paths with fewer edge nodes than
     * {@code omittedPathLength} are dropped as noise, and the error thresholds decide how far a
     * straight line or curve may stray from the pixel outline.
     */
    private record Simplification(float omittedPathLength, float lineError, float curveError) {
        private static final Simplification[] LEVELS = {
                new Simplification(0, 1, 1),
                new Simplification(4, 1, 1),
                new Simplification(8, 1.5f, 1.5f),
                new Simplification(16, 2.5f, 2.5f),
                new Simplification(32, 4, 4)
        };
    }

    private static List<Color> customPalette(List<Color> colors, boolean hasTransparency) {
        if (!hasTransparency) {
            return colors;
        }
        List<Color> palette = new ArrayList<>(colors);
        palette.add(new Color(0, 0, 0, 0));
        return List.copyOf(palette);
    }

    /**
     * The visible pixels' bounding box plus a one-pixel margin, which is the area that gets traced;
     * a single pixel at the origin when nothing is visible.
     */
    public static Rectangle artworkBounds(BufferedImage source) {
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
            return new Rectangle(0, 0, 1, 1);
        }
        int left = Math.max(0, minX - 1);
        int top = Math.max(0, minY - 1);
        int right = Math.min(source.getWidth() - 1, maxX + 1);
        int bottom = Math.min(source.getHeight() - 1, maxY + 1);
        return new Rectangle(left, top, right - left + 1, bottom - top + 1);
    }

    private static BufferedImage cropToArtwork(BufferedImage source) {
        Rectangle bounds = artworkBounds(source);
        BufferedImage cropped = new BufferedImage(bounds.width, bounds.height, BufferedImage.TYPE_INT_ARGB);
        if (bounds.width == 1 && bounds.height == 1 && (source.getRGB(0, 0) >>> 24) == 0) {
            return cropped;
        }
        Graphics2D graphics = cropped.createGraphics();
        try {
            graphics.drawImage(source, -bounds.x, -bounds.y, null);
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

    private static List<Color> reducedPalette(BufferedImage artwork, int maxColors, boolean mergeBlends) {
        List<Color> palette = ColorReducer.reduce(artwork, maxColors);
        return mergeBlends ? ColorReducer.withoutBlends(palette, artwork) : palette;
    }

    private static List<Color> sourcePalette(BufferedImage image, boolean mergeBlends) {
        List<Color> colors = sourcePalette(image);
        if (!mergeBlends) {
            return colors;
        }
        List<Color> opaque = colors.stream().filter(color -> color.getAlpha() == 255).toList();
        List<Color> kept = ColorReducer.withoutBlends(opaque, image);
        return colors.stream().filter(color -> color.getAlpha() != 255 || kept.contains(color)).toList();
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
