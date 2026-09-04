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
            // Launcher3 can expose the same visible shortcut through a different accessibility
            // node shape after navigation. Fall back to direct label/bounds lookup instead of
            // treating a scanner miss as proof that the shortcut disappeared.
            if (a == null) a = findByVisibleLabel(root, source.label, source.pageIndex, source.centerX, source.centerY);
            if (b == null) b = findByVisibleLabel(root, target.label, target.pageIndex, target.centerX, target.centerY);
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
            final HomeShortcut liveSource = a;
            final HomeShortcut liveTarget = b;
            service.appendDiagnostic("SOURCE_BEFORE: " + liveSource.describe() + "\nTARGET_BEFORE: " + liveTarget.describe() + "\n");
            service.appendDiagnostic("GESTURE_STARTED source=" + liveSource.label + " target=" + liveTarget.label + " page=" + liveSource.pageIndex
                    + " cell=" + liveSource.cellX + "," + liveSource.cellY + " -> " + liveTarget.cellX + "," + liveTarget.cellY + "\n");
            service.performFolderGesture(liveSource.centerX, liveSource.centerY, liveTarget.centerX, liveTarget.centerY, new GestureController.Callback() {
                @Override public void onSuccess() {
                    service.appendDiagnostic("GESTURE_COMPLETED\nVERIFICATION_START\n");
                    verify(liveSource, liveTarget, callback, 0);
                }
                @Override public void onFailure(String reason) {
                    service.appendDiagnostic("GESTURE_FAILED reason=" + reason + "\n");
                    callback.onResult("FAILED", reason);
                }
            });
        }, attempt == 0 ? 250 : 450);
    }

    private HomeShortcut findByVisibleLabel(AccessibilityNodeInfo n, String wanted, int page, int expectedX, int expectedY) {
        if (n == null) return null;
        String label = normalize(n.getText());
        if (label.isEmpty()) label = normalize(n.getContentDescription());
        Rect r = new Rect(); n.getBoundsInScreen(r);
        if (normalize(wanted).equals(label) && n.isVisibleToUser() && r.width() > 0 && r.height() > 0) {
            boolean hotseat = looksLikeHotseat(n, r);
            if (!hotseat && Math.abs(r.centerX() - expectedX) <= 220 && Math.abs(r.centerY() - expectedY) <= 220) {
                String liveLabel = n.getText() != null ? n.getText().toString() : n.getContentDescription().toString();
                return new HomeShortcut(liveLabel, String.valueOf(n.getPackageName()), null, String.valueOf(n.getClassName()),
                        n.getViewIdResourceName(), r, n.isClickable(), n.isLongClickable(), n.isVisibleToUser(),
                        1f, "live-direct-label-fallback", page, false, -1, -1);
            }
        }
        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo c = n.getChild(i);
            HomeShortcut found = findByVisibleLabel(c, wanted, page, expectedX, expectedY);
            if (c != null) c.recycle();
            if (found != null) return found;
        }
        return null;
    }

    private boolean looksLikeHotseat(AccessibilityNodeInfo n, Rect b) {
        String id = normalize(n.getViewIdResourceName());
        if (id.contains("hotseat") || id.contains("dock")) return true;
        AccessibilityNodeInfo root = service.getRootInActiveWindow();
        if (root == null) return false;
        Rect screen = new Rect(); root.getBoundsInScreen(screen); root.recycle();
        float top = (float)(b.top - screen.top) / Math.max(1, screen.height());
        return top > .78f;
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
