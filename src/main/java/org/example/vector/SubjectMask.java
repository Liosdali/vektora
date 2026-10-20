package org.example.vector;

import java.util.Objects;

/**
 * Per-pixel foreground likelihood (0-255) for one source image, as predicted by a subject
 * segmentation model. Pixels at or above the midpoint count as the subject.
 */
public final class SubjectMask {
    private final int width;
    private final int height;
    private final byte[] foreground;

    public SubjectMask(int width, int height, byte[] foreground) {
        Objects.requireNonNull(foreground, "Foreground values are required");
        if (width < 1 || height < 1 || foreground.length != width * height) {
            throw new IllegalArgumentException("Mask size must match its width and height");
        }
        this.width = width;
        this.height = height;
        this.foreground = foreground.clone();
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int foreground(int x, int y) {
        return foreground[y * width + x] & 0xff;
    }

    public boolean isSubject(int index) {
        return (foreground[index] & 0xff) >= 128;
    }
}
