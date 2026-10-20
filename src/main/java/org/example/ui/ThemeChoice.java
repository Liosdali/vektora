package org.example.ui;

import java.util.Objects;
import java.util.prefs.Preferences;

/** Remembers, per user, whether the light theme is on. */
final class ThemeChoice {
    private static final String LIGHT = "lightTheme";

    private final Preferences preferences;

    ThemeChoice(Preferences preferences) {
        this.preferences = Objects.requireNonNull(preferences, "Preferences are required");
    }

    static ThemeChoice forCurrentUser() {
        return new ThemeChoice(Preferences.userNodeForPackage(ThemeChoice.class));
    }

    boolean light() {
        return preferences.getBoolean(LIGHT, false);
    }

    void setLight(boolean value) {
        preferences.putBoolean(LIGHT, value);
    }
}
