package com.nido.a05sinput;

/** Classifies a one-finger gesture as a tap or a swipe in one of four directions. */
final class SwipeClassifier {
    enum Gesture { NONE, TAP, LEFT, RIGHT, UP, DOWN }

    static final long MAX_TAP_MS = 300;
    static final long MAX_SWIPE_MS = 900;

    private SwipeClassifier() {
    }

    /**
     * @param tapSlop movement (px) below which a quick touch is a tap
     * @param minSwipe movement (px) a swipe must cover
     */
    static Gesture classify(float dx, float dy, long durationMs, float tapSlop, float minSwipe) {
        double distance = Math.hypot(dx, dy);
        if (distance <= tapSlop) return durationMs <= MAX_TAP_MS ? Gesture.TAP : Gesture.NONE;
        if (distance < minSwipe || durationMs > MAX_SWIPE_MS) return Gesture.NONE;
        float ax = Math.abs(dx);
        float ay = Math.abs(dy);
        // Clear diagonals are ignored rather than guessed.
        if (ax > ay * 1.3f) return dx < 0 ? Gesture.LEFT : Gesture.RIGHT;
        if (ay > ax * 1.3f) return dy < 0 ? Gesture.UP : Gesture.DOWN;
        return Gesture.NONE;
    }
}
