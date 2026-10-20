package org.example.segmentation;

import java.awt.Rectangle;
import java.util.List;
import java.util.Objects;
import org.example.vector.EraseStroke;

/**
 * What the user already said about an image, for segmenters that can use it.
 *
 * @param frame  the area holding the subject in source pixels, or {@code null} for the default
 * @param keep   brush strokes over pixels that belong to the subject
 * @param remove brush strokes over pixels that belong to the background
 */
public record SegmentationHints(Rectangle frame, List<EraseStroke> keep, List<EraseStroke> remove) {
    public static final SegmentationHints NONE = new SegmentationHints(null, List.of(), List.of());

    public SegmentationHints {
        frame = frame == null ? null : new Rectangle(frame);
        keep = List.copyOf(Objects.requireNonNull(keep, "Keep strokes are required"));
        remove = List.copyOf(Objects.requireNonNull(remove, "Remove strokes are required"));
    }
}
