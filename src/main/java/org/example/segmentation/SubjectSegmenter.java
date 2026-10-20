package org.example.segmentation;

import java.awt.image.BufferedImage;
import org.example.vector.SubjectMask;

@FunctionalInterface
public interface SubjectSegmenter {
    SubjectMask segment(BufferedImage image) throws SegmentationException;
}
