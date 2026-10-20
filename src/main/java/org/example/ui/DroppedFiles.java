package org.example.ui;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.example.project.ProjectFile;

/** Files dropped on the window, sorted into images to add and a project to open. */
record DroppedFiles(List<Path> images, Optional<Path> project) {
    static DroppedFiles of(List<Path> paths) {
        List<Path> images = new ArrayList<>();
        Path project = null;
        for (Path path : paths) {
            String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
            if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")) {
                images.add(path);
            } else if (project == null && name.endsWith(ProjectFile.EXTENSION)) {
                project = path;
            }
        }
        return new DroppedFiles(List.copyOf(images), Optional.ofNullable(project));
    }

    boolean isEmpty() {
        return images.isEmpty() && project.isEmpty();
    }
}
