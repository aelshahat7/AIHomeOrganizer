package com.aelshahat.homeorganizer;

public final class ClassificationResult {
    public final HomeShortcut shortcut;
    public final String category;
    public final float confidence;
    public final String reason;

    public ClassificationResult(HomeShortcut shortcut, String category, float confidence, String reason) {
        this.shortcut = shortcut;
        this.category = category;
        this.confidence = confidence;
        this.reason = reason == null ? "" : reason;
    }
}
