package com.nido.a05sinput;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Counts the grades given through the remote in one sitting. Time between actions counts
 * as study time up to {@link #MAX_CARD_MS} per gap, like Anki's own answer-time cap, so a
 * break does not drag the pace down.
 */
final class ReviewSession {
    static final long MAX_CARD_MS = 60_000;
    /** A session ends after this long without any remote action. */
    static final long SESSION_TIMEOUT_MS = 30 * 60_000;

    private final int[] counts = new int[5];
    private final Deque<Integer> grades = new ArrayDeque<>();
    private boolean started;
    private long startedAt;
    private long lastActionAt;
    private long activeMs;

    /** Records any remote action (flip, grade, undo) for study time. */
    void touch(long now) {
        if (!started) {
            started = true;
            startedAt = now;
        } else if (now > lastActionAt) {
            activeMs += Math.min(now - lastActionAt, MAX_CARD_MS);
        }
        lastActionAt = now;
    }

    void grade(int ease, long now) {
        if (ease < 1 || ease > 4) return;
        touch(now);
        counts[ease]++;
        grades.push(ease);
    }

    /** Takes back the latest grade; returns its ease, or 0 if there was none. */
    int undo(long now) {
        touch(now);
        Integer ease = grades.poll();
        if (ease == null) return 0;
        counts[ease]--;
        return ease;
    }

    boolean isStale(long now) {
        return started && now - lastActionAt > SESSION_TIMEOUT_MS;
    }

    void reset() {
        for (int i = 0; i < counts.length; i++) counts[i] = 0;
        grades.clear();
        started = false;
        startedAt = 0;
        lastActionAt = 0;
        activeMs = 0;
    }

    int count(int ease) {
        return ease >= 1 && ease <= 4 ? counts[ease] : 0;
    }

    int total() {
        return counts[1] + counts[2] + counts[3] + counts[4];
    }

    /** Share of Again grades, 0..1. */
    double againRate() {
        int total = total();
        return total == 0 ? 0 : counts[1] / (double) total;
    }

    long activeMs() {
        return activeMs;
    }

    boolean isStarted() {
        return started;
    }

    long startedAt() {
        return startedAt;
    }

    /** Cards per minute of study time; 0 until there is half a minute of data. */
    double cardsPerMinute() {
        return activeMs < 30_000 ? 0 : total() / (activeMs / 60_000.0);
    }

    String encode() {
        if (!started) return "";
        StringBuilder out = new StringBuilder();
        out.append(startedAt).append(',').append(lastActionAt).append(',').append(activeMs).append(',');
        for (Integer ease : grades) out.append(ease);
        return out.toString();
    }

    static ReviewSession decode(String value) {
        ReviewSession session = new ReviewSession();
        if (value == null) return session;
        String[] parts = value.split(",", -1);
        if (parts.length != 4) return session;
        try {
            session.startedAt = Long.parseLong(parts[0]);
            session.started = true;
            session.lastActionAt = Long.parseLong(parts[1]);
            session.activeMs = Long.parseLong(parts[2]);
            // Stored newest first, so rebuild from the oldest.
            for (int i = parts[3].length() - 1; i >= 0; i--) {
                int ease = parts[3].charAt(i) - '0';
                if (ease < 1 || ease > 4) return new ReviewSession();
                session.counts[ease]++;
                session.grades.push(ease);
            }
        } catch (NumberFormatException e) {
            return new ReviewSession();
        }
        return session;
    }
}
