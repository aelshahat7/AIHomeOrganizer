package com.aelshahat.homeorganizer;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.List;

public class HomeAccessibilityService extends AccessibilityService {
    private static final String TAG = "AIHomeOrganizer";
    private static final String LAUNCHER_PACKAGE = "com.android.launcher3";
    private static HomeAccessibilityService instance;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final GestureController gestures = new GestureController(this);
    private List<HomeShortcut> lastShortcuts;
    private String lastLauncherPackage;
    public interface TestCallback { void onResult(String result); }

    @Override protected void onServiceConnected() {
        super.onServiceConnected(); instance = this;
        AccessibilityServiceInfo info = getServiceInfo();
        if (info != null) { info.flags |= AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS | AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS; setServiceInfo(info); }
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }
    @Override public void onInterrupt() { gestures.cancel(); }
    @Override public void onDestroy() { gestures.cancel(); if (instance == this) instance = null; super.onDestroy(); }
    public static HomeAccessibilityService getInstance() { return instance; }
    public boolean canPerformGestureNow() { AccessibilityServiceInfo i = getServiceInfo(); return i != null && i.capabilitiesHasGesture(); }
    public HomeShortcut getReliableShortcut() { return lastShortcuts == null || lastShortcuts.isEmpty() ? null : lastShortcuts.get(0); }

    public void scanHomeScreen() { if (gestures.isRunning()) return; performGlobalAction(GLOBAL_ACTION_HOME); handler.postDelayed(this::captureHomeScreen, 900); }

    private void captureHomeScreen() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) { saveReport("SCAN FAILED\n\nNo active accessibility window was available."); return; }
        String pkg = String.valueOf(root.getPackageName()); lastLauncherPackage = pkg;
        LauncherAdapter adapter = LauncherAdapter.forPackage(pkg); lastShortcuts = adapter.findShortcuts(root, pkg);
        StringBuilder out = new StringBuilder("HOME SCREEN DIAGNOSTIC\n=======================\n");
        out.append("Launcher package: ").append(pkg).append("\nAdapter: ").append(adapter.name()).append("\nAndroid: ").append(android.os.Build.VERSION.RELEASE).append(" (API ").append(android.os.Build.VERSION.SDK_INT).append(")\n\n");
        out.append("Detected Home Screen shortcuts: ").append(lastShortcuts.size()).append("\n\n");
        for (int i=0;i<lastShortcuts.size();i++) out.append("[").append(i+1).append("] ").append(lastShortcuts.get(i).describe()).append("\n");
        out.append("\nDetection notes: label + visibility + valid bounds + launcher-appropriate click/long-click behavior; search/clock/date/weather/navigation UI excluded; duplicates removed.\n");
        root.recycle(); saveReport(out.toString());
        Log.i(TAG, "SCAN launcher="+pkg+" adapter="+adapter.name()+" shortcuts="+lastShortcuts.size());
    }

    public void runSafeDragTest(final HomeShortcut selected, final TestCallback callback) {
        if (selected == null) { callback.onResult("TEST_FAILED_SHORTCUT_NOT_FOUND"); return; }
        if (!canPerformGestureNow()) { callback.onResult("TEST_FAILED_GESTURE_UNAVAILABLE"); return; }
        AccessibilityNodeInfo root = getRootInActiveWindow();
        String pkg = root == null ? "" : String.valueOf(root.getPackageName());
        if (root != null) root.recycle();
        if (!LAUNCHER_PACKAGE.equals(pkg) || !pkg.equals(lastLauncherPackage)) { callback.onResult("TEST_FAILED_LAUNCHER_CHANGED"); return; }
        Log.i(TAG,"TEST selected="+selected.describe());
        final int sx=selected.centerX, sy=selected.centerY;
        gestures.safeDrag(sx,sy,8,0,new GestureController.Callback() {
            @Override public void onSuccess() { Log.i(TAG,"GESTURE callback=completed start="+sx+","+sy+" delta=8,0"); handler.postDelayed(()->verifyAfterGesture(selected,callback),700); }
            @Override public void onFailure(String reason) { Log.w(TAG,"GESTURE callback=failure reason="+reason); saveReport(getSavedReport()+"\n\nTEST_FAILED: "+reason); callback.onResult(reason.contains("capability")?"TEST_FAILED_GESTURE_UNAVAILABLE":"TEST_FAILED_VERIFICATION"); }
        });
    }

    private void verifyAfterGesture(HomeShortcut before, TestCallback callback) {
        AccessibilityNodeInfo root=getRootInActiveWindow();
        if(root==null || !LAUNCHER_PACKAGE.equals(String.valueOf(root.getPackageName()))) { if(root!=null)root.recycle(); callback.onResult("TEST_FAILED_LAUNCHER_CHANGED"); return; }
        List<HomeShortcut> after=LauncherAdapter.forPackage(LAUNCHER_PACKAGE).findShortcuts(root,LAUNCHER_PACKAGE); root.recycle();
        HomeShortcut match=findMatch(before,after); String result;
        if(match==null) result="TEST_FAILED_SHORTCUT_NOT_FOUND"; else if(match.centerX!=before.centerX||match.centerY!=before.centerY) result="TEST_SUCCESS_DRAG_DETECTED"; else result="TEST_SUCCESS_NO_MOVE";
        Log.i(TAG,"VERIFY result="+result+" before="+before.centerX+","+before.centerY+" after="+(match==null?"missing":match.centerX+","+match.centerY));
        saveReport(getSavedReport()+"\n\nVERIFICATION RESULT: "+result+"\nBefore: "+before.describe()+"\nAfter: "+(match==null?"shortcut not found":match.describe()));
        lastShortcuts=after; callback.onResult(result);
    }
    private HomeShortcut findMatch(HomeShortcut b,List<HomeShortcut>a){ for(HomeShortcut s:a)if(s.label.equalsIgnoreCase(b.label)&&Math.abs(s.centerX-b.centerX)<120&&Math.abs(s.centerY-b.centerY)<120)return s; for(HomeShortcut s:a)if(s.label.equalsIgnoreCase(b.label))return s; return null; }
    private String getSavedReport(){return getSharedPreferences("diagnostic",MODE_PRIVATE).getString("last_report","");}
    private void saveReport(String s){getSharedPreferences("diagnostic",MODE_PRIVATE).edit().putString("last_report",s).apply();}
}
