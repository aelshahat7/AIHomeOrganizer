package com.aelshahat.homeorganizer;

import android.view.accessibility.AccessibilityNodeInfo;
import java.util.List;

public final class HomeScreenController {
    private final HomeAccessibilityService service;
    public HomeScreenController(HomeAccessibilityService service) { this.service = service; }

    public boolean launcherMatches(String packageName) {
        return service != null && packageName != null && packageName.equals(service.getLastLauncherPackage());
    }

    public HomeShortcut findShortcut(List<HomeShortcut> shortcuts, HomeShortcut wanted) {
        if (wanted == null) return null;
        for (HomeShortcut s : shortcuts) if (same(s, wanted)) return s;
        return null;
    }

    public boolean shortcutStillVisible(AccessibilityNodeInfo root, HomeShortcut wanted) {
        if (root == null || wanted == null) return false;
        List<HomeShortcut> current = LauncherAdapter.forPackage(String.valueOf(root.getPackageName()))
                .findShortcuts(root, String.valueOf(root.getPackageName()), wanted.pageIndex);
        return findShortcut(current, wanted) != null;
    }

    private boolean same(HomeShortcut a, HomeShortcut b) {
        if (a.viewId != null && !a.viewId.isEmpty() && a.viewId.equals(b.viewId)
                && a.label.equalsIgnoreCase(b.label)) return true;
        return a.label.equalsIgnoreCase(b.label)
                && a.hotseat == b.hotseat
                && a.pageIndex == b.pageIndex
                && Math.abs(a.centerX - b.centerX) < 100
                && Math.abs(a.centerY - b.centerY) < 100;
    }
}
