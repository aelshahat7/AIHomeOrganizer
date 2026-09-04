package com.aelshahat.homeorganizer;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityEvent;

import java.util.ArrayList;
import java.util.List;

public class HomeAccessibilityService extends AccessibilityService {
    private static HomeAccessibilityService instance;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;

        AccessibilityServiceInfo info = getServiceInfo();
        if (info != null) {
            info.flags |= AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
            info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
            setServiceInfo(info);
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Diagnostic phase: observe only. No Home Screen changes are performed here.
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        if (instance == this) instance = null;
        super.onDestroy();
    }

    public static HomeAccessibilityService getInstance() {
        return instance;
    }

    public void scanHomeScreen() {
        performGlobalAction(GLOBAL_ACTION_HOME);
        handler.postDelayed(this::captureHomeScreen, 900);
    }

    private void captureHomeScreen() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            saveReport("SCAN FAILED\n\nNo active accessibility window was available.");
            return;
        }

        String packageName = String.valueOf(root.getPackageName());
        StringBuilder out = new StringBuilder();
        out.append("HOME SCREEN DIAGNOSTIC\n");
        out.append("=======================\n");
        out.append("Launcher package: ").append(packageName).append("\n");
        out.append("Android: ").append(android.os.Build.VERSION.RELEASE)
                .append(" (API ").append(android.os.Build.VERSION.SDK_INT).append(")\n\n");

        List<String> candidates = new ArrayList<>();
        collectCandidates(root, candidates);

        out.append("Potential interactive Home Screen nodes: ")
                .append(candidates.size()).append("\n\n");

        for (String item : candidates) {
            out.append(item).append("\n");
        }

        if (candidates.isEmpty()) {
            out.append("No candidate shortcut nodes detected.\n");
            out.append("This is useful diagnostic information: the Launcher may expose a different accessibility structure.\n");
        }

        root.recycle();
        saveReport(out.toString());
    }

    private void collectCandidates(AccessibilityNodeInfo node, List<String> out) {
        if (node == null) return;

        CharSequence text = node.getText();
        CharSequence desc = node.getContentDescription();
        CharSequence className = node.getClassName();

        boolean interesting = node.isClickable() || node.isLongClickable();
        boolean hasLabel = (text != null && text.length() > 0) || (desc != null && desc.length() > 0);

        if (interesting && hasLabel) {
            Rect bounds = new Rect();
            node.getBoundsInScreen(bounds);
            String label = text != null && text.length() > 0 ? text.toString() : desc.toString();
            String viewId = node.getViewIdResourceName();

            out.add("- label=\"" + clean(label) + "\""
                    + " | class=" + className
                    + " | clickable=" + node.isClickable()
                    + " | longClickable=" + node.isLongClickable()
                    + " | bounds=" + bounds.left + "," + bounds.top + ","
                    + bounds.right + "," + bounds.bottom
                    + (viewId == null ? "" : " | id=" + viewId));
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            collectCandidates(node.getChild(i), out);
        }
    }

    private String clean(String value) {
        return value.replace("\n", " ").replace("\r", " ").trim();
    }

    private void saveReport(String report) {
        getSharedPreferences("diagnostic", MODE_PRIVATE)
                .edit()
                .putString("last_report", report)
                .apply();
    }
}
