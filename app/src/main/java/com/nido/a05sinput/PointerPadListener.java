package com.nido.a05sinput;

import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

/** Small aim-and-tap pad: drag moves the host's pointer, a tap clicks under it. */
final class PointerPadListener implements View.OnTouchListener {
    private static final long TAP_MAX_MS = 260;

    private final ScrollPadListener.Host host;
    private float downX;
    private float downY;
    private float lastX;
    private float lastY;
    private long downAt;
    private boolean dragging;

    PointerPadListener(ScrollPadListener.Host host) {
        this.host = host;
    }

    @Override public boolean onTouch(View view, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = lastX = event.getX();
                downY = lastY = event.getY();
                downAt = SystemClock.uptimeMillis();
                dragging = false;
                host.haptic(view, HapticFeedbackConstants.VIRTUAL_KEY);
                host.scrollVisual(view, true);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!dragging && Math.hypot(event.getX() - downX, event.getY() - downY) > host.dp(10))
                    dragging = true;
                if (dragging) {
                    // Aiming wants more reach than the full trackpad: the pad is small.
                    float scale = host.pointerPercent() * 1.6f / 100f;
                    int dx = Math.round((event.getX() - lastX) * scale);
                    int dy = Math.round((event.getY() - lastY) * scale);
                    if (dx != 0 || dy != 0) {
                        host.move(dx, dy);
                        lastX = event.getX();
                        lastY = event.getY();
                    }
                }
                return true;
            case MotionEvent.ACTION_UP:
                host.scrollVisual(view, false);
                if (!dragging && SystemClock.uptimeMillis() - downAt <= TAP_MAX_MS) {
                    host.clickLeft();
                    host.haptic(view, HapticFeedbackConstants.CONFIRM);
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                host.scrollVisual(view, false);
                return true;
            default:
                return true;
        }
    }
}
