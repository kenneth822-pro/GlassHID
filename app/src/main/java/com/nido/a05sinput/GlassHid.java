package com.nido.a05sinput;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Process-wide state shared by the activity, the foreground service, and pocket mode: the
 * input mode, the Bluetooth HID link, the Anki commander and keymap, the study coach, and
 * live Anki desktop info from the USB helper.
 */
final class GlassHid {
    static final int MODE_OFF = 0;
    static final int MODE_BLUETOOTH = 1;
    static final int MODE_USB = 2;

    static final int TARGET_AUTO = 0;
    static final int TARGET_DESKTOP = 1;
    static final int TARGET_ANKIDROID = 2;

    /** The USB helper connection, provided by the activity while it is alive. */
    interface UsbLink {
        boolean connected();

        void send(String line);
    }

    interface Listener {
        /** Mode, target, pocket mode, or live info changed. Always called on the main thread. */
        void onRuntimeChanged();
    }

    private static GlassHid instance;

    final Context app;
    final Handler handler;
    final SharedPreferences prefs;
    final AnkiCommander commander;
    final StudyCoach coach;
    final PocketMode pocket;

    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private BluetoothHidController bluetooth;
    private volatile int mode;
    private volatile UsbLink usbLink;
    private AnkiLiveInfo liveInfo;
    private boolean activityVisible;
    private boolean serviceRunning;

    static synchronized GlassHid get(Context context) {
        if (instance == null) instance = new GlassHid(context.getApplicationContext());
        return instance;
    }

    /** Tests get a fresh application each run; drop state tied to the previous one. */
    static synchronized void resetForTests() {
        if (instance != null) {
            instance.pocket.stop();
            instance.coach.setEngaged("remote", false);
            if (instance.bluetooth != null) instance.bluetooth.destroy();
        }
        instance = null;
        HidService.resetForTests();
    }

    private GlassHid(Context app) {
        this.app = app;
        handler = new Handler(Looper.getMainLooper());
        prefs = app.getSharedPreferences("controls", Context.MODE_PRIVATE);
        mode = prefs.getInt("active_mode", MODE_OFF);
        commander = new AnkiCommander(output, AnkiKeymap.decode(prefs.getString("anki_keymap", "")));
        coach = new StudyCoach(app, handler);
        commander.setObserver(coach);
        pocket = new PocketMode(this);
    }

    // ---------------------------------------------------------------- Bluetooth

    /** The HID controller, recreated if a previous one was released. */
    BluetoothHidController bluetooth() {
        if (bluetooth == null || bluetooth.isDestroyed()) {
            bluetooth = new BluetoothHidController(app, handler);
            bluetooth.setForeground(activityVisible || serviceRunning);
            bluetooth.setActive(mode == MODE_BLUETOOTH);
        }
        return bluetooth;
    }

    /** Unregisters HID; only when neither the activity nor the service needs it. */
    void releaseBluetooth() {
        if (bluetooth != null && !serviceRunning && !activityVisible) {
            bluetooth.destroy();
            bluetooth = null;
        }
    }

    void setActivityVisible(boolean visible) {
        activityVisible = visible;
        updateKeepAlive();
    }

    void setServiceRunning(boolean running) {
        serviceRunning = running;
        updateKeepAlive();
    }

    boolean isActivityVisible() {
        return activityVisible;
    }

    private void updateKeepAlive() {
        // Reconnection work runs while the app is on screen or the service keeps it alive.
        bluetooth().setForeground(activityVisible || serviceRunning);
    }

    // ---------------------------------------------------------------- Mode and target

    int mode() {
        return mode;
    }

    void setMode(int newMode) {
        if (newMode == mode) return;
        mode = newMode;
        prefs.edit().putInt("active_mode", newMode).apply();
        bluetooth().setActive(newMode == MODE_BLUETOOTH);
        if (newMode != MODE_BLUETOOTH) pocket.stop();
        if (newMode != MODE_USB) setLiveInfo(null);
        HidService.sync(app);
        notifyChanged();
    }

    /** Keep Bluetooth input alive in the background (foreground service). */
    boolean backgroundEnabled() {
        return prefs.getBoolean("background_input", true);
    }

    void setBackgroundEnabled(boolean enabled) {
        prefs.edit().putBoolean("background_input", enabled).apply();
        HidService.sync(app);
        notifyChanged();
    }

    int targetSetting() {
        return prefs.getInt("anki_target", TARGET_AUTO);
    }

