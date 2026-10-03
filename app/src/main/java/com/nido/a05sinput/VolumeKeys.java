package com.nido.a05sinput;

/**
 * Volume keys as an Anki remote: Volume Down shows the answer and then grades Good, Volume Up
 * grades Again, and an optional long press runs a chosen action (Undo, Flag, …). With no
 * long-press action the key acts on press; with one it acts on release so the two can be
 * told apart. Used by the on-screen remote and by screen-off pocket mode.
 */
final class VolumeKeys {
    interface Config {
        /** Action for a long press, or null to act immediately on press. */
        AnkiAction longPressAction(boolean volumeUp);
    }

    interface Feedback {
        void onVolumeAction(AnkiAction action, boolean longPress);
    }

    private final AnkiCommander commander;
    private final Config config;
    private final Feedback feedback;
    private final PressGesture up = new PressGesture();
    private final PressGesture down = new PressGesture();
    private boolean lastWasUp;

    VolumeKeys(AnkiCommander commander, Config config, Feedback feedback) {
        this.commander = commander;
        this.config = config;
        this.feedback = feedback;
    }

    /** A key-down or auto-repeat. */
    void press(boolean volumeUp, long now) {
        lastWasUp = volumeUp;
        PressGesture gesture = volumeUp ? up : down;
        AnkiAction longAction = config.longPressAction(volumeUp);
        if (longAction == null) {
            boolean first = !gesture.isHeld();
            gesture.press(now);
            if (first) shortPress(volumeUp);
            return;
        }
        if (gesture.press(now) == PressGesture.Result.LONG) longPress(longAction);
    }

    void release(boolean volumeUp, long now) {
        PressGesture gesture = volumeUp ? up : down;
        AnkiAction longAction = config.longPressAction(volumeUp);
        PressGesture.Result result = gesture.release(now);
        if (longAction == null) return;
        if (result == PressGesture.Result.SHORT) shortPress(volumeUp);
        else if (result == PressGesture.Result.LONG) longPress(longAction);
    }

    /** A release whose key is unknown (pocket mode reports only "released"). */
    void releaseLast(long now) {
        release(lastWasUp, now);
    }

    /** Call a few times a second; resolves presses whose release never arrived. */
    void tick(long now) {
        resolveTimeout(true, now);
        resolveTimeout(false, now);
    }

    boolean isHeld() {
        return up.isHeld() || down.isHeld();
    }

    void cancel() {
        up.cancel();
        down.cancel();
    }

    private void resolveTimeout(boolean volumeUp, long now) {
        PressGesture gesture = volumeUp ? up : down;
        PressGesture.Result result = gesture.timeout(now);
        if (result == PressGesture.Result.SHORT && config.longPressAction(volumeUp) != null)
            shortPress(volumeUp);
    }

    private void shortPress(boolean volumeUp) {
        AnkiAction action = volumeUp ? commander.volumeUp() : commander.volumeDown();
        if (feedback != null) feedback.onVolumeAction(action, false);
    }

    private void longPress(AnkiAction action) {
        commander.perform(action);
        if (feedback != null) feedback.onVolumeAction(action, true);
    }
}
