package com.aelshahat.homeorganizer;

import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.List;

/** crDroid Launcher3 folder bridge. Uses normal Accessibility drag/drop only. */
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
            if (!pkg.equals(service.getLastLauncherPackage()) || service.getCurrentPage() != source.pageIndex) {
                root.recycle();
                callback.onResult("TEST_FAILED_LAUNCHER_CHANGED", "Launcher/page state is not the requested crDroid Launcher3 page");
                return;
            }
            List<HomeShortcut> live = LauncherAdapter.forPackage(pkg).findShortcuts(root, pkg, service.getCurrentPage());
            HomeShortcut a = find(live, source), b = find(live, target);
            service.appendDiagnostic("LIVE_SCAN attempt=" + (attempt + 1) + " currentPage=" + service.getCurrentPage()
                    + " requestedPage=" + source.pageIndex + " source=" + describe(a) + " target=" + describe(b) + "\n");
            root.recycle();
            if (a == null || b == null) {
                if (attempt + 1 < MAX_LIVE_SCAN_ATTEMPTS) { findLivePair(source, target, callback, attempt + 1); return; }
                callback.onResult("TEST_FAILED_SHORTCUT_NOT_FOUND", "Source or target not found after " + MAX_LIVE_SCAN_ATTEMPTS + " live scans");
                return;
            }
            if (a.hotseat || b.hotseat || a.pageIndex != b.pageIndex) {
                callback.onResult("FOLDER_CREATION_UNSUPPORTED", "Folder creation requires two normal shortcuts on the same page"); return;
            }
            service.appendDiagnostic("SOURCE_BEFORE: " + a.describe() + "\nTARGET_BEFORE: " + b.describe() + "\n");
            service.appendDiagnostic("GESTURE_STARTED source=" + a.label + " target=" + b.label + " page=" + a.pageIndex
                    + " cell=" + a.cellX + "," + a.cellY + " -> " + b.cellX + "," + b.cellY + "\n");
            service.performFolderGesture(a.centerX, a.centerY, b.centerX, b.centerY, new GestureController.Callback() {
                @Override public void onSuccess() {
                    service.appendDiagnostic("GESTURE_COMPLETED\nVERIFICATION_START\n");
                    verify(a, b, callback, 0);
                }
                @Override public void onFailure(String reason) {
                    service.appendDiagnostic("GESTURE_FAILED reason=" + reason + "\n");
                    callback.onResult("FAILED", reason);
                }
            });
        }, attempt == 0 ? 250 : 450);
    }

    private void verify(HomeShortcut source, HomeShortcut target, Callback callback, int attempt) {
        handler.postDelayed(() -> {
            AccessibilityNodeInfo root = service.getRootInActiveWindow();
            if (root == null) { callback.onResult("FOLDER_VERIFICATION_FAILED", "Launcher root unavailable"); return; }
            String pkg = String.valueOf(root.getPackageName());
            boolean explicit = containsFolderNode(root);
            boolean sourceVisible = containsLabel(root, source.label);
            boolean targetVisible = containsLabel(root, target.label);
            boolean folderNearTarget = containsFolderNodeNear(root, target.centerX, target.centerY, 180);
            service.appendDiagnostic("VERIFICATION_READ attempt=" + (attempt + 1)
                    + " explicitFolder=" + explicit + " folderNearTarget=" + folderNearTarget
                    + " sourceVisible=" + sourceVisible + " targetVisible=" + targetVisible + " package=" + pkg + "\n");
            root.recycle();
            if (explicit || folderNearTarget) {
                service.appendDiagnostic("VERIFICATION_SUCCESS folder-node-detected\n");
                callback.onResult("FOLDER_CREATED", "Verified Launcher3 folder node"); return;
            }
            if (attempt < 2) { verify(source, target, callback, attempt + 1); return; }
            service.appendDiagnostic("VERIFICATION_FAILED no-folder-node\n");
            callback.onResult("FOLDER_VERIFICATION_FAILED", "No reliable Launcher3 FolderIcon/folder node was exposed after 3 reads");
        }, attempt == 0 ? 1400 : 500);
    }

    private HomeShortcut find(List<HomeShortcut> list, HomeShortcut wanted) {
        HomeShortcut nearest = null; double best = Double.MAX_VALUE;
        for (HomeShortcut s : list) {
            if (s.hotseat || !normalize(s.label).equals(normalize(wanted.label))) continue;
            double dx = s.centerX - wanted.centerX, dy = s.centerY - wanted.centerY;
            double d = dx * dx + dy * dy;
            if (s.pageIndex == wanted.pageIndex && d < 140 * 140) return s;
            if (d < best && d < 220 * 220) { best = d; nearest = s; }
        }
        return nearest;
    }

    private boolean containsFolderNode(AccessibilityNodeInfo n) {
        if (n == null) return false;
        String x = lower(n.getClassName()) + " " + lower(n.getViewIdResourceName()) + " " + lower(n.getContentDescription());
        if (x.contains("foldericon") || x.contains("folder_icon") || x.contains("folder")) return true;
        for (int i=0;i<n.getChildCount();i++) { AccessibilityNodeInfo c=n.getChild(i); boolean hit=containsFolderNode(c); if(c!=null)c.recycle(); if(hit)return true; }
        return false;
    }

    private boolean containsFolderNodeNear(AccessibilityNodeInfo n, int x, int y, int radius) {
        if (n == null) return false;
        Rect r=new Rect(); n.getBoundsInScreen(r);
        String q=lower(n.getClassName())+" "+lower(n.getViewIdResourceName())+" "+lower(n.getContentDescription());
        if ((q.contains("folder") || q.contains("foldericon")) && Math.abs(r.centerX()-x)<=radius && Math.abs(r.centerY()-y)<=radius) return true;
        for(int i=0;i<n.getChildCount();i++){AccessibilityNodeInfo c=n.getChild(i);boolean hit=containsFolderNodeNear(c,x,y,radius);if(c!=null)c.recycle();if(hit)return true;}
        return false;
    }

    private boolean containsLabel(AccessibilityNodeInfo n, String label) {
        if (n == null) return false;
        if (normalize(n.getText()).equals(normalize(label)) || normalize(n.getContentDescription()).equals(normalize(label))) return true;
        for(int i=0;i<n.getChildCount();i++){AccessibilityNodeInfo c=n.getChild(i);boolean hit=containsLabel(c,label);if(c!=null)c.recycle();if(hit)return true;}
        return false;
    }

    private String normalize(CharSequence value) { return value == null ? "" : value.toString().replace("\u200B","").replace("\u200C","").replace("\u200D","").replace("\uFEFF","").trim().toLowerCase(java.util.Locale.ROOT); }
    private String lower(CharSequence value) { return normalize(value); }
    private String describe(HomeShortcut s) { return s == null ? "missing" : s.describe(); }
}
