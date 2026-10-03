package com.nido.a05sinput;

/** Fires once when a question has been showing longer than the chosen limit. */
final class AnswerNudge {
    private long dueAt;

    void arm(long now, long delayMs) {
        dueAt = delayMs > 0 ? now + delayMs : 0;
    }

    void disarm() {
        dueAt = 0;
    }

    boolean armed() {
        return dueAt != 0;
    }

    /** True exactly once, the first time it is checked at or after the deadline. */
    boolean due(long now) {
        if (dueAt == 0 || now < dueAt) return false;
        dueAt = 0;
        return true;
    }
}
