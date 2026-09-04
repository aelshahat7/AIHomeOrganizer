package com.aelshahat.homeorganizer;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Handler;
import android.os.Looper;

public final class GestureController {
    public interface Callback { void onSuccess(); void onFailure(String reason); }
    private final AccessibilityService service;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean running;

    public GestureController(AccessibilityService service) { this.service = service; }
    public boolean isRunning() { return running; }
    public boolean canPerformGesture() {
        if (service == null) return false;
        AccessibilityServiceInfo info = service.getServiceInfo();
        return info != null && (info.getCapabilities() & AccessibilityServiceInfo.CAPABILITY_CAN_PERFORM_GESTURES) != 0;
    }
    public void cancel() { running = false; handler.removeCallbacksAndMessages(null); }

    public void tap(int x, int y, Callback cb) { Path p = new Path(); p.moveTo(x, y); run(p, 0, 180, cb); }
    public void longPress(int x, int y, Callback cb) { Path p = new Path(); p.moveTo(x, y); p.lineTo(x, y); run(p, 0, 750, cb); }

    public void safeDrag(int x, int y, int dx, int dy, Callback cb) {
        if (running) { cb.onFailure("GESTURE_BUSY"); return; }
        if (!canPerformGesture()) { cb.onFailure("GESTURE_CAPABILITY_UNAVAILABLE"); return; }
        int sx = Math.max(-80, Math.min(80, dx));
        int sy = Math.max(-80, Math.min(80, dy));
        Path p = new Path(); p.moveTo(x, y); p.lineTo(x, y); p.lineTo(x + sx, y + sy); p.lineTo(x + Math.round(sx * .25f), y + Math.round(sy * .25f));
        run(p, 0, 1250, cb);
    }

    public void drag(int x1, int y1, int x2, int y2, long duration, Callback cb) {
        if (running) { cb.onFailure("GESTURE_BUSY"); return; }
        if (!canPerformGesture()) { cb.onFailure("GESTURE_CAPABILITY_UNAVAILABLE"); return; }
        Path p = new Path(); p.moveTo(x1, y1); p.lineTo(x2, y2); run(p, 0, Math.max(700, Math.min(3000, duration)), cb);
    }

    public void dragAfterLongPress(int x1, int y1, int x2, int y2, long holdAndMoveDuration, Callback cb) {
        if (running) { cb.onFailure("GESTURE_BUSY"); return; }
        if (!canPerformGesture()) { cb.onFailure("GESTURE_CAPABILITY_UNAVAILABLE"); return; }
        int safeX = Math.max(-3000, Math.min(3000, x2));
        int safeY = Math.max(-3000, Math.min(3000, y2));
        Path p = new Path();
        p.moveTo(x1, y1);
        p.lineTo(x1, y1);
        p.lineTo(safeX, safeY);
        run(p, 0, Math.max(900, Math.min(3000, holdAndMoveDuration)), cb);
    }

    private void run(Path path, long start, long duration, Callback cb) {
        if (running) { cb.onFailure("GESTURE_BUSY"); return; }
        if (!canPerformGesture()) { cb.onFailure("GESTURE_CAPABILITY_UNAVAILABLE"); return; }
        running = true;
        GestureDescription g = new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(path, start, duration)).build();
        try {
            boolean ok = service.dispatchGesture(g, new AccessibilityService.GestureResultCallback() {
                @Override public void onCompleted(GestureDescription d) { finish(cb, true, ""); }
                @Override public void onCancelled(GestureDescription d) { finish(cb, false, "GESTURE_CANCELLED"); }
            }, handler);
            if (!ok) finish(cb, false, "GESTURE_DISPATCH_REJECTED");
            else handler.postDelayed(() -> { if (running) finish(cb, false, "GESTURE_TIMEOUT"); }, duration + 1800);
        } catch (RuntimeException e) { finish(cb, false, "GESTURE_ERROR_" + e.getClass().getSimpleName()); }
    }

    private void finish(Callback cb, boolean success, String reason) {
        if (!running) return;
        running = false; handler.removeCallbacksAndMessages(null);
        if (success) cb.onSuccess(); else cb.onFailure(reason);
    }
}
