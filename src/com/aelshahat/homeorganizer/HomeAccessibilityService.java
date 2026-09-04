package com.aelshahat.homeorganizer;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class HomeAccessibilityService extends AccessibilityService {
    private static final String TAG = "AIHomeOrganizer";
    private static HomeAccessibilityService instance;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final GestureController gestures = new GestureController(this);
    private final PageNavigator navigator = new PageNavigator(this);
    private final HomeScreenController homeController = new HomeScreenController(this);
    private final FolderController folderController = new FolderController(this);
    private List<HomeShortcut> lastShortcuts = new ArrayList<>();
    private List<HomePage> lastPages = new ArrayList<>();
    private String lastLauncherPackage, lastAdapterName;
    private boolean scanning;

    public interface TestCallback { void onResult(String result); }
    public interface ScanCallback { void onFinished(String code); }

    @Override protected void onServiceConnected() {
        super.onServiceConnected(); instance = this;
        AccessibilityServiceInfo info = getServiceInfo();
        if (info != null) {
            info.flags |= AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
            info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
            setServiceInfo(info);
        }
    }
    @Override public void onAccessibilityEvent(android.view.accessibility.AccessibilityEvent event) { }
    @Override public void onInterrupt() { navigator.cancel(); gestures.cancel(); scanning = false; }
    @Override public void onDestroy() { navigator.cancel(); gestures.cancel(); scanning = false; if (instance == this) instance = null; super.onDestroy(); }
    public static HomeAccessibilityService getInstance() { return instance; }
    public boolean canPerformGestureNow() { return gestures.canPerformGesture(); }
    public List<HomeShortcut> getShortcuts() { return Collections.unmodifiableList(new ArrayList<>(lastShortcuts)); }
    public List<HomePage> getPages() { return Collections.unmodifiableList(new ArrayList<>(lastPages)); }
    public String getLastLauncherPackage() { return lastLauncherPackage; }
    public String getLastAdapterName() { return lastAdapterName; }
    public boolean isScanning() { return scanning; }

    public void scanHomeScreen() { scanHomeScreen(null); }
    public void scanHomeScreen(ScanCallback callback) {
        if (scanning) { if (callback != null) callback.onFinished("SCAN_ALREADY_RUNNING"); return; }
        scanning = true; saveReport("SCAN_STARTED\n");
        if (!performGlobalAction(GLOBAL_ACTION_HOME)) { scanning = false; saveReport("SCAN_FAILED\nCould not request Home Screen.\n"); if (callback != null) callback.onFinished("SCAN_FAILED"); return; }
        handler.postDelayed(() -> scanPageLoop(0, new HashSet<>(), new ArrayList<>(), callback), 900);
    }

    private void scanPageLoop(final int pageIndex, final Set<String> seen, final List<HomePage> pages, final ScanCallback callback) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) { finishScan(pages, "SCAN_FAILED", callback); return; }
        String pkg = String.valueOf(root.getPackageName());
        if (lastLauncherPackage != null && !lastLauncherPackage.equals(pkg)) { root.recycle(); finishScan(pages, "SCAN_FAILED_LAUNCHER_CHANGED", callback); return; }
        LauncherAdapter adapter = LauncherAdapter.forPackage(pkg); lastLauncherPackage = pkg; lastAdapterName = adapter.name();
        List<HomeShortcut> shortcuts = adapter.findShortcuts(root, pkg, pageIndex);
        String signature = makePageSignature(shortcuts, root); root.recycle();
        if (seen.contains(signature)) { appendReport("PAGE_LOOP_DETECTED page=" + pageIndex + "\n"); finishScan(pages, "MULTI_PAGE_SCAN_COMPLETE", callback); return; }
        seen.add(signature); pages.add(new HomePage(pageIndex, signature, pageIndex == 0, shortcuts));
        appendReport("PAGE_SCAN_COMPLETE page=" + pageIndex + " shortcuts=" + shortcuts.size() + "\n");
        for (HomeShortcut s : shortcuts) appendReport("  " + s.describe() + "\n");
        if (pageIndex >= 20) { finishScan(pages, "PAGE_LIMIT_REACHED", callback); return; }
        AccessibilityNodeInfo navRoot = getRootInActiveWindow();
        if (navRoot == null) { finishScan(pages, "MULTI_PAGE_SCAN_UNSUPPORTED", callback); return; }
        navigator.next(navRoot, new PageNavigator.Callback() {
            @Override public void onSuccess() { handler.postDelayed(() -> scanPageLoop(pageIndex + 1, seen, pages, callback), 650); }
            @Override public void onFailure(String reason) { appendReport("PAGE_NAVIGATION_FAILED page=" + pageIndex + " reason=" + reason + "\n"); finishScan(pages, "MULTI_PAGE_SCAN_UNSUPPORTED", callback); }
        });
        navRoot.recycle();
    }

    private String makePageSignature(List<HomeShortcut> list, AccessibilityNodeInfo root) {
        StringBuilder b = new StringBuilder(String.valueOf(root.getPackageName()));
        for (HomeShortcut s : list) if (!s.hotseat) b.append('|').append(s.label).append('@').append(s.centerX).append(',').append(s.centerY);
        return b.toString();
    }

    private void finishScan(List<HomePage> pages, String code, ScanCallback callback) {
        lastPages = new ArrayList<>(pages); lastShortcuts = new ArrayList<>(); for (HomePage p : pages) lastShortcuts.addAll(p.shortcuts); scanning = false;
        appendReport("\n" + code + "\nPages detected: " + pages.size() + "\nTotal shortcuts: " + lastShortcuts.size() + "\n");
        Log.i(TAG, code + " pages=" + pages.size() + " shortcuts=" + lastShortcuts.size());
        if (callback != null) callback.onFinished(code);
    }

    public void navigateToPage(int targetPage, final TestCallback callback) {
        if (targetPage <= 0) { callback.onResult("PAGE_READY"); return; }
        navigateStep(0, targetPage, callback);
    }
    private void navigateStep(final int current, final int target, final TestCallback callback) {
        if (current >= target) { callback.onResult("PAGE_READY"); return; }
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) { callback.onResult("TEST_FAILED_LAUNCHER_CHANGED"); return; }
        navigator.next(root, new PageNavigator.Callback() {
            @Override public void onSuccess() { handler.postDelayed(() -> navigateStep(current + 1, target, callback), 650); }
            @Override public void onFailure(String reason) { callback.onResult("TEST_FAILED_VERIFICATION"); }
        });
        root.recycle();
    }

    public void runSafeDragTest(final HomeShortcut selected, final TestCallback callback) {
        if (selected == null) { callback.onResult("TEST_FAILED_SHORTCUT_NOT_FOUND"); return; }
        if (!canPerformGestureNow()) { callback.onResult("TEST_FAILED_GESTURE_UNAVAILABLE"); return; }
        navigateToPage(selected.pageIndex, new TestCallback() {
            @Override public void onResult(String pageResult) {
                if (!"PAGE_READY".equals(pageResult)) { callback.onResult(pageResult); return; }
                AccessibilityNodeInfo root = getRootInActiveWindow();
                if (root == null) { callback.onResult("TEST_FAILED_LAUNCHER_CHANGED"); return; }
                String pkg = String.valueOf(root.getPackageName());
                List<HomeShortcut> current = LauncherAdapter.forPackage(pkg).findShortcuts(root, pkg, selected.pageIndex); root.recycle();
                if (lastLauncherPackage == null || !lastLauncherPackage.equals(pkg)) { callback.onResult("TEST_FAILED_LAUNCHER_CHANGED"); return; }
                HomeShortcut live = homeController.findShortcut(current, selected);
                if (live == null) { callback.onResult("TEST_FAILED_SHORTCUT_NOT_FOUND"); return; }
                appendReport("GESTURE_STARTED safe-probe label=" + live.label + " page=" + live.pageIndex + "\n");
                gestures.safeDrag(live.centerX, live.centerY, 45, 0, new GestureController.Callback() {
                    @Override public void onSuccess() { appendReport("GESTURE_COMPLETED\n"); handler.postDelayed(() -> verifyAfterGesture(live, callback), 800); }
                    @Override public void onFailure(String reason) { appendReport("GESTURE_FAILED reason=" + reason + "\n"); callback.onResult(reason.contains("CAPABILITY") ? "TEST_FAILED_GESTURE_UNAVAILABLE" : "TEST_FAILED_VERIFICATION"); }
                });
            }
        });
    }

    private void verifyAfterGesture(HomeShortcut before, TestCallback callback) {
        AccessibilityNodeInfo root = getRootInActiveWindow(); if (root == null) { callback.onResult("TEST_FAILED_VERIFICATION"); return; }
        String pkg = String.valueOf(root.getPackageName()); List<HomeShortcut> after = LauncherAdapter.forPackage(pkg).findShortcuts(root, pkg, before.pageIndex); root.recycle();
        HomeShortcut match = null; for (HomeShortcut s : after) if (s.label.equalsIgnoreCase(before.label) && s.hotseat == before.hotseat) { match = s; break; }
        String result = match == null ? "TEST_FAILED_VERIFICATION" : ((match.centerX != before.centerX || match.centerY != before.centerY) ? "TEST_SUCCESS_DRAG_DETECTED" : "TEST_SUCCESS_NO_MOVE");
        appendReport("VERIFICATION RESULT: " + result + "\nBefore: " + before.describe() + "\nAfter: " + (match == null ? "missing" : match.describe()) + "\n");
        lastShortcuts = after; callback.onResult(result);
    }

    public void performFolderGesture(int sx, int sy, int tx, int ty, GestureController.Callback cb) { gestures.dragAfterLongPress(sx, sy, tx, ty, 1100, cb); }
    public void createFolder(final HomeShortcut source, final HomeShortcut target, final FolderController.Callback cb) {
        if (source == null || target == null || source.pageIndex != target.pageIndex) { cb.onResult("FOLDER_CREATION_UNSUPPORTED", "Both shortcuts must be on the same scanned page"); return; }
        navigateToPage(source.pageIndex, result -> { if (!"PAGE_READY".equals(result)) cb.onResult(result, "Could not reach shortcut page"); else folderController.createFolder(source, target, cb); });
    }
    public void verifyFolderProbe(HomeShortcut source, HomeShortcut target, FolderController.Callback callback) {
        handler.postDelayed(() -> {
            AccessibilityNodeInfo root = getRootInActiveWindow(); if (root == null) { callback.onResult("TEST_FAILED_VERIFICATION", "No launcher window after folder probe"); return; }
            String pkg = String.valueOf(root.getPackageName()); boolean folderLike = containsFolderNode(root); List<HomeShortcut> after = LauncherAdapter.forPackage(pkg).findShortcuts(root, pkg, source.pageIndex); root.recycle();
            if (folderLike && (hasLabel(after, source.label) || hasLabel(after, target.label))) callback.onResult("FOLDER_CREATED", "Folder-like launcher state detected");
            else callback.onResult("FOLDER_CREATION_UNSUPPORTED", "Folder state could not be verified safely");
        }, 900);
    }
    private boolean containsFolderNode(AccessibilityNodeInfo n) { if (n == null) return false; String c=String.valueOf(n.getClassName()).toLowerCase(java.util.Locale.US), d=String.valueOf(n.getContentDescription()).toLowerCase(java.util.Locale.US); if (c.contains("folder")||d.contains("folder")) return true; for(int i=0;i<n.getChildCount();i++) if(containsFolderNode(n.getChild(i))) return true; return false; }
    private boolean hasLabel(List<HomeShortcut> list,String label){for(HomeShortcut s:list)if(s.label.equalsIgnoreCase(label))return true;return false;}
    public String getSavedReport() { return getSharedPreferences("diagnostic", MODE_PRIVATE).getString("last_report", ""); }
    private void saveReport(String s) { getSharedPreferences("diagnostic", MODE_PRIVATE).edit().putString("last_report", s).apply(); }
    private void appendReport(String s) { saveReport(getSavedReport() + s); }
}
