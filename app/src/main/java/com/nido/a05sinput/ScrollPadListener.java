package com.nido.a05sinput;

import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

/** Dedicated one-finger scroll-strip recognizer. */
final class ScrollPadListener implements View.OnTouchListener {
    interface Host {
        int dp(int value);
        int scrollPercent();
        void scroll(int amount);
        void haptic(View view, int kind);
        void scrollVisual(View view, boolean pressed);
        int pointerPercent();
        void move(int dx, int dy);
        void clickLeft();
        /** Called when a finger lifts after scrolling. */
        void scrollFinished();
    }

    private static final long DOUBLE_TAP_MS = 320;

    private final Host host;
    private final Runnable onDoubleTap;
    private float lastY;
    private float remainder;
    private boolean scrolled;
    private float downY;
    private long lastTapAt;

    ScrollPadListener(Host host) {
        this(host, null);
    }

    /** @param onDoubleTap runs on a stationary double tap, or null for scrolling only */
    ScrollPadListener(Host host, Runnable onDoubleTap) {
        this.host = host;
        this.onDoubleTap = onDoubleTap;
    }

    @Override public boolean onTouch(View view, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastY = downY = event.getY();
                remainder = 0;
                scrolled = false;
                host.haptic(view, HapticFeedbackConstants.VIRTUAL_KEY);
                view.animate().scaleX(0.965f).scaleY(0.985f).setDuration(55).start();
                host.scrollVisual(view, true);
                return true;
            case MotionEvent.ACTION_MOVE:
                remainder += event.getY() - lastY;
                lastY = event.getY();
                int stepSize = host.dp(Math.max(4, 1000 / host.scrollPercent()));
                int steps = (int) (remainder / stepSize);
                if (steps != 0) {
                    host.scroll(Math.max(-127, Math.min(127, -steps)));
                    remainder -= steps * stepSize;
                    scrolled = true;
                    host.haptic(view, HapticFeedbackConstants.CLOCK_TICK);
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                view.animate().scaleX(1f).scaleY(1f).setDuration(75).start();
                host.scrollVisual(view, false);
                if (scrolled) {
                    host.scrollFinished();
                } else if (onDoubleTap != null && event.getActionMasked() == MotionEvent.ACTION_UP &&
                        Math.abs(event.getY() - downY) < host.dp(12)) {
                    long now = SystemClock.uptimeMillis();
                    if (now - lastTapAt <= DOUBLE_TAP_MS) {
                        lastTapAt = 0;
                        onDoubleTap.run();
                    } else {
                        lastTapAt = now;
                    }
                }
                return true;
            default:
                return true;
        }
    }
}
