package org.example.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ThemeChoiceTest {
    private final Preferences preferences =
            Preferences.userRoot().node("org/example/vektora-test-" + UUID.randomUUID());

    @AfterEach
    void removePreferences() throws BackingStoreException {
        preferences.removeNode();
    }

    @Test
    void startsDarkAndRemembersTheLightTheme() {
        assertFalse(new ThemeChoice(preferences).light());

        new ThemeChoice(preferences).setLight(true);

        assertTrue(new ThemeChoice(preferences).light());
    }
}
