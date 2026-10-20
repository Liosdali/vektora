package org.example.ui;

final class PreviewRequestTracker {
    private long latestRequest;

    long beginRequest() {
        return ++latestRequest;
    }

    boolean isCurrent(long request) {
        return request == latestRequest;
    }
}
