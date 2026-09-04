package com.aelshahat.homeorganizer;

import android.graphics.Rect;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public interface LauncherAdapter {
    boolean supports(String packageName);
    String name();
    List<HomeShortcut> findShortcuts(AccessibilityNodeInfo root, String launcherPackage);

    static LauncherAdapter forPackage(String packageName) {
        if (packageName != null && packageName.contains("launcher3")) {
            return new Launcher3Adapter();
        }
        return new GenericLauncherAdapter();
    }

    final class Launcher3Adapter implements LauncherAdapter {
        @Override public boolean supports(String packageName) {
            return packageName != null && packageName.contains("launcher3");
        }
        @Override public String name() { return "Launcher3-compatible"; }
        @Override public List<HomeShortcut> findShortcuts(AccessibilityNodeInfo root, String launcherPackage) {
            return ShortcutScanner.scan(root, launcherPackage, true);
        }
    }

    final class GenericLauncherAdapter implements LauncherAdapter {
        @Override public boolean supports(String packageName) { return true; }
        @Override public String name() { return "Generic launcher"; }
        @Override public List<HomeShortcut> findShortcuts(AccessibilityNodeInfo root, String launcherPackage) {
            return ShortcutScanner.scan(root, launcherPackage, false);
        }
    }

    final class ShortcutScanner {
        private ShortcutScanner() {}

        static List<HomeShortcut> scan(AccessibilityNodeInfo root, String launcherPackage, boolean launcher3) {
            Map<String, HomeShortcut> unique = new LinkedHashMap<>();
            visit(root, launcherPackage, launcher3, unique);
            return new ArrayList<>(unique.values());
        }

        private static void visit(AccessibilityNodeInfo node, String launcherPackage,
                                  boolean launcher3, Map<String, HomeShortcut> unique) {
            if (node == null) return;
            CharSequence text = node.getText();
            CharSequence desc = node.getContentDescription();
            String label = text != null && text.length() > 0 ? text.toString()
                    : (desc != null ? desc.toString() : "");
            Rect bounds = new Rect();
            node.getBoundsInScreen(bounds);
            String cls = String.valueOf(node.getClassName());
            String id = node.getViewIdResourceName();

            boolean hasLabel = !label.trim().isEmpty();
            boolean validBounds = bounds.width() > 0 && bounds.height() > 0
                    && bounds.width() <= 500 && bounds.height() <= 500;
            boolean launcherUiNoise = isLauncherUiNoise(label, id, cls);
            boolean textView = cls.contains("TextView");
            boolean clickable = node.isClickable();
            boolean longClickable = node.isLongClickable();

            float confidence = 0f;
            if (hasLabel) confidence += .20f;
            if (node.isVisibleToUser()) confidence += .15f;
            if (validBounds) confidence += .15f;
            if (clickable) confidence += .20f;
            if (longClickable) confidence += .20f;
            if (textView) confidence += .10f;
            if (launcher3 && longClickable && textView) confidence += .10f;
            if (launcherUiNoise) confidence -= .55f;

            // Launcher3 on the target device exposes home app icons as long-clickable
            // TextViews. Generic launchers use a more conservative clickable/label rule.
            boolean likely = hasLabel && validBounds && node.isVisibleToUser()
                    && !launcherUiNoise
                    && ((launcher3 && longClickable && textView)
                    || (!launcher3 && (longClickable || clickable) && textView));

            if (likely && confidence >= .70f) {
                String component = extractComponent(node);
                HomeShortcut shortcut = new HomeShortcut(
                        clean(label), launcherPackage, component, cls, id, bounds,
                        clickable, longClickable, node.isVisibleToUser(), Math.min(1f, confidence));
                String key = shortcut.identityKey();
                HomeShortcut old = unique.get(key);
                if (old == null || shortcut.confidence > old.confidence) unique.put(key, shortcut);
            }

            for (int i = 0; i < node.getChildCount(); i++) {
                visit(node.getChild(i), launcherPackage, launcher3, unique);
            }
        }

        private static boolean isLauncherUiNoise(String label, String id, String cls) {
            String s = (label + " " + String.valueOf(id)).toLowerCase(java.util.Locale.US);
            String c = cls.toLowerCase(java.util.Locale.US);
            String[] noise = {"search", "weather", "clock", "date", "calendar", "settings",
                    "quick_event", "quick_event_title", "quick_event_weather_temp", "page",
                    "hotseat", "all_apps", "dock", "navigation", "overview", "home"};
            for (String n : noise) if (s.contains(n)) return true;
            return c.contains("searchview") || c.contains("edittext");
        }

        private static String extractComponent(AccessibilityNodeInfo node) {
            // AccessibilityNodeInfo does not expose the launch Intent component directly.
            // Keep the field nullable rather than inventing a component from coordinates.
            return null;
        }

        private static String clean(String value) {
            return value.replace("\n", " ").replace("\r", " ").trim();
        }
    }
}
