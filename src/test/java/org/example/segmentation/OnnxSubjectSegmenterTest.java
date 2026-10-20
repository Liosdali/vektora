package org.example.segmentation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import org.example.vector.SubjectMask;
import org.junit.jupiter.api.Test;

class OnnxSubjectSegmenterTest {
    @Test
    void normalizesAWhiteImageWithTheModelMeanAndDeviation() {
        BufferedImage white = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
        float[] tensor = OnnxSubjectSegmenter.inputTensor(white, SegmentationModel.U2NETP);

        int plane = 320 * 320;
        assertEquals(3 * plane, tensor.length);
        assertEquals((1 - 0.485f) / 0.229f, tensor[0], 1e-4);
        assertEquals((1 - 0.456f) / 0.224f, tensor[plane], 1e-4);
        assertEquals((1 - 0.406f) / 0.225f, tensor[2 * plane + 17], 1e-4);
    }

    @Test
    void stretchesAndResizesThePredictionAndKeepsTransparentPixelsOut() {
        BufferedImage source = new BufferedImage(4, 2, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < 4; x++) {
            source.setRGB(x, 0, Color.RED.getRGB());
            source.setRGB(x, 1, Color.RED.getRGB());
        }
        source.setRGB(3, 1, 0);
        float[] prediction = {0.2f, 0.8f, 0.2f, 0.8f};

        SubjectMask mask = OnnxSubjectSegmenter.toMask(prediction, 2, 2, source);

        assertEquals(0, mask.foreground(0, 0));
        assertEquals(255, mask.foreground(3, 0));
        assertTrue(mask.foreground(1, 0) < 128 && mask.foreground(2, 0) >= 128);
        assertEquals(0, mask.foreground(3, 1));
    }

    /** Runs only when -Dvektora.modelDir points at a folder holding u2netp.onnx. */
    @Test
    void realModelSeparatesADiskFromAPlainBackground() throws Exception {
        String directory = System.getProperty("vektora.modelDir");
        assumeTrue(directory != null, "vektora.modelDir is not set");
        Path model = Path.of(directory, SegmentationModel.U2NETP.spec().fileName());
        assumeTrue(Files.isRegularFile(model), "u2netp.onnx is missing");
        BufferedImage image = new BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(230, 230, 230));
        graphics.fillRect(0, 0, 200, 200);
        graphics.setColor(new Color(200, 30, 30));
        graphics.fillOval(50, 50, 100, 100);
        graphics.dispose();

        try (OnnxSubjectSegmenter segmenter = new OnnxSubjectSegmenter(SegmentationModel.U2NETP, model)) {
            SubjectMask mask = segmenter.segment(image);

            assertTrue(mask.foreground(100, 100) >= 128);
            assertTrue(mask.foreground(5, 5) < 128);
        }
    }
}
