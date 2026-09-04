package com.aelshahat.homeorganizer;

import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.List;

public final class FolderController {
    public interface Callback { void onResult(String code, String detail); }
    private final HomeAccessibilityService service;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private static final int MAX_LIVE_SCAN_ATTEMPTS = 5;

    public FolderController(HomeAccessibilityService service) { this.service = service; }

    public void createFolder(HomeShortcut source, HomeShortcut target, Callback callback) {
        if (service == null || source == null || target == null) {
            callback.onResult("FOLDER_CREATION_UNSUPPORTED", "Missing service or shortcuts"); return;
        }
        findLivePair(source, target, callback, 0);
    }

    private void findLivePair(HomeShortcut source, HomeShortcut target, Callback callback, int attempt) {
        handler.postDelayed(() -> {
            AccessibilityNodeInfo root = service.getRootInActiveWindow();
            if (root == null) {
                if (attempt + 1 < MAX_LIVE_SCAN_ATTEMPTS) findLivePair(source, target, callback, attempt + 1);
                else callback.onResult("TEST_FAILED_LAUNCHER_UNAVAILABLE", "Launcher root unavailable during live folder scan");
                return;
            }

            String pkg = String.valueOf(root.getPackageName());
            if (!pkg.equals(service.getLastLauncherPackage())) {
                root.recycle();
                callback.onResult("TEST_FAILED_LAUNCHER_CHANGED", "Launcher changed");
                return;
            }

            LauncherAdapter adapter = LauncherAdapter.forPackage(pkg);
            List<HomeShortcut> live = adapter.findShortcuts(root, pkg, service.getCurrentPage());
            HomeShortcut a = find(live, source);
            HomeShortcut b = find(live, target);
            String liveSummary = summarize(live);
            service.appendDiagnostic("LIVE_SCAN attempt=" + (attempt + 1)
                    + " currentPage=" + service.getCurrentPage()
                    + " requestedPage=" + source.pageIndex
                    + " source=" + describe(a) + " target=" + describe(b) + "\n");
            root.recycle();

            if (a == null || b == null) {
                if (attempt + 1 < MAX_LIVE_SCAN_ATTEMPTS) {
                    handler.postDelayed(() -> findLivePair(source, target, callback, attempt + 1), 450);
                    return;
                }
                service.appendDiagnostic("LIVE_SCAN_LABELS=" + liveSummary + "\n");
                callback.onResult("TEST_FAILED_SHORTCUT_NOT_FOUND",
                        "Source or target not found after " + MAX_LIVE_SCAN_ATTEMPTS + " live scans; currentPage=" + service.getCurrentPage());
                return;
            }

            service.appendDiagnostic("SOURCE_BEFORE: " + describe(a) + "\nTARGET_BEFORE: " + describe(b) + "\n");
            if (a.hotseat || b.hotseat || a.pageIndex != b.pageIndex) {
                callback.onResult("FOLDER_CREATION_UNSUPPORTED", "Folder probe requires two visible shortcuts on the same normal page"); return;
            }

            service.appendDiagnostic("FOLDER_GESTURE source=" + a.label + "(" + a.centerX + "," + a.centerY + ") target=" + b.label + "(" + b.centerX + "," + b.centerY + ")\n");
            service.performFolderGesture(a.centerX, a.centerY, b.centerX, b.centerY, new GestureController.Callback() {
                @Override public void onSuccess() {
                    service.appendDiagnostic("GESTURE_DISPATCHED=true\n");
                    service.verifyFolderProbe(a, b, callback);
                }
                @Override public void onFailure(String reason) {
                    service.appendDiagnostic("GESTURE_DISPATCHED=false reason=" + reason + "\n");
                    callback.onResult("TEST_FAILED_VERIFICATION", reason);
                }
            });
        }, attempt == 0 ? 250 : 450);
    }

    private HomeShortcut find(List<HomeShortcut> list, HomeShortcut wanted) {
        HomeShortcut nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (HomeShortcut s : list) {
            if (!sameLabel(s.label, wanted.label) || s.hotseat != wanted.hotseat || s.hotseat) continue;
            double dx = s.centerX - wanted.centerX;
            double dy = s.centerY - wanted.centerY;
            double distance = dx * dx + dy * dy;
            if (s.pageIndex == wanted.pageIndex && distance < 140.0 * 140.0) return s;
            if (distance < nearestDistance && distance < 220.0 * 220.0) {
                nearest = s;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    private boolean sameLabel(String a, String b) {
        return normalize(a).equals(normalize(b));
    }

    private String normalize(String value) {
        if (value == null) return "";
        return value.replace("\u200B", "").replace("\u200C", "").replace("\u200D", "")
                .replace("\uFEFF", "").trim().toLowerCase(java.util.Locale.ROOT);
    }

    private String summarize(List<HomeShortcut> list) {
        StringBuilder b = new StringBuilder();
        int count = 0;
        for (HomeShortcut s : list) {
            if (s.hotseat) continue;
            if (count++ > 0) b.append(" | ");
            b.append(s.label).append("@").append(s.centerX).append(",").append(s.centerY);
            if (count >= 40) { b.append(" | ..."); break; }
        }
        return b.toString();
    }

    private String describe(HomeShortcut s) { return s == null ? "missing" : s.describe(); }
}
