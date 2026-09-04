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

    /**
     * Launcher3 folder gesture using ONE continuous pointer stroke.
     * The path stays at the source long enough to cross the long-press threshold,
     * then moves directly to the target without continueStroke/multiple strokes.
     * This is still an Accessibility gesture, because an external app cannot call
     * Launcher3's private Workspace drag APIs directly without modifying/rooting the launcher.
     */
    public void dragAfterLongPress(int x1, int y1, int x2, int y2, long holdAndMoveDuration, Callback cb) {
        if (running) { cb.onFailure("GESTURE_BUSY"); return; }
        if (!canPerformGesture()) { cb.onFailure("GESTURE_CAPABILITY_UNAVAILABLE"); return; }
        long hold = Math.max(750, Math.min(1200, holdAndMoveDuration <= 0 ? 900 : holdAndMoveDuration));
        long move = Math.max(650, Math.min(1800, holdAndMoveDuration <= 0 ? 900 : holdAndMoveDuration));

        Path path = new Path();
        path.moveTo(x1, y1);
        // Same-coordinate segment keeps the pointer down while the long-press threshold elapses.
        path.lineTo(x1, y1);
        // The next segment is the actual drag, with no stroke boundary/pointer reset.
        path.lineTo(x2, y2);

        long total = hold + move;
        running = true;
        appendGestureDiagnostic("GESTURE_FOLDER_SINGLE_STROKE holdMs=" + hold + " moveMs=" + move
                + " from=" + x1 + "," + y1 + " to=" + x2 + "," + y2 + "\n");
        try {
            GestureDescription gesture = new GestureDescription.Builder()
                    .addStroke(new GestureDescription.StrokeDescription(path, 0, total))
                    .build();
            boolean ok = service.dispatchGesture(gesture, new AccessibilityService.GestureResultCallback() {
                @Override public void onCompleted(GestureDescription d) { finish(cb, true, ""); }
                @Override public void onCancelled(GestureDescription d) { finish(cb, false, "GESTURE_CANCELLED"); }
            }, handler);
            if (!ok) finish(cb, false, "GESTURE_DISPATCH_REJECTED");
            else handler.postDelayed(() -> { if (running) finish(cb, false, "GESTURE_TIMEOUT"); }, total + 1800);
        } catch (RuntimeException e) {
            finish(cb, false, "GESTURE_ERROR_" + e.getClass().getSimpleName());
        }
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

    private void appendGestureDiagnostic(String text) {
        if (service instanceof HomeAccessibilityService) {
            HomeAccessibilityService s = (HomeAccessibilityService) service;
            s.appendDiagnostic(text);
        }
    }
}
