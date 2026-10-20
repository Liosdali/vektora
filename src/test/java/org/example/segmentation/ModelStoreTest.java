package org.example.segmentation;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModelStoreTest {
    private static final byte[] MODEL = "tiny model bytes".getBytes(StandardCharsets.UTF_8);
    private static final URI ADDRESS = URI.create("https://example.invalid/model.onnx");

    @Test
    void installsAVerifiedModelOnceAndReportsProgress(@TempDir Path directory) throws Exception {
        AtomicInteger downloads = new AtomicInteger();
        ModelStore store = new ModelStore(directory, uri -> {
            downloads.incrementAndGet();
            return new ByteArrayInputStream(MODEL);
        });
        ModelSpec spec = new ModelSpec("model.onnx", ADDRESS, MODEL.length, sha256(MODEL));
        List<Long> progress = new ArrayList<>();

        assertFalse(store.isInstalled(spec));
        Path installed = store.install(spec, (done, total) -> progress.add(done));
        store.install(spec, (done, total) -> { });

        assertTrue(store.isInstalled(spec));
        assertArrayEquals(MODEL, Files.readAllBytes(installed));
        assertEquals((long) MODEL.length, progress.getLast());
        assertEquals(1, downloads.get());
        try (var files = Files.list(directory)) {
            assertEquals(List.of(installed), files.toList());
        }
    }

    @Test
    void rejectsADownloadWhoseHashDoesNotMatch(@TempDir Path directory) throws Exception {
        ModelStore store = new ModelStore(directory, uri -> new ByteArrayInputStream(MODEL));
        ModelSpec spec = new ModelSpec("model.onnx", ADDRESS, MODEL.length, "0".repeat(64));

        assertThrows(IOException.class, () -> store.install(spec, (done, total) -> { }));

        assertFalse(store.isInstalled(spec));
        try (var files = Files.list(directory)) {
            assertEquals(0, files.count());
        }
    }

    @Test
    void stopsADownloadThatIsLargerThanExpected(@TempDir Path directory) throws Exception {
        ModelStore store = new ModelStore(directory, uri -> new ByteArrayInputStream(MODEL));
        ModelSpec spec = new ModelSpec("model.onnx", ADDRESS, MODEL.length - 1, sha256(MODEL));

        assertThrows(IOException.class, () -> store.install(spec, (done, total) -> { }));
        assertFalse(store.isInstalled(spec));
    }

    @Test
    void pinsTheBuiltInModelsBySizeAndHash() {
        for (SegmentationModel model : SegmentationModel.values()) {
            if (!model.needsDownload()) {
                continue;
            }
            assertEquals("https", model.spec().uri().getScheme());
            assertEquals(64, model.spec().sha256().length());
        }
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
