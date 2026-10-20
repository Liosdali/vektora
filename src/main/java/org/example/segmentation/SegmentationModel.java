package org.example.segmentation;

import java.net.URI;
import java.util.Locale;

/** Subject segmentation models from the rembg project, run locally with ONNX Runtime. */
public enum SegmentationModel {
    U2NETP("Hızlı · U²-Net-p",
            new ModelSpec("u2netp.onnx",
                    URI.create("https://github.com/danielgatis/rembg/releases/download/v0.0.0/u2netp.onnx"),
                    4_574_861, "309c8469258dda742793dce0ebea8e6dd393174f89934733ecc8b14c76f4ddd8"),
            320, new float[] {0.485f, 0.456f, 0.406f}, new float[] {0.229f, 0.224f, 0.225f}),
    ISNET("Kaliteli · ISNet",
            new ModelSpec("isnet-general-use.onnx",
                    URI.create("https://github.com/danielgatis/rembg/releases/download/v0.0.0/isnet-general-use.onnx"),
                    178_648_008, "60920e99c45464f2ba57bee2ad08c919a52bbf852739e96947fbb4358c0d964a"),
            1024, new float[] {0.5f, 0.5f, 0.5f}, new float[] {1f, 1f, 1f});

    private final String label;
    private final ModelSpec spec;
    private final int inputSize;
    private final float[] mean;
    private final float[] deviation;

    SegmentationModel(String label, ModelSpec spec, int inputSize, float[] mean, float[] deviation) {
        this.label = label;
        this.spec = spec;
        this.inputSize = inputSize;
        this.mean = mean;
        this.deviation = deviation;
    }

    public ModelSpec spec() {
        return spec;
    }

    public int inputSize() {
        return inputSize;
    }

    float mean(int channel) {
        return mean[channel];
    }

    float deviation(int channel) {
        return deviation[channel];
    }

    public String downloadSize() {
        return String.format(Locale.forLanguageTag("tr"), "%.1f MB", spec.size() / (1024.0 * 1024.0));
    }

    @Override
    public String toString() {
        return label + " (" + downloadSize() + ")";
    }
}
