package org.example.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PreviewRequestTrackerTest {
    @Test
    void rejectsAnOlderPreviewCompletionAfterANewerRequestStarts() {
        PreviewRequestTracker tracker = new PreviewRequestTracker();
        long olderRequest = tracker.beginRequest();
        long newerRequest = tracker.beginRequest();

        assertFalse(tracker.isCurrent(olderRequest));
        assertTrue(tracker.isCurrent(newerRequest));
    }
}
