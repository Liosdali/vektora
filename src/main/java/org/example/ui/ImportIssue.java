package org.example.ui;

import java.nio.file.Path;
import java.util.Objects;

public record ImportIssue(Path path, String message) {
    public ImportIssue {
        Objects.requireNonNull(path, "Import issue path is required");
        Objects.requireNonNull(message, "Import issue message is required");
    }
}
