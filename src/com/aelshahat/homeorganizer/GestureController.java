package com.aelshahat.homeorganizer;

import android.accessibilityservice.AccessibilityService;
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

    public GestureController(AccessibilityService service) {
        this.service = service;
    }

    public boolean isRunning() { return running; }

    public void cancel() {
        running = false;
        handler.removeCallbacksAndMessages(null);
    }

    public void safeDrag(int x, int y, int deltaX, int deltaY, Callback callback) {
        if (running) {
            callback.onFailure("A gesture is already running.");
            return;
        }
        if (service == null || !service.getServiceInfo().capabilitiesHasGesture()) {
            callback.onFailure("Gesture capability is unavailable.");
            return;
        }
        running = true;
        final int safeDx = Math.max(-12, Math.min(12, deltaX));
        final int safeDy = Math.max(-12, Math.min(12, deltaY));

        Path path = new Path();
        path.moveTo(x, y);
        path.lineTo(x + safeDx, y + safeDy);
        path.lineTo(x, y);

        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, 850))
                .build();

        boolean dispatched;
        try {
            dispatched = service.dispatchGesture(gesture, new AccessibilityService.GestureResultCallback() {
                @Override public void onCompleted(GestureDescription gestureDescription) {
                    finishSuccess(callback);
                }
                @Override public void onCancelled(GestureDescription gestureDescription) {
                    finishFailure(callback, "Launcher cancelled the gesture.");
                }
            }, handler);
        } catch (RuntimeException e) {
            finishFailure(callback, "Gesture dispatch error: " + e.getClass().getSimpleName());
            return;
        }
        if (!dispatched) finishFailure(callback, "Gesture dispatch was rejected.");
        else handler.postDelayed(() -> {
            if (running) finishFailure(callback, "Gesture callback timeout.");
        }, 2500);
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