    void setTargetSetting(int setting) {
        prefs.edit().putInt("anki_target", setting).apply();
        notifyChanged();
    }

    /** AnkiDroid when chosen, or (AUTO) when the Bluetooth host is a phone or tablet. */
    AnkiKeymap.Target target() {
        if (mode != MODE_BLUETOOTH) return AnkiKeymap.Target.DESKTOP;
        int setting = targetSetting();
        if (setting == TARGET_DESKTOP) return AnkiKeymap.Target.DESKTOP;
        if (setting == TARGET_ANKIDROID) return AnkiKeymap.Target.ANKIDROID;
        BluetoothHidController hid = bluetooth();
        return hid.isMobileHost(hid.currentHost()) ? AnkiKeymap.Target.ANKIDROID : AnkiKeymap.Target.DESKTOP;
    }

    /** Long-press options for the volume keys, in the order the settings cycle through. */
    static final AnkiAction[] LONG_PRESS_OPTIONS = {null, AnkiAction.UNDO, AnkiAction.FLAG_RED,
            AnkiAction.MARK, AnkiAction.BURY_CARD, AnkiAction.SUSPEND_CARD, AnkiAction.REPLAY,
            AnkiAction.HARD, AnkiAction.EASY, AnkiAction.EDIT};

    /** Action for holding a volume key; null means the key acts as soon as it is pressed. */
    AnkiAction longPressAction(boolean volumeUp) {
        String stored = prefs.getString(volumeUp ? "anki_long_vol_up" : "anki_long_vol_down",
                volumeUp ? AnkiAction.UNDO.name() : AnkiAction.FLAG_RED.name());
        return AnkiAction.fromName(stored);
    }

    void setLongPressAction(boolean volumeUp, AnkiAction action) {
        prefs.edit().putString(volumeUp ? "anki_long_vol_up" : "anki_long_vol_down",
                action == null ? "OFF" : action.name()).apply();
        notifyChanged();
    }

    void saveKeymap() {
        prefs.edit().putString("anki_keymap", commander.keymap().encode()).apply();
    }

    // ---------------------------------------------------------------- USB and live info

    void setUsbLink(UsbLink link) {
        usbLink = link;
    }

    /** Clears the link only if it is still {@code link}; a newer activity may own it now. */
    void clearUsbLink(UsbLink link) {
        if (usbLink == link) usbLink = null;
    }

    boolean usbConnected() {
        UsbLink link = usbLink;
        return link != null && link.connected();
    }

    /** Live Anki desktop state, or null when the helper is not relaying any. */
    AnkiLiveInfo liveInfo() {
        return liveInfo;
    }

    void setLiveInfo(AnkiLiveInfo info) {
        AnkiLiveInfo previous = liveInfo;
        liveInfo = info;
        if (info != null && info.reviewing() &&
                (previous == null || !previous.reviewing() || previous.cardId != info.cardId)) {
            // A new card is on screen in Anki: the next Volume Down shows its answer.
            commander.questionShown();
            coach.questionShown();
        }
        boolean changed = previous == null ? info != null : info == null ||
                !previous.state.equals(info.state) || previous.cardId != info.cardId ||
                previous.reviewedToday != info.reviewedToday || previous.newCount != info.newCount ||
                previous.learnCount != info.learnCount || previous.reviewCount != info.reviewCount ||
                !previous.deck.equals(info.deck);
        if (changed) notifyChanged();
    }

    // ---------------------------------------------------------------- Listeners

    void addListener(Listener listener) {
        if (!listeners.contains(listener)) listeners.add(listener);
    }

    void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    void notifyChanged() {
        handler.post(() -> {
            for (Listener listener : listeners) listener.onRuntimeChanged();
        });
    }

    // ---------------------------------------------------------------- Key output

    private final AnkiCommander.Output output = new AnkiCommander.Output() {
        @Override public boolean canSend() {
            if (mode == MODE_BLUETOOTH) return bluetooth != null && bluetooth.isInputLive();
            return mode == MODE_USB && usbConnected();
        }

        @Override public void send(KeyChord chord) {
            if (mode == MODE_BLUETOOTH) {
                bluetooth().sendKey(chord.modifiers, chord.usage);
            } else if (mode == MODE_USB) {
                UsbLink link = usbLink;
                String command = chord.usbCommand();
                if (link != null && command != null) link.send(command);
            }
        }

        @Override public AnkiKeymap.Target target() {
            return GlassHid.this.target();
        }
    };
}
