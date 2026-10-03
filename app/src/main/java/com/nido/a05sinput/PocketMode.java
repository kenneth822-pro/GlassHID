package com.nido.a05sinput;

import android.content.Context;
import android.media.VolumeProvider;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.util.Log;

/**
 * Screen-off reviewing: while active, a media session with remote volume receives the phone's
 * volume keys even with the screen off or another app open, and turns them into Anki actions.
 * Android sends a volume adjustment for every press and repeat, and an ADJUST_SAME on
 * release. A partial wake lock keeps key-up reports on time so no key sticks down on the host.
 * It ends after {@link #AUTO_EXIT_MS} without a press, or when Bluetooth mode ends.
 */
final class PocketMode {
    static final long AUTO_EXIT_MS = 20 * 60_000;
    private static final long WATCH_MS = 200;
    private static final String TAG = "A05sInput";

    private final GlassHid runtime;
    private final VolumeKeys keys;
    private final Vibrator vibrator;
    private MediaSession session;
    private PowerManager.WakeLock wakeLock;
    private long lastPressAt;

    private final Runnable watch = new Runnable() {
        @Override public void run() {
            if (session == null) return;
            long now = SystemClock.uptimeMillis();
            keys.tick(now);
            if (now - lastPressAt > AUTO_EXIT_MS) {
                stop();
                return;
            }
            runtime.handler.postDelayed(this, WATCH_MS);
        }
    };

    PocketMode(GlassHid runtime) {
        this.runtime = runtime;
        Context app = runtime.app;
        if (Build.VERSION.SDK_INT >= 31) {
            VibratorManager manager = (VibratorManager) app.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            vibrator = manager == null ? null : manager.getDefaultVibrator();
        } else {
            vibrator = (Vibrator) app.getSystemService(Context.VIBRATOR_SERVICE);
        }
        keys = new VolumeKeys(runtime.commander, runtime::longPressAction,
                (action, longPress) -> buzz(action));
    }

    boolean isActive() {
        return session != null;
    }

    /** Starts pocket mode; returns false unless Bluetooth input is on and permitted. */
    boolean start() {
        if (session != null) return true;
        if (runtime.mode() != GlassHid.MODE_BLUETOOTH || !runtime.bluetooth().hasPermission()) return false;
        try {
            MediaSession media = new MediaSession(runtime.app, "GlassHID pocket mode");
            media.setPlaybackToRemote(new VolumeProvider(VolumeProvider.VOLUME_CONTROL_RELATIVE, 100, 50) {
                @Override public void onAdjustVolume(int direction) {
                    runtime.handler.post(() -> onVolume(direction));
                }
            });
            media.setPlaybackState(new PlaybackState.Builder()
                    .setState(PlaybackState.STATE_PLAYING, 0, 1f)
                    .build());
            media.setActive(true);
            session = media;
        } catch (RuntimeException e) {
            Log.w(TAG, "Pocket mode media session failed", e);
            session = null;
            return false;
        }
        PowerManager power = (PowerManager) runtime.app.getSystemService(Context.POWER_SERVICE);
        if (power != null) {
            wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GlassHID:pocket");
            wakeLock.setReferenceCounted(false);
            wakeLock.acquire(AUTO_EXIT_MS + 60_000);
        }
        lastPressAt = SystemClock.uptimeMillis();
        runtime.handler.postDelayed(watch, WATCH_MS);
        runtime.coach.setEngaged("pocket", true);
        HidService.sync(runtime.app);
        runtime.notifyChanged();
        return true;
    }

    void stop() {
        if (session == null) return;
        try {
            session.setActive(false);
            session.release();
        } catch (RuntimeException ignored) {
        }
        session = null;
        keys.cancel();
        runtime.handler.removeCallbacks(watch);
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        wakeLock = null;
        runtime.coach.setEngaged("pocket", false);
        HidService.sync(runtime.app);
        runtime.notifyChanged();
    }

    private void onVolume(int direction) {
        if (session == null) return;
        long now = SystemClock.uptimeMillis();
        lastPressAt = now;
        if (wakeLock != null) wakeLock.acquire(AUTO_EXIT_MS + 60_000);
        if (direction > 0) keys.press(true, now);
        else if (direction < 0) keys.press(false, now);
        else keys.releaseLast(now);
    }

    private void buzz(AnkiAction action) {
        long[] pattern;
        switch (action) {
            case FLIP: pattern = new long[]{0, 22}; break;
            case GOOD: pattern = new long[]{0, 22, 60, 22}; break;
            case AGAIN: pattern = new long[]{0, 90}; break;
            case UNDO: pattern = new long[]{0, 15, 45, 15, 45, 15}; break;
            default: pattern = new long[]{0, 45, 60, 45}; break;
        }
        try {
            if (vibrator != null && vibrator.hasVibrator())
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
        } catch (RuntimeException ignored) {
        }
    }
}
