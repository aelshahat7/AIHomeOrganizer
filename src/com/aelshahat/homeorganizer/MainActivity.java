package com.aelshahat.homeorganizer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends Activity {
    private TextView status, launcher, summary, report, planView;
    private Spinner shortcutPicker;
    private Button safeDrag, classify, approve, execute, copyReport;
    private List<HomeShortcut> shortcuts = new ArrayList<>();
    private List<ClassificationResult> classifications = new ArrayList<>();
    private OrganizationPlan plan;
    private HomeShortcut selectedShortcut;
    private boolean approved;
    private boolean updating;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(28, 20, 28, 20);
        TextView title = new TextView(this); title.setText("AI Home Organizer\nFull MVP"); title.setTextSize(24); title.setGravity(Gravity.CENTER); root.addView(title, lp());
        status = new TextView(this); status.setTextSize(16); status.setPadding(0, 14, 0, 6); root.addView(status, lp());
        launcher = new TextView(this); root.addView(launcher, lp());
        Button settings = new Button(this); settings.setText("Enable Accessibility Service"); settings.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))); root.addView(settings, lp());
        Button scan = new Button(this); scan.setText("SCAN ALL HOME PAGES"); scan.setOnClickListener(v -> startScan()); root.addView(scan, lp());

        summary = new TextView(this); summary.setPadding(0, 10, 0, 6); root.addView(summary, lp());
        TextView pickTitle = new TextView(this); pickTitle.setText("Safe Drag Test: select one regular shortcut"); root.addView(pickTitle, lp());
        shortcutPicker = new Spinner(this); root.addView(shortcutPicker, lp());
        shortcutPicker.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> p, android.view.View v, int pos, long id) { if (!updating && pos < shortcuts.size()) { selectedShortcut = shortcuts.get(pos); updateButtons(); } }
            @Override public void onNothingSelected(android.widget.AdapterView<?> p) { selectedShortcut = null; updateButtons(); }
        });
        safeDrag = new Button(this); safeDrag.setText("SAFE DRAG PROBE"); safeDrag.setEnabled(false); safeDrag.setOnClickListener(v -> confirmSafeDrag()); root.addView(safeDrag, lp());
        classify = new Button(this); classify.setText("CLASSIFY LOCALLY"); classify.setEnabled(false); classify.setOnClickListener(v -> buildPlan()); root.addView(classify, lp());

        TextView planTitle = new TextView(this); planTitle.setText("Proposed Plan"); planTitle.setTextSize(16); root.addView(planTitle, lp());
        ScrollView planScroll = new ScrollView(this); planView = new TextView(this); planView.setTextSize(14); planView.setPadding(0, 8, 0, 8); planScroll.addView(planView, new ViewGroup.LayoutParams(-1, -2)); root.addView(planScroll, new LinearLayout.LayoutParams(-1, 0, 0.9f));
        approve = new Button(this); approve.setText("APPROVE PLAN"); approve.setEnabled(false); approve.setOnClickListener(v -> { approved = true; if (plan != null) plan.setApproved(true); append("USER_APPROVED\n"); status.setText("Plan approved. Execute is now enabled."); updateButtons(); }); root.addView(approve, lp());
        execute = new Button(this); execute.setText("EXECUTE APPROVED PLAN"); execute.setEnabled(false); execute.setOnClickListener(v -> confirmExecute()); root.addView(execute, lp());

        LinearLayout reportBar = new LinearLayout(this); reportBar.setOrientation(LinearLayout.HORIZONTAL);
        TextView reportTitle = new TextView(this); reportTitle.setText("Diagnostic Report"); reportTitle.setTextSize(16); reportBar.addView(reportTitle, new LinearLayout.LayoutParams(0, -2, 1));
        copyReport = new Button(this); copyReport.setText("COPY"); copyReport.setOnClickListener(v -> copyReport()); reportBar.addView(copyReport, new LinearLayout.LayoutParams(-2, -2)); root.addView(reportBar, lp());
        ScrollView scroll = new ScrollView(this); report = new TextView(this); report.setTextSize(12); report.setTextIsSelectable(true); report.setPadding(0, 8, 0, 24); scroll.addView(report, new ViewGroup.LayoutParams(-1, -2)); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1.5f));
        setContentView(root); refresh();
    }

    private LinearLayout.LayoutParams lp() { return new LinearLayout.LayoutParams(-1, -2); }
    @Override protected void onResume() { super.onResume(); refresh(); }

    private void startScan() {
        HomeAccessibilityService s = HomeAccessibilityService.getInstance();
        if (s == null) { status.setText("Service OFF. Enable Accessibility first."); return; }
        approved = false; plan = null; classifications.clear(); classify.setEnabled(false); approve.setEnabled(false); execute.setEnabled(false);
        status.setText("Scanning every Home Screen page...");
        s.scanHomeScreen(code -> runOnUiThread(() -> { refresh(); status.setText(code + " | review pages and shortcuts"); classify.setEnabled(code.equals("MULTI_PAGE_SCAN_COMPLETE") || code.equals("PAGE_LIMIT_REACHED")); }));
    }

    private void confirmSafeDrag() {
        if (selectedShortcut == null) return;
        new AlertDialog.Builder(this).setTitle("Safe Drag Probe")
                .setMessage("A controlled long-press/drag probe will touch only: " + selectedShortcut.label + "\nIt will not intentionally move the shortcut to another grid cell. Continue?")
                .setNegativeButton("Cancel", null).setPositiveButton("Run", (d,w) -> runSafeDrag()).show();
    }
    private void runSafeDrag() {
        HomeAccessibilityService s = HomeAccessibilityService.getInstance(); if (s == null) return;
        safeDrag.setEnabled(false); status.setText("Running gesture probe...");
        s.runSafeDragTest(selectedShortcut, result -> runOnUiThread(() -> { status.setText(result); refresh(); updateButtons(); }));
    }

    private void buildPlan() {
        HomeAccessibilityService s = HomeAccessibilityService.getInstance(); if (s == null || shortcuts.isEmpty()) return;
        classifications = new ClassificationEngine().classify(shortcuts); plan = new OrganizationPlan();
        for (ClassificationResult r : classifications) plan.add(new OrganizationPlan.Item(r.shortcut, r.category, r.confidence, r.reason));
        approved = false; renderPlan(); approve.setEnabled(true); execute.setEnabled(false); append("CLASSIFICATION_COMPLETE\nPLAN_READY\n"); status.setText("Plan ready. Nothing has been changed on Home Screen.");
    }

    private void renderPlan() {
        if (plan == null) { planView.setText("No plan yet."); return; }
        Map<String,List<OrganizationPlan.Item>> groups = new LinkedHashMap<>();
        for (OrganizationPlan.Item i : plan.getItems()) groups.computeIfAbsent(i.category, k -> new ArrayList<>()).add(i);
        StringBuilder b = new StringBuilder("PROPOSED ORGANIZATION PLAN\n");
        for (Map.Entry<String,List<OrganizationPlan.Item>> e : groups.entrySet()) {
            b.append("\n").append(e.getKey()).append(" (" ).append(e.getValue().size()).append(")\n");
            for (OrganizationPlan.Item i : e.getValue()) b.append("  - ").append(i.shortcut.label).append(" | page=").append(i.shortcut.pageIndex).append(" | confidence ").append(String.format(java.util.Locale.US,"%.2f",i.confidence)).append("\n");
        }
        planView.setText(b.toString());
    }

    private void confirmExecute() {
        if (!approved || plan == null) return;
        new AlertDialog.Builder(this).setTitle("Execute Home Screen changes?")
                .setMessage("The app will execute approved folder operations one at a time and STOP on the first verification failure. This may modify your Home Screen. Continue?")
                .setNegativeButton("Cancel", null).setPositiveButton("Execute", (d,w) -> executePlan()).show();
    }

    private void executePlan() {
        HomeAccessibilityService s = HomeAccessibilityService.getInstance(); if (s == null) return;
        execute.setEnabled(false); append("EXECUTION_STARTED\n"); status.setText("Executing one verified operation at a time...");
        executeNextCategory(s, new ArrayList<>(groupCategories()), 0);
    }

    private List<List<OrganizationPlan.Item>> groupCategories() {
        Map<String,List<OrganizationPlan.Item>> groups = new LinkedHashMap<>();
        if (plan != null) for (OrganizationPlan.Item i : plan.getItems()) if (!i.shortcut.hotseat && !"Needs Review".equals(i.category)) groups.computeIfAbsent(i.category,k -> new ArrayList<>()).add(i);
        return new ArrayList<>(groups.values());
    }

    private void executeNextCategory(HomeAccessibilityService s, List<List<OrganizationPlan.Item>> groups, int index) {
        if (index >= groups.size()) { append("ORGANIZATION_COMPLETE\n"); status.setText("ORGANIZATION_COMPLETE"); return; }
        List<OrganizationPlan.Item> items = groups.get(index);
        if (items.size() < 2) { executeNextCategory(s, groups, index + 1); return; }
        OrganizationPlan.Item a = items.get(0), b = items.get(1);
        append("FOLDER_OPERATION category=" + a.category + " source=" + a.shortcut.label + " target=" + b.shortcut.label + "\n");
        s.createFolder(a.shortcut, b.shortcut, (code, detail) -> runOnUiThread(() -> {
            append(code + " detail=" + detail + "\n");
            if (!"FOLDER_CREATED".equals(code)) { append("ORGANIZATION_STOPPED_AFTER_FAILURE\n"); status.setText("ORGANIZATION_STOPPED_AFTER_FAILURE: " + code); return; }
            status.setText("Verified folder operation. Continuing..."); executeNextCategory(s, groups, index + 1);
        }));
    }

    private void copyReport() {
        String text = report == null ? "" : report.getText().toString();
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) { cm.setPrimaryClip(ClipData.newPlainText("AI Home Organizer Diagnostic Report", text)); Toast.makeText(this, "Report copied", Toast.LENGTH_SHORT).show(); }
    }

    private void refresh() {
        HomeAccessibilityService s = HomeAccessibilityService.getInstance();
        launcher.setText("Launcher: " + (s == null || s.getLastLauncherPackage() == null ? "not scanned" : s.getLastLauncherPackage()) + " | Adapter: " + (s == null || s.getLastAdapterName() == null ? "-" : s.getLastAdapterName()));
        List<HomeShortcut> all = s == null ? new ArrayList<>() : new ArrayList<>(s.getShortcuts());
        shortcuts = new ArrayList<>(); int hotseatCount = 0;
        for (HomeShortcut h : all) { if (h.hotseat) hotseatCount++; else shortcuts.add(h); }
        updating = true; ArrayList<String> labels = new ArrayList<>(); for (HomeShortcut h : shortcuts) labels.add(h.label + " | page=" + h.pageIndex);
        if (labels.isEmpty()) labels.add("No regular shortcuts detected yet"); shortcutPicker.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labels));
        if (!shortcuts.isEmpty()) { selectedShortcut = shortcuts.get(0); shortcutPicker.setSelection(0); } else selectedShortcut = null; updating = false;
        int pages = s == null ? 0 : s.getPages().size(); summary.setText("Pages detected: " + pages + "\nRegular shortcuts: " + shortcuts.size() + "\nHotseat instances: " + hotseatCount);
        report.setText(s == null ? "Service OFF" : s.getSavedReport()); if (plan != null) renderPlan(); updateButtons();
        if (copyReport != null) copyReport.setEnabled(report.length() > 0);
        status.setText(s == null ? "Service OFF" : "Service ON");
    }

    private void updateButtons() {
        HomeAccessibilityService s = HomeAccessibilityService.getInstance();
        safeDrag.setEnabled(selectedShortcut != null && s != null && s.canPerformGestureNow() && !s.isScanning());
        classify.setEnabled(!shortcuts.isEmpty() && s != null && !s.isScanning());
        approve.setEnabled(plan != null && !approved);
        execute.setEnabled(plan != null && approved);
    }

    private void append(String text) { HomeAccessibilityService s = HomeAccessibilityService.getInstance(); if (s != null) s.getSharedPreferences("diagnostic", MODE_PRIVATE).edit().putString("last_report", s.getSavedReport() + text).apply(); refresh(); }
}
