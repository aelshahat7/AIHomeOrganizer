package com.aelshahat.homeorganizer;

import android.app.Activity;
import android.app.AlertDialog;
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

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private TextView status;
    private TextView launcher;
    private TextView report;
    private Spinner shortcutPicker;
    private Button safeDrag;
    private List<HomeShortcut> shortcuts = new ArrayList<>();
    private HomeShortcut selectedShortcut;
    private boolean updatingPicker;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 24, 32, 24);

        TextView title = new TextView(this);
        title.setText("AI Home Organizer\nSafe Home Screen MVP");
        title.setTextSize(24);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        status = new TextView(this);
        status.setTextSize(16);
        status.setPadding(0, 18, 0, 6);
        root.addView(status, new LinearLayout.LayoutParams(-1, -2));

        launcher = new TextView(this);
        launcher.setTextSize(14);
        launcher.setPadding(0, 0, 0, 12);
        root.addView(launcher, new LinearLayout.LayoutParams(-1, -2));

        Button settings = new Button(this);
        settings.setText("Enable Accessibility Service");
        settings.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        root.addView(settings, new LinearLayout.LayoutParams(-1, -2));

        Button scan = new Button(this);
        scan.setText("Scan Home Screen");
        scan.setOnClickListener(v -> startScan());
        root.addView(scan, new LinearLayout.LayoutParams(-1, -2));

        TextView selectTitle = new TextView(this);
        selectTitle.setText("Select ONE reliable shortcut for the safe test:");
        selectTitle.setPadding(0, 12, 0, 4);
        root.addView(selectTitle, new LinearLayout.LayoutParams(-1, -2));

        shortcutPicker = new Spinner(this);
        root.addView(shortcutPicker, new LinearLayout.LayoutParams(-1, -2));
        shortcutPicker.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) {
                if (!updatingPicker && position >= 0 && position < shortcuts.size()) {
                    selectedShortcut = shortcuts.get(position);
                    updateSafeDragEnabled();
                }
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {
                if (!updatingPicker) { selectedShortcut = null; updateSafeDragEnabled(); }
            }
        });

        safeDrag = new Button(this);
        safeDrag.setText("Safe Drag Test");
        safeDrag.setEnabled(false);
        safeDrag.setOnClickListener(v -> confirmSafeDrag());
        root.addView(safeDrag, new LinearLayout.LayoutParams(-1, -2));

        report = new TextView(this);
        report.setTextSize(13);
        report.setPadding(0, 18, 0, 24);
        report.setTextIsSelectable(true);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(report, new ViewGroup.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        refreshReport();
        refreshStatus();
    }

    @Override protected void onResume() {
        super.onResume();
        refreshReport();
        refreshStatus();
    }

    private void startScan() {
        HomeAccessibilityService service = HomeAccessibilityService.getInstance();
        if (service == null) {
            status.setText("Service: OFF — enable Accessibility first.");
            return;
        }
        status.setText("Scanning Home Screen…");
        service.scanHomeScreen();
        report.postDelayed(() -> {
            refreshReport();
            refreshStatus();
        }, 1200);
    }

    private void confirmSafeDrag() {
        if (selectedShortcut == null) {
            status.setText("No reliable shortcut is selected.");
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Confirm Safe Drag Test")
                .setMessage("The app will attempt a very small reversible drag on:\n\n"
                        + selectedShortcut.label
                        + "\n\nNo folder will be created and no other shortcut will be touched."
                        + " The pointer moves at most a few pixels, then returns toward the original position before release. Continue?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Run Test", (d, w) -> runSafeDrag())
                .show();
    }

    private void runSafeDrag() {
        final HomeAccessibilityService service = HomeAccessibilityService.getInstance();
        if (service == null) {
            status.setText("TEST_FAILED_LAUNCHER_CHANGED");
            return;
        }
        status.setText("Running Safe Drag Test…");
        safeDrag.setEnabled(false);
        service.runSafeDragTest(selectedShortcut, result -> {
            status.setText(result);
            refreshReport();
            updateSafeDragEnabled();
        });
    }

    private void refreshReport() {
        HomeAccessibilityService service = HomeAccessibilityService.getInstance();
        String saved = getSharedPreferences("diagnostic", MODE_PRIVATE)
                .getString("last_report", "No scan yet. Enable the service, then scan the Home Screen.");
        report.setText(saved);
        shortcuts = service == null ? new ArrayList<>() : new ArrayList<>(service.getShortcuts());
        populatePicker();
        updateSafeDragEnabled();
        String pkg = service == null ? null : service.getLastLauncherPackage();
        launcher.setText("Launcher package: " + (pkg == null ? "not scanned" : pkg));
    }

    private void populatePicker() {
        updatingPicker = true;
        ArrayList<String> labels = new ArrayList<>();
        for (int i = 0; i < shortcuts.size(); i++) {
            HomeShortcut s = shortcuts.get(i);
            labels.add((i + 1) + ". " + s.label + "  (confidence "
                    + String.format(java.util.Locale.US, "%.2f", s.confidence) + ")");
        }
        if (labels.isEmpty()) labels.add("No reliable shortcuts detected");
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        shortcutPicker.setAdapter(adapter);
        if (!shortcuts.isEmpty()) {
            selectedShortcut = shortcuts.get(0);
            shortcutPicker.setSelection(0);
        } else {
            selectedShortcut = null;
        }
        updatingPicker = false;
    }

    private void updateSafeDragEnabled() {
        HomeAccessibilityService service = HomeAccessibilityService.getInstance();
        safeDrag.setEnabled(selectedShortcut != null && service != null && service.canPerformGestureNow());
    }

    private void refreshStatus() {
        HomeAccessibilityService service = HomeAccessibilityService.getInstance();
        if (service == null) status.setText("Service: OFF");
        else status.setText("Service: ON — ready for SCAN → SELECT ONE → SAFE DRAG PROBE → VERIFY");
    }
}
