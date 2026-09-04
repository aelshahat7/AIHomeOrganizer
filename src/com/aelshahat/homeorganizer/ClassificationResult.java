package com.aelshahat.homeorganizer;

public final class ClassificationResult {
    public final HomeShortcut shortcut;
    public final String category;
    public final float confidence;
    public final String reason;
    public final String resolvedPackage;
    public final boolean resolutionUnique;

    public ClassificationResult(HomeShortcut shortcut, String category, float confidence, String reason) {
        this(shortcut, category, confidence, reason, "", false);
    }

    public ClassificationResult(HomeShortcut shortcut, String category, float confidence, String reason,
                                String resolvedPackage, boolean resolutionUnique) {
        this.shortcut = shortcut;
        this.category = category;
        this.confidence = confidence;
        this.reason = reason == null ? "" : reason;
        this.resolvedPackage = resolvedPackage == null ? "" : resolvedPackage;
        this.resolutionUnique = resolutionUnique;
    }
}
