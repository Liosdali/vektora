package org.example.segmentation;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Objects;

/** Keeps downloaded model files in one folder and only accepts files matching their pinned hash. */
public final class ModelStore {
    private final Path directory;
    private final Source source;

    @FunctionalInterface
    public interface Source {
        InputStream open(URI uri) throws IOException;
    }

    @FunctionalInterface
    public interface Progress {
        void update(long downloadedBytes, long totalBytes);
    }

    public ModelStore(Path directory, Source source) {
        this.directory = Objects.requireNonNull(directory, "Model directory is required");
        this.source = Objects.requireNonNull(source, "Model source is required");
    }

    /** Models live in {@code ~/.vektora/models} and are fetched over HTTPS. */
    public static ModelStore inUserHome() {
        return new ModelStore(Path.of(System.getProperty("user.home"), ".vektora", "models"), httpsSource());
    }

    public Path path(ModelSpec spec) {
        return directory.resolve(spec.fileName());
    }

    public boolean isInstalled(ModelSpec spec) {
        Path file = path(spec);
        try {
            return Files.isRegularFile(file) && Files.size(file) == spec.size();
        } catch (IOException exception) {
            return false;
        }
    }

    public Path install(ModelSpec spec, Progress progress) throws IOException {
        Objects.requireNonNull(progress, "Progress listener is required");
        if (isInstalled(spec)) {
            return path(spec);
        }
        Files.createDirectories(directory);
        Path partial = Files.createTempFile(directory, spec.fileName(), ".part");
        try {
            MessageDigest digest = sha256();
            long downloaded = 0;
            try (InputStream input = source.open(spec.uri()); OutputStream output = Files.newOutputStream(partial)) {
                byte[] buffer = new byte[64 * 1024];
                for (int read = input.read(buffer); read >= 0; read = input.read(buffer)) {
                    downloaded += read;
                    if (downloaded > spec.size()) {
                        throw new IOException("Model dosyası beklenenden büyük; indirme durduruldu");
                    }
                    digest.update(buffer, 0, read);
                    output.write(buffer, 0, read);
                    progress.update(downloaded, spec.size());
                }
            }
            if (downloaded != spec.size() || !HexFormat.of().formatHex(digest.digest()).equals(spec.sha256())) {
                throw new IOException("İndirilen model doğrulanamadı (boyut veya SHA-256 uyuşmuyor)");
            }
            return Files.move(partial, path(spec), StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(partial);
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static Source httpsSource() {
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(20))
                .build();
        return uri -> {
            if (!"https".equals(uri.getScheme())) {
                throw new IOException("Modeller yalnızca HTTPS üzerinden indirilir");
            }
            try {
                HttpResponse<InputStream> response = client.send(
                        HttpRequest.newBuilder(uri).GET().build(), HttpResponse.BodyHandlers.ofInputStream());
                if (response.statusCode() != 200) {
                    response.body().close();
                    throw new IOException("Model indirilemedi (HTTP " + response.statusCode() + ")");
                }
                return response.body();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException("Model indirmesi kesildi", exception);
            }
        };
    }
}
