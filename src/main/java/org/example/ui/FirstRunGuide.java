package org.example.ui;

import java.util.Objects;
import java.util.prefs.Preferences;

/** Remembers, per user, whether the "how it works" card has been dismissed. */
final class FirstRunGuide {
    private static final String DISMISSED = "firstRunGuideDismissed";

    private final Preferences preferences;

    FirstRunGuide(Preferences preferences) {
        this.preferences = Objects.requireNonNull(preferences, "Preferences are required");
    }

    static FirstRunGuide forCurrentUser() {
        return new FirstRunGuide(Preferences.userNodeForPackage(FirstRunGuide.class));
    }

    boolean shouldShow() {
        return !preferences.getBoolean(DISMISSED, false);
    }

    void dismiss() {
        preferences.putBoolean(DISMISSED, true);
    }
}
