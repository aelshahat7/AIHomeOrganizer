package com.aelshahat.homeorganizer;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

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

        List<String> shortcuts = new ArrayList<>();
        List<String> candidates = new ArrayList<>();
        collectNodes(root, candidates, shortcuts);

        out.append("Detected Home Screen shortcuts: ")
                .append(shortcuts.size()).append("\n\n");
        for (String item : shortcuts) {
            out.append(item).append("\n");
        }

        out.append("\nOther interactive nodes: ")
                .append(candidates.size()).append("\n\n");
        for (String item : candidates) {
            out.append(item).append("\n");
        }

        if (shortcuts.isEmpty()) {
            out.append("\nNo reliable shortcut nodes detected yet.\n");
            out.append("The next step will use the Launcher accessibility tree to adapt to its actual structure.\n");
        }

        root.recycle();
        saveReport(out.toString());
    }

    private void collectNodes(AccessibilityNodeInfo node,
                              List<String> candidates,
                              List<String> shortcuts) {
        if (node == null) return;

        CharSequence text = node.getText();
        CharSequence desc = node.getContentDescription();
        CharSequence className = node.getClassName();

        boolean interesting = node.isClickable() || node.isLongClickable();
        boolean hasLabel = (text != null && text.length() > 0)
                || (desc != null && desc.length() > 0);

        if (interesting && hasLabel) {
            Rect bounds = new Rect();
            node.getBoundsInScreen(bounds);
            String label = text != null && text.length() > 0
                    ? text.toString()
                    : desc.toString();
            String item = formatNode(node, label, bounds);
            candidates.add(item);

            // Launcher3 app shortcuts are exposed as long-clickable TextViews.
            // The quick-event/search widgets seen on this device are not long-clickable,
            // so this is a useful first discriminator without relying on screen coordinates.
            boolean looksLikeShortcut = node.isLongClickable()
                    && node.isVisibleToUser()
                    && className != null
                    && className.toString().contains("TextView")
                    && bounds.width() > 0
                    && bounds.height() > 0
                    && bounds.width() < 500
                    && bounds.height() < 500;

            if (looksLikeShortcut) {
                shortcuts.add(formatNode(node, label, bounds));
            }
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            collectNodes(node.getChild(i), candidates, shortcuts);
        }
    }

    private String formatNode(AccessibilityNodeInfo node, String label, Rect bounds) {
        String viewId = node.getViewIdResourceName();
        return "- label=\"" + clean(label) + "\""
                + " | class=" + node.getClassName()
                + " | clickable=" + node.isClickable()
                + " | longClickable=" + node.isLongClickable()
                + " | bounds=" + bounds.left + "," + bounds.top + ","
                + bounds.right + "," + bounds.bottom
                + " | center=" + bounds.centerX() + "," + bounds.centerY()
                + (viewId == null ? "" : " | id=" + viewId);
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
