package com.nido.a05sinput;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Study companion for the Anki remote: session and daily counts, streaks, a daily goal, a
 * focus/break timer, and an optional nudge when a question has been up too long. Counts come
 * from remote presses that reached a host, so they track Anki closely but are not Anki's own
 * records. Everything stays on the phone.
 */
final class StudyCoach implements AnkiCommander.Observer {
    interface Listener {
        /** Counts, timer, or settings changed. Called on the main thread. */
        void onCoachChanged();

        /** A goal, break, or back-to-work moment worth showing. */
        void onCoachAlert(String message);
    }

    static final int[] GOAL_STEPS = {0, 50, 100, 150, 200, 250, 300, 400, 500, 750, 1000};
    static final int[] FOCUS_STEPS = {0, 10, 15, 20, 25, 30, 40, 45, 50, 60};
    static final int[] BREAK_STEPS = {3, 5, 10, 15, 20};
    static final int[] NUDGE_STEPS = {0, 8, 10, 15, 20, 30, 45, 60};

    private static final long TICK_MS = 1000;

    private final Context app;
    private final Handler handler;
    private final SharedPreferences prefs;
    private final Vibrator vibrator;
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private final Set<String> engagedBy = new HashSet<>();
    private final AnswerNudge nudge = new AnswerNudge();
    private final ReviewSession session;
    private final StudyHistory history;
    private final FocusTimer timer;
    private int goal;
    private int focusMinutes;
    private int breakMinutes;
    private int nudgeSeconds;
    private boolean ticking;
    private String goalCelebratedOn;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!ticking) return;
            onTick();
            handler.postDelayed(this, TICK_MS);
        }
    };

    StudyCoach(Context context, Handler handler) {
        app = context.getApplicationContext();
        this.handler = handler;
        prefs = app.getSharedPreferences("study", Context.MODE_PRIVATE);
        if (Build.VERSION.SDK_INT >= 31) {
            VibratorManager manager = (VibratorManager) app.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            vibrator = manager == null ? null : manager.getDefaultVibrator();
        } else {
            vibrator = (Vibrator) app.getSystemService(Context.VIBRATOR_SERVICE);
        }
        session = ReviewSession.decode(prefs.getString("session", ""));
        history = StudyHistory.decode(prefs.getString("history", ""));
        goal = prefs.getInt("goal", 0);
        focusMinutes = prefs.getInt("focus_minutes", 0);
        breakMinutes = prefs.getInt("break_minutes", 5);
        nudgeSeconds = prefs.getInt("nudge_seconds", 0);
        goalCelebratedOn = prefs.getString("goal_celebrated_on", "");
        timer = new FocusTimer(Math.max(1, focusMinutes) * 60_000L, breakMinutes * 60_000L);
        history.trim(today());
    }

    // ---------------------------------------------------------------- Events

    @Override public void onAction(AnkiAction action, boolean delivered) {
        if (!delivered) return;
        long now = now();
        if (session.isStale(now)) session.reset();
        LocalDate today = today();
        long activeBefore = session.activeMs();
        if (action.isGrade()) {
            session.grade(action.ease, now);
            history.addGrade(today, action.ease, 1);
        } else if (action == AnkiAction.UNDO) {
            int ease = session.undo(now);
            if (ease > 0) history.addGrade(today, ease, -1);
        } else {
            session.touch(now);
        }
        history.addActive(today, session.activeMs() - activeBefore);

        if (action == AnkiAction.FLIP) nudge.disarm();
        else if (action.showsNextQuestion()) armNudge(now);
        if (action.isGrade() && focusMinutes > 0 && timer.phase() == FocusTimer.Phase.OFF)
            timer.startFocus(now);

        checkGoal(today);
        save();
        notifyChanged();
    }

    /** Live info saw a new card: start the answer-time nudge for it. */
    void questionShown() {
        armNudge(now());
    }

    /** Screens and pocket mode that show the coach keep its one-second clock running. */
    void setEngaged(String source, boolean engaged) {
        if (engaged) engagedBy.add(source);
        else engagedBy.remove(source);
        boolean shouldTick = !engagedBy.isEmpty();
        if (shouldTick && !ticking) {
            ticking = true;
            handler.postDelayed(tick, TICK_MS);
        } else if (!shouldTick && ticking) {
            ticking = false;
            handler.removeCallbacks(tick);
        }
    }

    private void onTick() {
        long now = now();
        if (nudge.due(now)) vibrate(new long[]{0, 35, 90, 35});
        FocusTimer.Event event = timer.tick(now);
        if (event == FocusTimer.Event.FOCUS_DONE) {
            vibrate(new long[]{0, 320, 160, 320});
            alert("Focus block done — take a " + breakMinutes + " min break");
        } else if (event == FocusTimer.Event.BREAK_DONE) {
            vibrate(new long[]{0, 120, 100, 120, 100, 120});
            alert("Break over — back to your reviews");
        }
        if (timer.phase() != FocusTimer.Phase.OFF || session.isStarted()) notifyChanged();
    }

    private void armNudge(long now) {
        if (nudgeSeconds > 0) nudge.arm(now, nudgeSeconds * 1000L);
        else nudge.disarm();
    }

    private void checkGoal(LocalDate today) {
        if (goal <= 0 || history.day(today).total() < goal) return;
        String stamp = today.toString();
        if (stamp.equals(goalCelebratedOn)) return;
        goalCelebratedOn = stamp;
        prefs.edit().putString("goal_celebrated_on", stamp).apply();
        vibrate(new long[]{0, 60, 70, 60, 70, 180});
        alert("Daily goal reached: " + goal + " cards 🎉");
    }

    // ---------------------------------------------------------------- Readouts

    ReviewSession session() {
        return session;
    }

    StudyHistory history() {
        return history;
    }

    int todayTotal() {
        return history.day(today()).total();
    }

    int streak() {
        return history.streak(today());
    }

    int goal() {
        return goal;
    }

    int focusMinutes() {
        return focusMinutes;
    }

    int breakMinutes() {
        return breakMinutes;
    }

    int nudgeSeconds() {
        return nudgeSeconds;
    }

    FocusTimer.Phase focusPhase() {
        return timer.phase();
    }

    long focusRemainingMs() {
        return timer.remaining(now());
    }

    // ---------------------------------------------------------------- Settings and controls

    void setGoal(int value) {
        goal = Math.max(0, value);
        prefs.edit().putInt("goal", goal).apply();
        notifyChanged();
    }

    void setFocusMinutes(int minutes) {
        focusMinutes = Math.max(0, minutes);
        prefs.edit().putInt("focus_minutes", focusMinutes).apply();
        timer.setDurations(Math.max(1, focusMinutes) * 60_000L, breakMinutes * 60_000L);
        if (focusMinutes == 0) timer.stop();
        notifyChanged();
    }

    void setBreakMinutes(int minutes) {
        breakMinutes = Math.max(1, minutes);
        prefs.edit().putInt("break_minutes", breakMinutes).apply();
        timer.setDurations(Math.max(1, focusMinutes) * 60_000L, breakMinutes * 60_000L);
        notifyChanged();
    }

    void setNudgeSeconds(int seconds) {
        nudgeSeconds = Math.max(0, seconds);
        prefs.edit().putInt("nudge_seconds", nudgeSeconds).apply();
        if (nudgeSeconds == 0) nudge.disarm();
        notifyChanged();
    }

    void startFocus() {
        if (focusMinutes == 0) setFocusMinutes(25);
        timer.startFocus(now());
        notifyChanged();
    }

    void stopFocus() {
        timer.stop();
        notifyChanged();
    }

    void newSession() {
        session.reset();
        nudge.disarm();
        save();
        notifyChanged();
    }

    void clearHistory() {
        history.clear();
        session.reset();
        goalCelebratedOn = "";
        prefs.edit().putString("goal_celebrated_on", "").apply();
        save();
        notifyChanged();
    }

    /** Next value in {@code steps} after {@code current}, wrapping, or stepping back with -1. */
    static int step(int[] steps, int current, int direction) {
        int index = 0;
        for (int i = 0; i < steps.length; i++) if (steps[i] <= current) index = i;
        index = Math.max(0, Math.min(steps.length - 1, index + direction));
        return steps[index];
    }

    // ---------------------------------------------------------------- Plumbing

    void addListener(Listener listener) {
        if (!listeners.contains(listener)) listeners.add(listener);
    }

    void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    private void notifyChanged() {
        handler.post(() -> {
            for (Listener listener : listeners) listener.onCoachChanged();
        });
    }

    private void alert(String message) {
        handler.post(() -> {
            for (Listener listener : listeners) listener.onCoachAlert(message);
        });
    }

    private void save() {
        prefs.edit()
                .putString("session", session.encode())
                .putString("history", history.encode())
                .apply();
    }

    private void vibrate(long[] pattern) {
        try {
            if (vibrator != null && vibrator.hasVibrator())
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
        } catch (RuntimeException ignored) {
            // A vibrator that refuses must never break a review.
        }
    }

    private static long now() {
        return System.currentTimeMillis();
    }

    private static LocalDate today() {
        return LocalDate.now();
    }
}
