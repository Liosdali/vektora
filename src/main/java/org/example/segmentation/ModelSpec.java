package org.example.segmentation;

import java.net.URI;
import java.util.Locale;
import java.util.Objects;

/** A downloadable model file, pinned by size and SHA-256 so a tampered download is rejected. */
public record ModelSpec(String fileName, URI uri, long size, String sha256) {
    public ModelSpec {
        Objects.requireNonNull(fileName, "File name is required");
        Objects.requireNonNull(uri, "Download address is required");
        sha256 = Objects.requireNonNull(sha256, "SHA-256 is required").toLowerCase(Locale.ROOT);
        if (!fileName.matches("[A-Za-z0-9._-]+") || size < 1 || !sha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Invalid model description");
        }
    }
}
