package com.nido.a05sinput;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;

/**
 * Keeps Bluetooth input alive while GlassHID is in the background or the screen is off, and
 * hosts pocket mode. It runs only in Bluetooth mode with the Nearby devices permission, and
 * stops itself after {@link #IDLE_STOP_MS} in the background with no host connected.
 */
public final class HidService extends Service
        implements BluetoothHidController.Listener, GlassHid.Listener {
    static final String ACTION_DISCONNECT = "com.nido.a05sinput.action.DISCONNECT";
    static final String ACTION_STOP_POCKET = "com.nido.a05sinput.action.STOP_POCKET";
    private static final String CHANNEL = "glasshid_input";
    private static final int NOTIFICATION_ID = 7;
    private static final long IDLE_STOP_MS = 10 * 60_000;
    private static final String TAG = "A05sInput";

    private static volatile boolean running;
    /** Set when the service stopped itself for being idle; cleared when the app is opened. */
    private static volatile boolean idleStopped;

    private GlassHid runtime;
    private long idleSince;
    private String lastText = "";

    private final Runnable idleCheck = new Runnable() {
        @Override public void run() {
            if (!running) return;
            checkIdle();
            runtime.handler.postDelayed(this, 30_000);
        }
    };

    static boolean isRunning() {
        return running;
    }

    static void resetForTests() {
        running = false;
        idleStopped = false;
    }

    /** The app came to the foreground: background input may start again. */
    static void clearIdleStop() {
        idleStopped = false;
    }

    /** Starts or stops the service to match the current mode and settings. */
    static void sync(Context context) {
        GlassHid runtime = GlassHid.get(context);
        boolean want = runtime.mode() == GlassHid.MODE_BLUETOOTH &&
                runtime.bluetooth().hasPermission() &&
                (runtime.pocket.isActive() || (runtime.backgroundEnabled() && !idleStopped));
        Intent intent = new Intent(context, HidService.class);
        try {
            if (want && !running) {
                context.startForegroundService(intent);
            } else if (!want && running) {
                context.stopService(intent);
            }
        } catch (RuntimeException e) {
            // Android refuses to start foreground services from the background; the next
            // time the app is opened it tries again.
            Log.w(TAG, "Could not change the input service", e);
        }
    }

    @Override public void onCreate() {
        super.onCreate();
        running = true;
        runtime = GlassHid.get(this);
        createChannel();
        if (!enterForeground()) return;
        runtime.setServiceRunning(true);
        runtime.bluetooth().addListener(this);
        runtime.addListener(this);
        idleSince = SystemClock.uptimeMillis();
        runtime.handler.postDelayed(idleCheck, 30_000);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (!running) return START_NOT_STICKY;
        String action = intent == null ? null : intent.getAction();
        if (ACTION_STOP_POCKET.equals(action)) {
            runtime.pocket.stop();
        } else if (ACTION_DISCONNECT.equals(action)) {
            runtime.pocket.stop();
            runtime.setMode(GlassHid.MODE_OFF);
            stopSelf();
            return START_NOT_STICKY;
        }
        enterForeground();
        return START_NOT_STICKY;
    }

    @Override public void onDestroy() {
        running = false;
        if (runtime != null) {
            runtime.handler.removeCallbacks(idleCheck);
            runtime.bluetooth().removeListener(this);
            runtime.removeListener(this);
            runtime.pocket.stop();
            runtime.setServiceRunning(false);
            runtime.releaseBluetooth();
        }
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) {
        return null;
    }

    @Override public void onStateChanged() {
        refreshNotification();
    }

    @Override public void onInputConnected(String hostName) {
        refreshNotification();
    }

    @Override public void onRuntimeChanged() {
        refreshNotification();
    }

    private void checkIdle() {
        boolean idle = !runtime.isActivityVisible() && !runtime.pocket.isActive() &&
                !runtime.bluetooth().isInputLive();
        long now = SystemClock.uptimeMillis();
        if (!idle) {
            idleSince = now;
        } else if (now - idleSince > IDLE_STOP_MS) {
            Log.d(TAG, "No host for 10 minutes in the background; stopping input service");
            idleStopped = true;
            stopSelf();
        }
    }

    private boolean enterForeground() {
        try {
            Notification notification = buildNotification();
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIFICATION_ID, notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
            return true;
        } catch (RuntimeException e) {
            // Missing Bluetooth permission or a platform refusal: run without the service.
            Log.w(TAG, "Input service could not enter the foreground", e);
            running = false;
            stopSelf();
            return false;
        }
    }

    private void refreshNotification() {
        if (!running) return;
        String text = statusText();
        if (text.equals(lastText)) return;
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager == null) return;
        try {
            manager.notify(NOTIFICATION_ID, buildNotification());
        } catch (RuntimeException e) {
            Log.w(TAG, "Could not update the input notification", e);
        }
    }

    private String statusText() {
        String text = runtime.bluetooth().isInputLive()
                ? "Connected to " + runtime.bluetooth().safeName(runtime.bluetooth().currentHost())
                : "Waiting for your computer or tablet…";
        if (runtime.pocket.isActive()) text = "Pocket mode · volume keys control Anki · " + text;
        return text;
    }

    private Notification buildNotification() {
        lastText = statusText();
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder builder = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setContentTitle(runtime.pocket.isActive() ? "GlassHID pocket mode" : "GlassHID Bluetooth input")
                .setContentText(lastText)
                .setStyle(new Notification.BigTextStyle().bigText(lastText))
                .setContentIntent(open)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_SERVICE);
        if (runtime.pocket.isActive()) builder.addAction(action(ACTION_STOP_POCKET, "Exit pocket mode", 1));
        builder.addAction(action(ACTION_DISCONNECT, "Disconnect", 2));
        return builder.build();
    }

    private Notification.Action action(String name, String title, int requestCode) {
        PendingIntent intent = PendingIntent.getService(this, requestCode,
                new Intent(this, HidService.class).setAction(name),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Action.Builder(
                Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
                title, intent).build();
    }

    private void createChannel() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager == null) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL, "Bluetooth input",
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Keeps GlassHID connected while it is in the background.");
        channel.setShowBadge(false);
        manager.createNotificationChannel(channel);
    }
}
