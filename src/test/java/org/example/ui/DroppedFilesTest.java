package org.example.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class DroppedFilesTest {
    @Test
    void separatesImagesFromAProjectAndIgnoresOtherFiles() {
        DroppedFiles dropped = DroppedFiles.of(List.of(Path.of("a.PNG"), Path.of("notes.txt"),
                Path.of("b.jpeg"), Path.of("logo.vektora"), Path.of("c.jpg")));

        assertEquals(List.of(Path.of("a.PNG"), Path.of("b.jpeg"), Path.of("c.jpg")), dropped.images());
        assertEquals(Path.of("logo.vektora"), dropped.project().orElseThrow());
        assertTrue(DroppedFiles.of(List.of(Path.of("x.gif"))).isEmpty());
    }
}
