package org.example.segmentation;

import java.awt.image.BufferedImage;
import org.example.vector.SubjectMask;

@FunctionalInterface
public interface SubjectSegmenter {
    SubjectMask segment(BufferedImage image) throws SegmentationException;

    /** Segments with the user's hints; segmenters that cannot use them ignore them. */
    default SubjectMask segment(BufferedImage image, SegmentationHints hints) throws SegmentationException {
        return segment(image);
    }
}
