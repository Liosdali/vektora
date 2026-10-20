package org.example.vector;

import java.awt.image.BufferedImage;

@FunctionalInterface
public interface SvgVectorizer {
    String vectorize(BufferedImage image, TraceOptions options) throws VectorizationException;
}
