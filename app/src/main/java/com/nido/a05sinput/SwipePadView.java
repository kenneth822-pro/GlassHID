package com.nido.a05sinput;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

/**
 * Full-surface grading: tap to flip, swipe left for Again, right for Good, up for Easy,
 * down for Hard, and hold to undo. Direction hints are drawn on the pad.
 */
final class SwipePadView extends View {
    interface Callback {
        void onGesture(SwipeClassifier.Gesture gesture);

        void onHold();
    }

    private static final long HOLD_MS = 650;

    private final Callback callback;
    private final Paint hint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint center = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final float density;
    private float downX;
    private float downY;
    private long downAt;
    private boolean holdFired;
    private final Runnable hold = new Runnable() {
        @Override public void run() {
            holdFired = true;
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            callback.onHold();
        }
    };

    SwipePadView(Context context, Callback callback, int textColor) {
        super(context);
        this.callback = callback;
        density = context.getResources().getDisplayMetrics().density;
        hint.setColor(textColor);
        hint.setTextAlign(Paint.Align.CENTER);
        hint.setTypeface(Typeface.DEFAULT_BOLD);
        hint.setTextSize(15 * density);
        center.setColor(textColor);
        center.setTextAlign(Paint.Align.CENTER);
        center.setTypeface(Typeface.DEFAULT_BOLD);
        center.setTextSize(20 * density);
        setContentDescription("Swipe grading pad");
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        float pad = 18 * density;
        canvas.drawText("↑ EASY · 4", w / 2, pad + hint.getTextSize(), hint);
        canvas.drawText("↓ HARD · 2", w / 2, h - pad, hint);
        canvas.save();
        canvas.rotate(-90, pad + hint.getTextSize(), h / 2);
        canvas.drawText("← AGAIN · 1", pad + hint.getTextSize(), h / 2, hint);
        canvas.restore();
        canvas.save();
        canvas.rotate(90, w - pad - hint.getTextSize(), h / 2);
        canvas.drawText("GOOD · 3 →", w - pad - hint.getTextSize(), h / 2, hint);
        canvas.restore();
        canvas.drawText("TAP = FLIP", w / 2, h / 2 - 4 * density, center);
        canvas.drawText("HOLD = UNDO", w / 2, h / 2 + center.getTextSize(), hint);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX();
                downY = event.getY();
                downAt = SystemClock.uptimeMillis();
                holdFired = false;
                setPressed(true);
                handler.postDelayed(hold, HOLD_MS);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (Math.hypot(event.getX() - downX, event.getY() - downY) > 14 * density)
                    handler.removeCallbacks(hold);
                return true;
            case MotionEvent.ACTION_UP:
                handler.removeCallbacks(hold);
                setPressed(false);
                if (!holdFired) {
                    SwipeClassifier.Gesture gesture = SwipeClassifier.classify(
                            event.getX() - downX, event.getY() - downY,
                            SystemClock.uptimeMillis() - downAt, 14 * density, 70 * density);
                    if (gesture != SwipeClassifier.Gesture.NONE) {
                        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                        callback.onGesture(gesture);
                    }
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                handler.removeCallbacks(hold);
                setPressed(false);
                return true;
            default:
                return true;
        }
    }

    @Override protected void onDetachedFromWindow() {
        handler.removeCallbacks(hold);
        super.onDetachedFromWindow();
    }
}
