package com.aelshahat.homeorganizer;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    private TextView status;
    private TextView report;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 32, 32, 32);

        TextView title = new TextView(this);
        title.setText("AI Home Organizer\nDiagnostic");
        title.setTextSize(24);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        status = new TextView(this);
        status.setTextSize(16);
        status.setPadding(0, 24, 0, 24);
        root.addView(status, new LinearLayout.LayoutParams(-1, -2));

        Button settings = new Button(this);
        settings.setText("Enable Accessibility Service");
        settings.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        root.addView(settings, new LinearLayout.LayoutParams(-1, -2));

        Button scan = new Button(this);
        scan.setText("Scan Home Screen");
        scan.setOnClickListener(v -> {
            HomeAccessibilityService service = HomeAccessibilityService.getInstance();
            if (service == null) {
                status.setText("Accessibility Service is not running. Enable it first.");
                return;
            }
            status.setText("Scanning Home Screen...");
            service.scanHomeScreen();
        });
        root.addView(scan, new LinearLayout.LayoutParams(-1, -2));

        report = new TextView(this);
        report.setTextSize(13);
        report.setPadding(0, 24, 0, 0);
        root.addView(report, new LinearLayout.LayoutParams(-1, 0, 1));

        setContentView(root);
        refreshReport();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshReport();
    }

    private void refreshReport() {
        String saved = getSharedPreferences("diagnostic", MODE_PRIVATE)
                .getString("last_report", "No scan yet. Enable the service, then scan the Home Screen.");
        report.setText(saved);
        status.setText(HomeAccessibilityService.getInstance() == null
                ? "Service: OFF"
                : "Service: ON");
    }
}
