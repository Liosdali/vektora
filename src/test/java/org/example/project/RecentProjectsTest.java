package org.example.project;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class RecentProjectsTest {
    private final Preferences preferences =
            Preferences.userRoot().node("org/example/vektora-test-" + UUID.randomUUID());

    @AfterEach
    void removePreferences() throws BackingStoreException {
        preferences.removeNode();
    }

    @Test
    void listsTheLastFiveProjectsMostRecentFirst() {
        RecentProjects recent = new RecentProjects(preferences);
        for (int index = 1; index <= 6; index++) {
            recent.add(Path.of("p" + index + ".vektora"));
        }
        recent.add(Path.of("p3.vektora"));

        assertEquals(List.of("p3.vektora", "p6.vektora", "p5.vektora", "p4.vektora", "p2.vektora"),
                new RecentProjects(preferences).list().stream().map(path -> path.getFileName().toString()).toList());
    }
}
