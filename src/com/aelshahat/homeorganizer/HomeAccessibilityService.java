package com.aelshahat.homeorganizer;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.graphics.Rect;
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
    private int currentPage;

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
        currentPage = 0;
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
    public int getCurrentPage() { return currentPage; }

    public void scanHomeScreen() { scanHomeScreen(null); }
    public void scanHomeScreen(ScanCallback callback) {
        if (scanning) { if (callback != null) callback.onFinished("SCAN_ALREADY_RUNNING"); return; }
        scanning = true; currentPage = 0; saveReport("SCAN_STARTED\n");
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
        String signature = makePageSignature(shortcuts, root);
        root.recycle();
        if (seen.contains(signature)) {
            appendReport("PAGE_LOOP_DETECTED page=" + pageIndex + "\n");
            finishScan(pages, "MULTI_PAGE_SCAN_COMPLETE", callback);
            return;
        }
        currentPage = pageIndex;
        seen.add(signature); pages.add(new HomePage(pageIndex, signature, pageIndex == 0, shortcuts));
        appendReport("PAGE_SCAN_COMPLETE page=" + pageIndex + " shortcuts=" + shortcuts.size() + "\n");
        for (HomeShortcut s : shortcuts) appendReport("  " + s.describe() + "\n");
        if (pageIndex >= 20) { finishScan(pages, "PAGE_LIMIT_REACHED", callback); return; }
        AccessibilityNodeInfo navRoot = getRootInActiveWindow();
        if (navRoot == null) { finishScan(pages, "MULTI_PAGE_SCAN_UNSUPPORTED", callback); return; }
        navigator.next(navRoot, new PageNavigator.Callback() {
            @Override public void onSuccess() {
                currentPage = pageIndex + 1;
                handler.postDelayed(() -> scanPageLoop(pageIndex + 1, seen, pages, callback), 650);
            }
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
        lastPages = new ArrayList<>(pages);
        lastShortcuts = new ArrayList<>();
        for (HomePage p : pages) for (HomeShortcut s : p.shortcuts) if (!s.hotseat) lastShortcuts.add(s);
        scanning = false;
        appendReport("\n" + code + "\nPages detected: " + pages.size()
                + "\nRegular shortcuts: " + lastShortcuts.size()
                + "\nHotseat instances: " + countHotseat(pages)
                + "\nTotal scanned instances: " + countAll(pages) + "\n");
        Log.i(TAG, code + " pages=" + pages.size() + " regular=" + lastShortcuts.size() + " hotseat=" + countHotseat(pages));
        if (callback != null) callback.onFinished(code);
    }

    private int countHotseat(List<HomePage> pages) { int n=0; for(HomePage p:pages) for(HomeShortcut s:p.shortcuts) if(s.hotseat)n++; return n; }
    private int countAll(List<HomePage> pages) { int n=0; for(HomePage p:pages)n+=p.shortcuts.size(); return n; }

    /** Always reset the launcher to its Home page before a page-targeted action. */
    private void ensureHome(final TestCallback callback) {
        if (!performGlobalAction(GLOBAL_ACTION_HOME)) { callback.onResult("TEST_FAILED_LAUNCHER_UNAVAILABLE"); return; }
        currentPage = 0;
        handler.postDelayed(() -> {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) { callback.onResult("TEST_FAILED_LAUNCHER_UNAVAILABLE"); return; }
            String pkg = String.valueOf(root.getPackageName());
            root.recycle();
            if (!pkg.equals(lastLauncherPackage)) { callback.onResult("TEST_FAILED_LAUNCHER_CHANGED"); return; }
            currentPage = 0;
            callback.onResult("HOME_READY");
        }, 800);
    }

    public void navigateToPage(int targetPage, final TestCallback callback) {
        if (targetPage < 0 || targetPage >= Math.max(1, lastPages.size())) { callback.onResult("TEST_FAILED_PAGE_NOT_SCANNED"); return; }
        ensureHome(homeResult -> {
            if (!"HOME_READY".equals(homeResult)) { callback.onResult(homeResult); return; }
            if (targetPage == 0) { callback.onResult("PAGE_READY"); return; }
            navigateStep(0, targetPage, callback);
        });
    }

    private void navigateStep(final int current, final int target, final TestCallback callback) {
        if (current == target) { currentPage = target; callback.onResult("PAGE_READY"); return; }
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null || !String.valueOf(root.getPackageName()).equals(lastLauncherPackage)) {
            if (root != null) root.recycle(); callback.onResult("TEST_FAILED_LAUNCHER_CHANGED"); return;
        }
        navigator.next(root, new PageNavigator.Callback() {
            @Override public void onSuccess() { currentPage = current + 1; handler.postDelayed(() -> navigateStep(current + 1, target, callback), 700); }
            @Override public void onFailure(String reason) { callback.onResult("TEST_FAILED_PAGE_NAVIGATION: " + reason); }
        });
        root.recycle();
    }

    public void runSafeDragTest(final HomeShortcut selected, final TestCallback callback) {
        if (selected == null) { callback.onResult("TEST_FAILED_SHORTCUT_NOT_FOUND"); return; }
        if (!canPerformGestureNow()) { callback.onResult("TEST_FAILED_GESTURE_UNAVAILABLE"); return; }
        navigateToPage(selected.pageIndex, pageResult -> {
            if (!"PAGE_READY".equals(pageResult)) { callback.onResult(pageResult); return; }
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null || !String.valueOf(root.getPackageName()).equals(lastLauncherPackage)) {
                if (root != null) root.recycle(); callback.onResult("TEST_FAILED_LAUNCHER_CHANGED"); return;
            }
            String pkg = String.valueOf(root.getPackageName());
            List<HomeShortcut> current = LauncherAdapter.forPackage(pkg).findShortcuts(root, pkg, selected.pageIndex); root.recycle();
            HomeShortcut live = homeController.findShortcut(current, selected);
            if (live == null) { callback.onResult("TEST_FAILED_SHORTCUT_NOT_FOUND"); return; }
            appendReport("GESTURE_STARTED safe-probe label=" + live.label + " page=" + live.pageIndex + "\n");
            gestures.safeDrag(live.centerX, live.centerY, 45, 0, new GestureController.Callback() {
                @Override public void onSuccess() { appendReport("GESTURE_COMPLETED\n"); handler.postDelayed(() -> verifyAfterGesture(live, callback), 800); }
                @Override public void onFailure(String reason) { appendReport("GESTURE_FAILED reason=" + reason + "\n"); callback.onResult(reason.contains("CAPABILITY") ? "TEST_FAILED_GESTURE_UNAVAILABLE" : "TEST_FAILED_VERIFICATION"); }
            });
        });
    }

    private void verifyAfterGesture(HomeShortcut before, TestCallback callback) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null || !String.valueOf(root.getPackageName()).equals(lastLauncherPackage)) {
            if (root != null) root.recycle(); callback.onResult("TEST_FAILED_VERIFICATION"); return;
        }
        String pkg = String.valueOf(root.getPackageName()); List<HomeShortcut> after = LauncherAdapter.forPackage(pkg).findShortcuts(root, pkg, before.pageIndex); root.recycle();
        HomeShortcut match = null; for (HomeShortcut s : after) if (s.label.equalsIgnoreCase(before.label) && s.hotseat == before.hotseat) { match = s; break; }
        String result = match == null ? "TEST_FAILED_VERIFICATION" : ((match.centerX != before.centerX || match.centerY != before.centerY) ? "TEST_SUCCESS_DRAG_DETECTED" : "TEST_SUCCESS_NO_MOVE");
        appendReport("VERIFICATION RESULT: " + result + "\nBefore: " + before.describe() + "\nAfter: " + (match == null ? "missing" : match.describe()) + "\n");
        callback.onResult(result);
    }

    public void performFolderGesture(int sx, int sy, int tx, int ty, GestureController.Callback cb) { gestures.dragAfterLongPress(sx, sy, tx, ty, 850, cb); }

    public void createFolder(final HomeShortcut source, final HomeShortcut target, final FolderController.Callback cb) {
        if (source == null || target == null || source.hotseat || target.hotseat || source.pageIndex != target.pageIndex) {
            cb.onResult("FOLDER_CREATION_UNSUPPORTED", "Both shortcuts must be on the same normal scanned page"); return;
        }
        appendReport("FOLDER_PROBE_START source=" + source.label + " target=" + target.label + " page=" + source.pageIndex + "\n");
        navigateToPage(source.pageIndex, result -> {
            if (!"PAGE_READY".equals(result)) cb.onResult(result, "Could not reach shortcut page");
            else folderController.createFolder(source, target, cb);
        });
    }

    public void verifyFolderProbe(HomeShortcut source, HomeShortcut target, FolderController.Callback callback) {
        final int[] attempt = {0};
        verifyFolderProbeAttempt(source, target, callback, attempt);
    }

    private void verifyFolderProbeAttempt(HomeShortcut source, HomeShortcut target, FolderController.Callback callback, int[] attempt) {
        handler.postDelayed(() -> {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null || !String.valueOf(root.getPackageName()).equals(lastLauncherPackage)) {
                if (root != null) root.recycle(); callback.onResult("TEST_FAILED_VERIFICATION", "Launcher window not available after folder probe"); return;
            }
            String pkg = String.valueOf(root.getPackageName());
            List<HomeShortcut> after = LauncherAdapter.forPackage(pkg).findShortcuts(root, pkg, source.pageIndex);
            boolean sourceVisible = hasLabel(after, source.label);
            boolean targetVisible = hasLabel(after, target.label);
            String sourceNode = describeNearestNode(root, source);
            String targetNode = describeNearestNode(root, target);
            String tree = dumpRelevantTree(root, source, target);
            boolean explicitFolder = containsFolderNode(root);
            boolean pairDisappeared = !sourceVisible && !targetVisible;
            boolean folderLike = explicitFolder || pairDisappeared;
            String reason = explicitFolder ? "explicit folder-like node detected" : (pairDisappeared ? "both source and target disappeared from launcher shortcut scan" : "source/target remain visible and no folder marker was found");
            appendReport("FOLDER_VERIFY_ATTEMPT=" + attempt[0] + " folderLike=" + folderLike
                    + " explicitFolder=" + explicitFolder + " pairDisappeared=" + pairDisappeared
                    + " sourceVisible=" + sourceVisible + " targetVisible=" + targetVisible + "\n");
            appendReport("SOURCE_AFTER: " + sourceNode + "\nTARGET_AFTER: " + targetNode + "\n");
            appendReport("FOLDER_DETECTION reason=" + reason + "\n");
            appendReport("LAUNCHER3_TREE_AFTER_GESTURE\n" + tree);
            root.recycle();

            if (folderLike) {
                callback.onResult("FOLDER_CREATED", "Verified Launcher3 folder-like state: " + reason);
                return;
            }
            if (attempt[0] < 2) {
                attempt[0]++;
                verifyFolderProbeAttempt(source, target, callback, attempt);
                return;
            }
            callback.onResult("FOLDER_CREATION_UNSUPPORTED", "No reliable Launcher3 folder state after 3 verification reads");
        }, attempt[0] == 0 ? 1500 : 500);
    }

    private boolean containsFolderNode(AccessibilityNodeInfo n) {
        if (n == null) return false;
        String c = lower(n.getClassName());
        String d = lower(n.getContentDescription());
        String t = lower(n.getText());
        if (c.contains("folder") || d.contains("folder") || d.contains("foldericon") || t.contains("folder")) return true;
        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo child = n.getChild(i);
            boolean hit = containsFolderNode(child);
            if (child != null) child.recycle();
            if (hit) return true;
        }
        return false;
    }

    private String dumpRelevantTree(AccessibilityNodeInfo root, HomeShortcut source, HomeShortcut target) {
        StringBuilder out = new StringBuilder();
        dumpTree(root, source, target, out, 0, new int[]{0});
        return out.toString();
    }

    private void dumpTree(AccessibilityNodeInfo n, HomeShortcut source, HomeShortcut target, StringBuilder out, int depth, int[] count) {
        if (n == null || count[0] >= 220 || depth > 12) return;
        Rect r = new Rect(); n.getBoundsInScreen(r);
        boolean relevant = intersects(r, source) || intersects(r, target)
                || n.getChildCount() >= 2 || containsInterestingText(n);
        if (relevant || depth <= 2) {
            count[0]++;
            for (int i = 0; i < depth; i++) out.append("  ");
            out.append(nodeSummary(n, r)).append('\n');
        }
        for (int i = 0; i < n.getChildCount() && count[0] < 220; i++) {
            AccessibilityNodeInfo child = n.getChild(i);
            dumpTree(child, source, target, out, depth + 1, count);
            if (child != null) child.recycle();
        }
    }

    private boolean containsInterestingText(AccessibilityNodeInfo n) {
        String x = lower(n.getText()) + " " + lower(n.getContentDescription()) + " " + lower(n.getClassName());
        return x.contains("folder") || x.contains("facebook") || x.contains("instagram");
    }

    private String describeNearestNode(AccessibilityNodeInfo root, HomeShortcut wanted) {
        AccessibilityNodeInfo best = findNearestNode(root, wanted, null, new float[]{Float.MAX_VALUE});
        if (best == null) return "missing";
        Rect r = new Rect(); best.getBoundsInScreen(r);
        String s = nodeSummary(best, r);
        best.recycle();
        return s;
    }

    private AccessibilityNodeInfo findNearestNode(AccessibilityNodeInfo n, HomeShortcut wanted, AccessibilityNodeInfo best, float[] bestDist) {
        if (n == null) return best;
        Rect r = new Rect(); n.getBoundsInScreen(r);
        float dx = r.centerX() - wanted.centerX;
        float dy = r.centerY() - wanted.centerY;
        float dist = dx * dx + dy * dy;
        String text = lower(n.getText());
        String desc = lower(n.getContentDescription());
        if (text.equals(lower(wanted.label)) || desc.equals(lower(wanted.label))) {
            if (dist < bestDist[0]) {
                if (best != null) best.recycle();
                best = AccessibilityNodeInfo.obtain(n);
                bestDist[0] = dist;
            }
        }
        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo child = n.getChild(i);
            best = findNearestNode(child, wanted, best, bestDist);
            if (child != null) child.recycle();
        }
        return best;
    }

    private boolean intersects(Rect r, HomeShortcut s) {
        if (r == null || s == null) return false;
        return r.contains(s.centerX, s.centerY) || Math.abs(r.centerX() - s.centerX) < 100 && Math.abs(r.centerY() - s.centerY) < 100;
    }

    private String nodeSummary(AccessibilityNodeInfo n, Rect r) {
        return "class=" + safe(n.getClassName())
                + " text=" + safe(n.getText())
                + " contentDescription=" + safe(n.getContentDescription())
                + " viewId=" + safe(n.getViewIdResourceName())
                + " package=" + safe(n.getPackageName())
                + " bounds=" + r.flattenToString()
                + " clickable=" + n.isClickable()
                + " longClickable=" + n.isLongClickable()
                + " visibleToUser=" + n.isVisibleToUser()
                + " childCount=" + n.getChildCount();
    }

    private String safe(CharSequence x) { return x == null ? "" : x.toString().replace('\n', ' '); }
    private String lower(CharSequence x) { return safe(x).toLowerCase(java.util.Locale.ROOT); }
    private boolean hasLabel(List<HomeShortcut> list,String label){for(HomeShortcut s:list)if(s.label.equalsIgnoreCase(label))return true;return false;}
    public void appendDiagnostic(String s) { appendReport(s); }
    public String getSavedReport() { return getSharedPreferences("diagnostic", MODE_PRIVATE).getString("last_report", ""); }
    private void saveReport(String s) { getSharedPreferences("diagnostic", MODE_PRIVATE).edit().putString("last_report", s).apply(); }
    private void appendReport(String s) { saveReport(getSavedReport() + s); }
}
