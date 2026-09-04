package com.aelshahat.homeorganizer;

import android.graphics.Rect;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.List;

public final class FolderController {
    public interface Callback { void onResult(String code, String detail); }
    private final HomeAccessibilityService service;
    public FolderController(HomeAccessibilityService service) { this.service = service; }

    public void createFolder(HomeShortcut source, HomeShortcut target, Callback callback) {
        if (service == null || source == null || target == null) {
            callback.onResult("FOLDER_CREATION_UNSUPPORTED", "Missing service or shortcuts"); return;
        }
        AccessibilityNodeInfo root = service.getRootInActiveWindow();
        if (root == null) { callback.onResult("FOLDER_CREATION_UNSUPPORTED", "No active launcher window"); return; }
        String pkg = String.valueOf(root.getPackageName());
        if (!pkg.equals(service.getLastLauncherPackage())) { root.recycle(); callback.onResult("TEST_FAILED_LAUNCHER_CHANGED", "Launcher changed"); return; }
        LauncherAdapter adapter = LauncherAdapter.forPackage(pkg);
        List<HomeShortcut> live = adapter.findShortcuts(root, pkg, source.pageIndex);
        root.recycle();
        HomeShortcut a = find(live, source);
        HomeShortcut b = find(live, target);
        service.appendDiagnostic("SOURCE_BEFORE: " + describe(a) + "\nTARGET_BEFORE: " + describe(b) + "\n");
        if (a == null || b == null) {
            callback.onResult("TEST_FAILED_SHORTCUT_NOT_FOUND", "Source or target is no longer visible after fresh page scan"); return;
        }
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
    }

    private HomeShortcut find(List<HomeShortcut> list, HomeShortcut wanted) {
        for (HomeShortcut s : list) {
            if (s.label.equalsIgnoreCase(wanted.label) && s.pageIndex == wanted.pageIndex
                    && s.hotseat == wanted.hotseat
                    && Math.abs(s.centerX - wanted.centerX) < 100
                    && Math.abs(s.centerY - wanted.centerY) < 100) return s;
        }
        return null;
    }

    private String describe(HomeShortcut s) { return s == null ? "missing" : s.describe(); }
}
