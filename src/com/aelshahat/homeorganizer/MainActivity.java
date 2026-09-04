package com.aelshahat.homeorganizer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class MainActivity extends Activity {
    private TextView status;
    private TextView report;
    private Button safeDrag;
    private HomeShortcut selectedShortcut;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 24, 32, 24);

        TextView title = new TextView(this);
        title.setText("AI Home Organizer\nSafe Home Screen MVP");
        title.setTextSize(24); title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        status = new TextView(this); status.setTextSize(16);
        status.setPadding(0, 18, 0, 18);
        root.addView(status, new LinearLayout.LayoutParams(-1, -2));

        Button settings = new Button(this); settings.setText("Enable Accessibility Service");
        settings.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        root.addView(settings, new LinearLayout.LayoutParams(-1, -2));

        Button scan = new Button(this); scan.setText("Scan Home Screen");
        scan.setOnClickListener(v -> startScan());
        root.addView(scan, new LinearLayout.LayoutParams(-1, -2));

        safeDrag = new Button(this); safeDrag.setText("Safe Drag Test"); safeDrag.setEnabled(false);
        safeDrag.setOnClickListener(v -> confirmSafeDrag());
        root.addView(safeDrag, new LinearLayout.LayoutParams(-1, -2));

        report = new TextView(this); report.setTextSize(13); report.setPadding(0, 18, 0, 24); report.setTextIsSelectable(true);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        scroll.addView(report, new ViewGroup.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root); refreshReport();
    }

    @Override protected void onResume() { super.onResume(); refreshReport(); }

    private void startScan() {
        HomeAccessibilityService service = HomeAccessibilityService.getInstance();
        if (service == null) { status.setText("Service: OFF — enable Accessibility first."); return; }
        status.setText("Scanning Home Screen…");
        service.scanHomeScreen();
        report.postDelayed(this::refreshReport, 1200);
    }

    private void confirmSafeDrag() {
        if (selectedShortcut == null) { status.setText("No reliable shortcut is selected."); return; }
        new AlertDialog.Builder(this)
                .setTitle("Confirm Safe Drag Test")
                .setMessage("The app will attempt a very small reversible drag on:\n\n"
                        + selectedShortcut.label + "\n\nNo folder will be created and no other shortcut will be touched. The pointer will move only a few pixels and return toward the original position before release. Continue?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Run Test", (d, w) -> runSafeDrag())
                .show();
    }

    private void runSafeDrag() {
        final HomeAccessibilityService service = HomeAccessibilityService.getInstance();
        if (service == null) { append("TEST FAILED: accessibility service stopped."); return; }
        status.setText("Running Safe Drag Test…"); safeDrag.setEnabled(false);
        service.runSafeDragTest(selectedShortcut, new HomeAccessibilityService.TestCallback() {
            @Override public void onResult(String result) { status.setText(result); refreshReport(); }
        });
    }

    private void refreshReport() {
        HomeAccessibilityService service = HomeAccessibilityService.getInstance();
        String saved = getSharedPreferences("diagnostic", MODE_PRIVATE)
                .getString("last_report", "No scan yet. Enable the service, then scan the Home Screen.");
        report.setText(saved);
        status.setText(service == null ? "Service: OFF" : "Service: ON");
        selectedShortcut = service == null ? null : service.getReliableShortcut();
        safeDrag.setEnabled(selectedShortcut != null && service != null && service.canPerformGestureNow());
    }

    private void append(String message) { report.setText(report.getText() + "\n\n" + message); }
}
