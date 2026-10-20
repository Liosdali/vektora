package org.example.segmentation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SegmentationModelTest {
    @Test
    void grabCutIsOfferedWithoutADownload() {
        assertFalse(SegmentationModel.GRABCUT.needsDownload());
        assertEquals("Modelsiz · GrabCut (indirme yok)", SegmentationModel.GRABCUT.toString());
        assertTrue(SegmentationModel.U2NETP.needsDownload());
        assertTrue(SegmentationModel.U2NETP.toString().endsWith("MB)"));
    }
}
