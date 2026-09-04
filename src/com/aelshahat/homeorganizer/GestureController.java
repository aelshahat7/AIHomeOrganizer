package com.aelshahat.homeorganizer;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Handler;
import android.os.Looper;

public final class GestureController {
    public interface Callback {
        void onSuccess();
        void onFailure(String reason);
    }

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

    public void cancel() {
        running = false;
        handler.removeCallbacksAndMessages(null);
    }

    public void safeDrag(int x, int y, int deltaX, int deltaY, Callback callback) {
        if (running) { callback.onFailure("A gesture is already running."); return; }
        if (!canPerformGesture()) { callback.onFailure("Gesture capability is unavailable."); return; }

        running = true;
        final int safeDx = Math.max(-8, Math.min(8, deltaX));
        final int safeDy = Math.max(-8, Math.min(8, deltaY));

        // Keep the contact near the original point for the first part of the stroke,
        // then move only a few pixels and return to the origin before release.
        Path path = new Path();
        path.moveTo(x, y);
        path.lineTo(x + 1, y);
        path.lineTo(x + safeDx, y + safeDy);
        path.lineTo(x, y);

        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, 1100))
                .build();

        try {
            boolean dispatched = service.dispatchGesture(gesture, new AccessibilityService.GestureResultCallback() {
                @Override public void onCompleted(GestureDescription description) { finishSuccess(callback); }
                @Override public void onCancelled(GestureDescription description) { finishFailure(callback, "Launcher cancelled the gesture."); }
            }, handler);
            if (!dispatched) finishFailure(callback, "Gesture dispatch was rejected.");
            else handler.postDelayed(() -> {
                if (running) finishFailure(callback, "Gesture callback timeout.");
            }, 2500);
        } catch (RuntimeException e) {
            finishFailure(callback, "Gesture dispatch error: " + e.getClass().getSimpleName());
        }
    }

    private void finishSuccess(Callback callback) {
        if (!running) return;
        running = false;
        handler.removeCallbacksAndMessages(null);
        callback.onSuccess();
    }

    private void finishFailure(Callback callback, String reason) {
        if (!running) return;
        running = false;
        handler.removeCallbacksAndMessages(null);
        callback.onFailure(reason);
    }
}
