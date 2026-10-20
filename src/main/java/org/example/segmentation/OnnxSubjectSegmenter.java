package org.example.segmentation;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.FloatBuffer;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import org.example.vector.SubjectMask;

/** Runs a rembg-style salient object model (U²-Net, ISNet) locally on the CPU. */
public final class OnnxSubjectSegmenter implements SubjectSegmenter, AutoCloseable {
    private final SegmentationModel model;
    private final OrtEnvironment environment;
    private final OrtSession session;

    public OnnxSubjectSegmenter(SegmentationModel model, Path modelFile) throws SegmentationException {
        this.model = Objects.requireNonNull(model, "Model is required");
        Objects.requireNonNull(modelFile, "Model file is required");
        try {
            environment = OrtEnvironment.getEnvironment();
            session = environment.createSession(modelFile.toString(), new OrtSession.SessionOptions());
        } catch (OrtException exception) {
            throw new SegmentationException("Konu ayırma modeli yüklenemedi", exception);
        }
    }

    @Override
    public synchronized SubjectMask segment(BufferedImage image) throws SegmentationException {
        Objects.requireNonNull(image, "Image is required");
        int size = model.inputSize();
        String inputName = session.getInputNames().iterator().next();
        try (OnnxTensor input = OnnxTensor.createTensor(environment,
                FloatBuffer.wrap(inputTensor(image, model)), new long[] {1, 3, size, size});
             OrtSession.Result result = session.run(Map.of(inputName, input))) {
            OnnxTensor output = (OnnxTensor) result.get(0);
            long[] shape = output.getInfo().getShape();
            FloatBuffer values = output.getFloatBuffer();
            float[] prediction = new float[values.remaining()];
            values.get(prediction);
            return toMask(prediction, (int) shape[shape.length - 1], (int) shape[shape.length - 2], image);
        } catch (OrtException exception) {
            throw new SegmentationException("Konu ayırma modeli çalıştırılamadı", exception);
        }
    }

    @Override
    public void close() throws OrtException {
        session.close();
    }

    /**
     * The model input as NCHW floats: the image composited on white, resized to the model's
     * square input, scaled by its brightest value and normalized like rembg does.
     */
    static float[] inputTensor(BufferedImage image, SegmentationModel model) {
        int size = model.inputSize();
        BufferedImage resized = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = resized.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, size, size);
            graphics.drawImage(image, 0, 0, size, size, null);
        } finally {
            graphics.dispose();
        }
        int[] pixels = resized.getRGB(0, 0, size, size, null, 0, size);
        int brightest = 1;
        for (int pixel : pixels) {
            brightest = Math.max(brightest, Math.max((pixel >>> 16) & 0xff, Math.max((pixel >>> 8) & 0xff, pixel & 0xff)));
        }
        int plane = size * size;
        float[] tensor = new float[3 * plane];
        for (int index = 0; index < plane; index++) {
            int pixel = pixels[index];
            int[] channels = {(pixel >>> 16) & 0xff, (pixel >>> 8) & 0xff, pixel & 0xff};
            for (int channel = 0; channel < 3; channel++) {
                tensor[channel * plane + index] =
                        ((float) channels[channel] / brightest - model.mean(channel)) / model.deviation(channel);
            }
        }
        return tensor;
    }

    /**
     * Stretches the prediction to 0-255, resizes it to the source with bilinear sampling and keeps
     * transparent source pixels out of the subject.
     */
    static SubjectMask toMask(float[] prediction, int predictionWidth, int predictionHeight, BufferedImage source) {
        if (prediction.length != predictionWidth * predictionHeight) {
            throw new IllegalArgumentException("Prediction size does not match its shape");
        }
        float lowest = Float.MAX_VALUE;
        float highest = -Float.MAX_VALUE;
        for (float value : prediction) {
            lowest = Math.min(lowest, value);
            highest = Math.max(highest, value);
        }
        float range = Math.max(highest - lowest, 1e-6f);
        int width = source.getWidth();
        int height = source.getHeight();
        byte[] foreground = new byte[width * height];
        for (int y = 0; y < height; y++) {
            double sourceY = Math.max(0, Math.min(predictionHeight - 1, (y + 0.5) * predictionHeight / height - 0.5));
            int top = (int) sourceY;
            int bottom = Math.min(predictionHeight - 1, top + 1);
            double vertical = sourceY - top;
            for (int x = 0; x < width; x++) {
                if ((source.getRGB(x, y) >>> 24) == 0) {
                    continue;
                }
                double sourceX = Math.max(0, Math.min(predictionWidth - 1, (x + 0.5) * predictionWidth / width - 0.5));
                int left = (int) sourceX;
                int right = Math.min(predictionWidth - 1, left + 1);
                double horizontal = sourceX - left;
                double value = (prediction[top * predictionWidth + left] * (1 - horizontal)
                        + prediction[top * predictionWidth + right] * horizontal) * (1 - vertical)
                        + (prediction[bottom * predictionWidth + left] * (1 - horizontal)
                        + prediction[bottom * predictionWidth + right] * horizontal) * vertical;
                foreground[y * width + x] = (byte) Math.round((value - lowest) / range * 255);
            }
        }
        return new SubjectMask(width, height, foreground);
    }
}
