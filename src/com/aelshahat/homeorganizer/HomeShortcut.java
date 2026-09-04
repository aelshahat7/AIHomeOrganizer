package com.aelshahat.homeorganizer;

import android.graphics.Rect;

public final class HomeShortcut {
    public final String label;
    public final String packageName;
    public final String componentName;
    public final String className;
    public final String viewId;
    public final Rect bounds;
    public final int centerX;
    public final int centerY;
    public final boolean clickable;
    public final boolean longClickable;
    public final boolean visibleToUser;
    public final float confidence;

    public HomeShortcut(String label,
                        String packageName,
                        String componentName,
                        String className,
                        String viewId,
                        Rect bounds,
                        boolean clickable,
                        boolean longClickable,
                        boolean visibleToUser,
                        float confidence) {
        this.label = label == null ? "" : label;
        this.packageName = packageName;
        this.componentName = componentName;
        this.className = className == null ? "" : className;
        this.viewId = viewId;
        this.bounds = bounds == null ? new Rect() : new Rect(bounds);
        this.centerX = this.bounds.centerX();
        this.centerY = this.bounds.centerY();
        this.clickable = clickable;
        this.longClickable = longClickable;
        this.visibleToUser = visibleToUser;
        this.confidence = confidence;
    }

    public String identityKey() {
        if (componentName != null && !componentName.isEmpty()) {
            return "component:" + componentName;
        }
        if (viewId != null && !viewId.isEmpty()) {
            return "viewId:" + viewId + "|label:" + label;
        }
        return "label:" + label + "|center:" + centerX + "," + centerY;
    }

    public String describe() {
        return "label=\"" + clean(label) + \"\""
                + " | package=" + String.valueOf(packageName)
                + " | component=" + String.valueOf(componentName)
                + " | class=" + className
                + " | id=" + String.valueOf(viewId)
                + " | clickable=" + clickable
                + " | longClickable=" + longClickable
                + " | visible=" + visibleToUser
                + " | bounds=" + bounds.left + "," + bounds.top + ","
                + bounds.right + "," + bounds.bottom
                + " | center=" + centerX + "," + centerY
                + " | confidence=" + String.format(java.util.Locale.US, "%.2f", confidence);
    }

    private String clean(String value) {
        return value.replace("\n", " ").replace("\r", " ").trim();
    }
}
