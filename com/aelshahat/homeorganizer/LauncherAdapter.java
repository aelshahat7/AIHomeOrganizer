package com.aelshahat.homeorganizer;

import android.graphics.Rect;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public interface LauncherAdapter {
    boolean supports(String packageName);
    String name();
    List<HomeShortcut> findShortcuts(AccessibilityNodeInfo root, String launcherPackage, int pageIndex);

    static LauncherAdapter forPackage(String packageName) {
        if (packageName != null && packageName.contains("launcher3")) return new Launcher3Adapter();
        return new GenericLauncherAdapter();
    }

    final class Launcher3Adapter implements LauncherAdapter {
        @Override public boolean supports(String packageName) { return packageName != null && packageName.contains("launcher3"); }
        @Override public String name() { return "crDroid Launcher3"; }
        @Override public List<HomeShortcut> findShortcuts(AccessibilityNodeInfo root, String launcherPackage, int pageIndex) {
            return ShortcutScanner.scan(root, launcherPackage, pageIndex, true);
        }
    }

    final class GenericLauncherAdapter implements LauncherAdapter {
        @Override public boolean supports(String packageName) { return true; }
        @Override public String name() { return "Generic launcher"; }
        @Override public List<HomeShortcut> findShortcuts(AccessibilityNodeInfo root, String launcherPackage, int pageIndex) {
            return ShortcutScanner.scan(root, launcherPackage, pageIndex, false);
        }
    }

    final class ShortcutScanner {
        private ShortcutScanner() {}

        static List<HomeShortcut> scan(AccessibilityNodeInfo root, String launcherPackage,
                                       int pageIndex, boolean launcher3) {
            Map<String, HomeShortcut> unique = new LinkedHashMap<>();
            Rect screen = new Rect();
            if (root != null) root.getBoundsInScreen(screen);
            if (screen.width() <= 0 || screen.height() <= 0) screen.set(0, 0, 1, 1);
            visit(root, launcherPackage, pageIndex, launcher3, screen, unique);
            List<HomeShortcut> result = new ArrayList<>(unique.values());
            if (launcher3) result = inferCells(result);
            return result;
        }

        private static void visit(AccessibilityNodeInfo node, String launcherPackage, int pageIndex,
                                  boolean launcher3, Rect screen, Map<String, HomeShortcut> unique) {
            if (node == null) return;
            CharSequence text = node.getText();
            CharSequence desc = node.getContentDescription();
            String label = text != null && text.length() > 0 ? text.toString() : (desc != null ? desc.toString() : "");
            Rect bounds = new Rect(); node.getBoundsInScreen(bounds);
            String cls = String.valueOf(node.getClassName());
            String id = node.getViewIdResourceName();
            boolean visible = node.isVisibleToUser();
            boolean hasLabel = !label.trim().isEmpty();
            boolean validBounds = bounds.width() > 0 && bounds.height() > 0
                    && bounds.width() <= Math.max(500, screen.width())
                    && bounds.height() <= Math.max(500, screen.height());
            boolean noise = isLauncherUiNoise(label, id, cls);
            boolean textView = cls.contains("TextView");
            boolean clickable = node.isClickable();
            boolean longClickable = node.isLongClickable();
            boolean hotseat = looksLikeHotseat(bounds, screen, id);

            float confidence = 0f;
            if (hasLabel) confidence += .20f;
            if (visible) confidence += .15f;
            if (validBounds) confidence += .15f;
            if (clickable) confidence += .20f;
            if (longClickable) confidence += .20f;
            if (textView) confidence += .10f;
            if (launcher3 && longClickable && textView) confidence += .10f;
            if (hotseat) confidence += .03f;
            if (noise) confidence -= .60f;

            boolean likely = hasLabel && validBounds && visible && !noise
                    && ((launcher3 && longClickable && textView)
                    || (!launcher3 && (longClickable || clickable) && textView));
            if (likely && confidence >= .70f) {
                String reason = "label+visible+bounds+" + (clickable ? "clickable+" : "")
                        + (longClickable ? "longClickable+" : "") + "TextView";
                if (launcher3) reason += "+Launcher3-long-click-pattern";
                if (hotseat) reason += "+hotseat-position";
                HomeShortcut shortcut = new HomeShortcut(clean(label), launcherPackage,
                        extractComponent(node), cls, id, bounds, clickable, longClickable,
                        visible, Math.min(1f, confidence), reason, pageIndex, hotseat);
                String key = shortcut.identityKey();
                HomeShortcut old = unique.get(key);
                if (old == null || shortcut.confidence > old.confidence) unique.put(key, shortcut);
            }
            for (int i = 0; i < node.getChildCount(); i++) visit(node.getChild(i), launcherPackage, pageIndex, launcher3, screen, unique);
        }

        private static List<HomeShortcut> inferCells(List<HomeShortcut> input) {
            List<HomeShortcut> regular = new ArrayList<>();
            for (HomeShortcut s : input) if (!s.hotseat) regular.add(s);
            if (regular.isEmpty()) return input;
            List<Integer> xs = clusterCenters(regular, true, 30);
            List<Integer> ys = clusterCenters(regular, false, 30);
            List<HomeShortcut> out = new ArrayList<>();
            for (HomeShortcut s : input) {
                if (s.hotseat) { out.add(s); continue; }
                int x = nearestIndex(xs, s.centerX);
                int y = nearestIndex(ys, s.centerY);
                out.add(s.withCell(x, y));
            }
            return out;
        }

        private static List<Integer> clusterCenters(List<HomeShortcut> list, boolean xAxis, int tolerance) {
            List<Integer> values = new ArrayList<>();
            for (HomeShortcut s : list) values.add(xAxis ? s.centerX : s.centerY);
            values.sort(Comparator.naturalOrder());
            List<Integer> clusters = new ArrayList<>();
            for (int v : values) {
                if (clusters.isEmpty() || Math.abs(v - clusters.get(clusters.size() - 1)) > tolerance) clusters.add(v);
                else clusters.set(clusters.size() - 1, (clusters.get(clusters.size() - 1) + v) / 2);
            }
            return clusters;
        }

        private static int nearestIndex(List<Integer> values, int value) {
            int best = 0; int distance = Integer.MAX_VALUE;
            for (int i = 0; i < values.size(); i++) { int d = Math.abs(values.get(i) - value); if (d < distance) { distance = d; best = i; } }
            return best;
        }

        private static boolean looksLikeHotseat(Rect b, Rect screen, String id) {
            String sid = String.valueOf(id).toLowerCase(Locale.US);
            if (sid.contains("hotseat") || sid.contains("dock")) return true;
            float top = (float) (b.top - screen.top) / Math.max(1, screen.height());
            float bottom = (float) (b.bottom - screen.top) / Math.max(1, screen.height());
            return top > .78f && bottom <= 1.05f;
        }

        private static boolean isLauncherUiNoise(String label, String id, String cls) {
            String s = (label + " " + String.valueOf(id)).toLowerCase(Locale.US);
            String c = cls.toLowerCase(Locale.US);
            String[] noise = {"search", "weather", "clock", "date", "quick_event", "quick_event_title",
                    "quick_event_weather_temp", "page_indicator", "all_apps", "navigation", "overview", "edit_text"};
            for (String n : noise) if (s.contains(n)) return true;
            return c.contains("searchview") || c.contains("edittext");
        }

        private static String extractComponent(AccessibilityNodeInfo node) { return null; }
        private static String clean(String value) { return value.replace("\n", " ").replace("\r", " ").trim(); }
    }
}
