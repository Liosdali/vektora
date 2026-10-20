package org.example.ui;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FirstRunGuideTest {
    private final Preferences preferences =
            Preferences.userRoot().node("org/example/vektora-test-" + UUID.randomUUID());

    @AfterEach
    void removePreferences() throws BackingStoreException {
        preferences.removeNode();
    }

    @Test
    void showsTheGuideUntilItIsDismissedOnce() {
        assertTrue(new FirstRunGuide(preferences).shouldShow());

        new FirstRunGuide(preferences).dismiss();

        assertFalse(new FirstRunGuide(preferences).shouldShow());
    }
}
