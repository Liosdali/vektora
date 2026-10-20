package org.example.project;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.prefs.Preferences;

/** The last few projects opened or saved, most recent first, remembered per user. */
public final class RecentProjects {
    private static final int LIMIT = 5;
    private static final String KEY = "recentProjects";

    private final Preferences preferences;

    public RecentProjects(Preferences preferences) {
        this.preferences = Objects.requireNonNull(preferences, "Preferences are required");
    }

    public static RecentProjects forCurrentUser() {
        return new RecentProjects(Preferences.userNodeForPackage(RecentProjects.class));
    }

    public List<Path> list() {
        String stored = preferences.get(KEY, "");
        return stored.isEmpty() ? List.of() : stored.lines().map(Path::of).toList();
    }

    public void add(Path project) {
        Path absolute = project.toAbsolutePath().normalize();
        List<Path> paths = new ArrayList<>(list());
        paths.remove(absolute);
        paths.addFirst(absolute);
        preferences.put(KEY, String.join("\n",
                paths.subList(0, Math.min(LIMIT, paths.size())).stream().map(Path::toString).toList()));
    }
}
