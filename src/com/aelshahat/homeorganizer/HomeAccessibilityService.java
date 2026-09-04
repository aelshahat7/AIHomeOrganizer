package com.aelshahat.homeorganizer;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class HomeAccessibilityService extends AccessibilityService {
    private static final String TAG = "AIHomeOrganizer";
    private static HomeAccessibilityService instance;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final GestureController gestures = new GestureController(this);
    private List<HomeShortcut> lastShortcuts = new ArrayList<>();
    private String lastLauncherPackage;
    private String lastAdapterName;

    public interface TestCallback { void onResult(String result); }

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        AccessibilityServiceInfo info = getServiceInfo();
        if (info != null) {
            info.flags |= AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
            info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
            setServiceInfo(info);
        }
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }
    @Override public void onInterrupt() { gestures.cancel(); }
    @Override public void onDestroy() {
        gestures.cancel();
        if (instance == this) instance = null;
        super.onDestroy();
    }

    public static HomeAccessibilityService getInstance() { return instance; }
    public boolean canPerformGestureNow() { return gestures.canPerformGesture(); }
    public List<HomeShortcut> getShortcuts() { return Collections.unmodifiableList(new ArrayList<>(lastShortcuts)); }
    public HomeShortcut getReliableShortcut() { return lastShortcuts.isEmpty() ? null : lastShortcuts.get(0); }
    public String getLastLauncherPackage() { return lastLauncherPackage; }

    public void scanHomeScreen() {
        if (gestures.isRunning()) return;
        boolean movedHome = performGlobalAction(GLOBAL_ACTION_HOME);
        if (!movedHome) {
            saveReport("SCAN FAILED\n\nCould not request the Home Screen from AccessibilityService.");
            return;
        }
        handler.postDelayed(this::captureHomeScreen, 900);
    }

    private void captureHomeScreen() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            saveReport("SCAN FAILED\n\nNo active accessibility window was available.");
            return;
        }

        String pkg = String.valueOf(root.getPackageName());
        LauncherAdapter adapter = LauncherAdapter.forPackage(pkg);
        lastLauncherPackage = pkg;
        lastAdapterName = adapter.name();
        lastShortcuts = adapter.findShortcuts(root, pkg);

        StringBuilder out = new StringBuilder("HOME SCREEN DIAGNOSTIC\n=======================\n");
        out.append("Launcher package: ").append(pkg).append("\n");
        out.append("Adapter: ").append(lastAdapterName).append("\n");
        out.append("Android: ").append(android.os.Build.VERSION.RELEASE)
                .append(" (API ").append(android.os.Build.VERSION.SDK_INT).append(")\n\n");
        out.append("Detected Home Screen shortcuts: ").append(lastShortcuts.size()).append("\n\n");
        for (int i = 0; i < lastShortcuts.size(); i++) {
            out.append("[").append(i + 1).append("] ").append(lastShortcuts.get(i).describe()).append("\n");
        }
        out.append("\nDetection notes:\n");
        out.append("- Accessibility tree is primary; no hard-coded grid coordinates.\n");
        out.append("- Launcher app icons require a visible labelled TextView with valid bounds and launcher-appropriate click/long-click behavior.\n");
        out.append("- Search, clock/date/weather, navigation and launcher utility UI are excluded.\n");
        out.append("- Duplicate identities are collapsed; componentName remains null when the launcher does not expose it through AccessibilityNodeInfo.\n");
        if (lastShortcuts.isEmpty()) out.append("- No reliable shortcut found; Safe Drag Test remains disabled.\n");

        saveReport(out.toString());
        root.recycle();
        Log.i(TAG, "SCAN launcher=" + pkg + " adapter=" + lastAdapterName + " shortcuts=" + lastShortcuts.size());
    }

    public void runSafeDragTest(final HomeShortcut selected, final TestCallback callback) {
        if (selected == null) { callback.onResult("TEST_FAILED_SHORTCUT_NOT_FOUND"); return; }
        if (!canPerformGestureNow()) { callback.onResult("TEST_FAILED_GESTURE_UNAVAILABLE"); return; }

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) { callback.onResult("TEST_FAILED_LAUNCHER_CHANGED"); return; }
        String currentPackage = String.valueOf(root.getPackageName());
        LauncherAdapter adapter = LauncherAdapter.forPackage(currentPackage);
        List<HomeShortcut> current = adapter.findShortcuts(root, currentPackage);
        root.recycle();

        if (lastLauncherPackage == null || !lastLauncherPackage.equals(currentPackage)) {
            callback.onResult("TEST_FAILED_LAUNCHER_CHANGED");
            return;
        }

        HomeShortcut live = findSameShortcut(selected, current);
        if (live == null) {
            callback.onResult("TEST_FAILED_SHORTCUT_NOT_FOUND");
            return;
        }

        Log.i(TAG, "TEST selected=" + live.describe());
        final int sx = live.centerX;
        final int sy = live.centerY;
        gestures.safeDrag(sx, sy, 8, 0, new GestureController.Callback() {
            @Override public void onSuccess() {
                Log.i(TAG, "GESTURE callback=completed start=" + sx + "," + sy + " end=" + (sx + 8) + "," + sy + " return=" + sx + "," + sy);
                handler.postDelayed(() -> verifyAfterGesture(live, callback), 700);
            }
            @Override public void onFailure(String reason) {
                Log.w(TAG, "GESTURE callback=failure reason=" + reason);
                saveReport(getSavedReport() + "\n\nGESTURE FAILURE: " + reason);
                callback.onResult(reason.contains("capability") ? "TEST_FAILED_GESTURE_UNAVAILABLE" : "TEST_FAILED_VERIFICATION");
            }
        });
    }

    private void verifyAfterGesture(HomeShortcut before, TestCallback callback) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) { callback.onResult("TEST_FAILED_VERIFICATION"); return; }
        String pkg = String.valueOf(root.getPackageName());
        if (lastLauncherPackage == null || !lastLauncherPackage.equals(pkg)) {
            root.recycle();
            callback.onResult("TEST_FAILED_LAUNCHER_CHANGED");
            return;
        }

        LauncherAdapter adapter = LauncherAdapter.forPackage(pkg);
        List<HomeShortcut> after = adapter.findShortcuts(root, pkg);
        root.recycle();
        HomeShortcut match = findMatchForVerification(before, after);
        String result;
        if (match == null) result = "TEST_FAILED_VERIFICATION";
        else if (match.centerX != before.centerX || match.centerY != before.centerY) result = "TEST_SUCCESS_DRAG_DETECTED";
        else result = "TEST_SUCCESS_NO_MOVE";

        Log.i(TAG, "VERIFY result=" + result + " before=" + before.centerX + "," + before.centerY
                + " after=" + (match == null ? "missing" : match.centerX + "," + match.centerY));
        String afterText = match == null ? "shortcut not found" : match.describe();
        saveReport(getSavedReport() + "\n\nVERIFICATION RESULT: " + result
                + "\nGesture start: " + before.centerX + "," + before.centerY
                + "\nGesture probe end: " + (before.centerX + 8) + "," + before.centerY
                + "\nGesture returned toward origin before release."
                + "\nBefore: " + before.describe() + "\nAfter: " + afterText);
        lastShortcuts = after;
        callback.onResult(result);
    }

    private HomeShortcut findSameShortcut(HomeShortcut wanted, List<HomeShortcut> candidates) {
        if (wanted.viewId != null && !wanted.viewId.isEmpty()) {
            for (HomeShortcut s : candidates) {
                if (wanted.viewId.equals(s.viewId) && wanted.label.equalsIgnoreCase(s.label)) return s;
            }
        }
        for (HomeShortcut s : candidates) {
            if (wanted.label.equalsIgnoreCase(s.label)
                    && Math.abs(s.centerX - wanted.centerX) < 80
                    && Math.abs(s.centerY - wanted.centerY) < 80) return s;
        }
        return null;
    }

    private HomeShortcut findMatchForVerification(HomeShortcut before, List<HomeShortcut> after) {
        for (HomeShortcut s : after) {
            if (before.viewId != null && !before.viewId.isEmpty()
                    && before.viewId.equals(s.viewId)
                    && before.label.equalsIgnoreCase(s.label)) return s;
        }
        for (HomeShortcut s : after) {
            if (before.label.equalsIgnoreCase(s.label)) return s;
        }
        return null;
    }

    private String getSavedReport() { return getSharedPreferences("diagnostic", MODE_PRIVATE).getString("last_report", ""); }
    private void saveReport(String s) { getSharedPreferences("diagnostic", MODE_PRIVATE).edit().putString("last_report", s).apply(); }
}
