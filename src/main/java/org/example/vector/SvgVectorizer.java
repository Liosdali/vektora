package org.example.vector;

import java.awt.image.BufferedImage;

@FunctionalInterface
public interface SvgVectorizer {
    String vectorize(BufferedImage image, TraceOptions options) throws VectorizationException;

    /**
     * Traces {@code image} with the colors {@code paletteSource} would get, so a smaller preview
     * copy keeps the layer colors of the full image. Vectorizers without a palette of their own
     * ignore it.
     */
    default String vectorize(BufferedImage image, TraceOptions options, BufferedImage paletteSource)
            throws VectorizationException {
        return vectorize(image, options);
    }
}
