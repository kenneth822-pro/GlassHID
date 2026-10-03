package com.nido.a05sinput;

/**
 * Tells a short press from a long press for a key that reports presses, optional repeats,
 * and an optional release. Pocket mode's media session may report no release at all, so a
 * press that goes quiet for {@link #RELEASE_GAP_MS} counts as released.
 */
final class PressGesture {
    enum Result { NONE, SHORT, LONG }

    static final long LONG_PRESS_MS = 500;
    static final long RELEASE_GAP_MS = 650;

    private boolean held;
    private boolean longFired;
    private long pressedAt;
    private long lastSignalAt;

    /** A key-down or a repeat while held. */
    Result press(long now) {
        // A press that went quiet lost its release (focus moved, say): start over rather
        // than mistake the next press for a long hold.
        if (held && now - lastSignalAt > RELEASE_GAP_MS) held = false;
        if (!held) {
            held = true;
            longFired = false;
            pressedAt = now;
            lastSignalAt = now;
            return Result.NONE;
        }
        lastSignalAt = now;
        if (!longFired && now - pressedAt >= LONG_PRESS_MS) {
            longFired = true;
            return Result.LONG;
        }
        return Result.NONE;
    }

    Result release(long now) {
        if (!held) return Result.NONE;
        held = false;
        if (longFired) return Result.NONE;
        return now - pressedAt >= LONG_PRESS_MS ? Result.LONG : Result.SHORT;
    }

    /** Call periodically; resolves a press whose release never arrived. */
    Result timeout(long now) {
        if (!held || now - lastSignalAt < RELEASE_GAP_MS) return Result.NONE;
        held = false;
        return longFired ? Result.NONE : Result.SHORT;
    }

    boolean isHeld() {
        return held;
    }

    void cancel() {
        held = false;
        longFired = false;
    }
}
