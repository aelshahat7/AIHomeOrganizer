package com.aelshahat.homeorganizer;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityNodeInfo;

public final class PageNavigator {
    public interface Callback { void onSuccess(); void onFailure(String reason); }
    private final AccessibilityService service;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean running;

    public PageNavigator(AccessibilityService service) { this.service = service; }
    public boolean isRunning() { return running; }

    public void next(AccessibilityNodeInfo root, Callback callback) {
        if (running) { callback.onFailure("NAVIGATION_BUSY"); return; }
        if (root == null) { callback.onFailure("NAVIGATION_NO_ROOT"); return; }
        Rect r = new Rect(); root.getBoundsInScreen(r);
        if (r.width() < 100 || r.height() < 100) { callback.onFailure("NAVIGATION_INVALID_BOUNDS"); return; }
        try {
            if (service == null || service.getServiceInfo() == null
                    || (service.getServiceInfo().getCapabilities()
                    & android.accessibilityservice.AccessibilityServiceInfo.CAPABILITY_CAN_PERFORM_GESTURES) == 0) {
                callback.onFailure("NAVIGATION_GESTURE_UNAVAILABLE");
                return;
            }
            float y = r.top + r.height() * .50f;
            float startX = r.left + r.width() * .78f;
            float endX = r.left + r.width() * .22f;
            Path path = new Path();
            path.moveTo(startX, y);
            path.lineTo(endX, y);
            GestureDescription g = new GestureDescription.Builder()
                    .addStroke(new GestureDescription.StrokeDescription(path, 0, 500)).build();
            running = true;
            boolean dispatched = service.dispatchGesture(g, new AccessibilityService.GestureResultCallback() {
                @Override public void onCompleted(GestureDescription description) { finish(callback, true, ""); }
                @Override public void onCancelled(GestureDescription description) { finish(callback, false, "NAVIGATION_CANCELLED"); }
            }, handler);
            if (!dispatched) finish(callback, false, "NAVIGATION_DISPATCH_REJECTED");
            else handler.postDelayed(() -> { if (running) finish(callback, false, "NAVIGATION_TIMEOUT"); }, 1800);
        } catch (RuntimeException e) {
            running = false;
            callback.onFailure("NAVIGATION_EXCEPTION_" + e.getClass().getSimpleName());
        }
    }

    private void finish(Callback callback, boolean success, String reason) {
        if (!running) return;
        running = false;
        handler.removeCallbacksAndMessages(null);
        if (success) callback.onSuccess(); else callback.onFailure(reason);
    }

    public void cancel() {
        running = false;
        handler.removeCallbacksAndMessages(null);
    }
}
