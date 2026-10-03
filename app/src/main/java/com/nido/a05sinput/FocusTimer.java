package com.nido.a05sinput;

/** Pomodoro-style focus/break cycle. Time is passed in so the logic stays testable. */
final class FocusTimer {
    enum Phase { OFF, FOCUS, BREAK }
    enum Event { NONE, FOCUS_DONE, BREAK_DONE }

    private long focusMs;
    private long breakMs;
    private Phase phase = Phase.OFF;
    private long endsAt;

    FocusTimer(long focusMs, long breakMs) {
        setDurations(focusMs, breakMs);
    }

    void setDurations(long focusMs, long breakMs) {
        this.focusMs = Math.max(60_000, focusMs);
        this.breakMs = Math.max(60_000, breakMs);
    }

    void startFocus(long now) {
        phase = Phase.FOCUS;
        endsAt = now + focusMs;
    }

    void startBreak(long now) {
        phase = Phase.BREAK;
        endsAt = now + breakMs;
    }

    void stop() {
        phase = Phase.OFF;
        endsAt = 0;
    }

    /** Advances the cycle; returns an event once when a phase ends. */
    Event tick(long now) {
        if (phase == Phase.FOCUS && now >= endsAt) {
            startBreak(now);
            return Event.FOCUS_DONE;
        }
        if (phase == Phase.BREAK && now >= endsAt) {
            stop();
            return Event.BREAK_DONE;
        }
        return Event.NONE;
    }

    Phase phase() {
        return phase;
    }

    long remaining(long now) {
        return phase == Phase.OFF ? 0 : Math.max(0, endsAt - now);
    }
}
