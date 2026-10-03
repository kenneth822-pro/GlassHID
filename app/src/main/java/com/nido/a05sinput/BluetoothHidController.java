package com.nido.a05sinput;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHidDevice;
import android.bluetooth.BluetoothHidDeviceAppSdpSettings;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;

import java.util.Collections;
import java.util.Set;

/** Owns Bluetooth HID registration, connection recovery, and report delivery. */
final class BluetoothHidController {
    interface Listener {
        void onStateChanged();
        void onInputConnected(String hostName);
    }

    private static final String TAG = "A05sInput";
    private static final byte[] HID_DESCRIPTOR = hex(
            "05010906A1018501050719E029E715002501750195088102" +
            "95017508810195067508150025650507190029658100C0" +
            "05010902A10185020901A100050919012905150025019505" +
            "7501810295017503810105010930093109381581257F7508" +
            "95038106C0C0" +
            "050C0901A1018503150026FF0319002AFF03751095018100C0" +
            // Gamepad, report 4: four signed axes, 16 buttons, and an 8-way hat.
            "05010905A1018504" +
            "09300931093209351581257F750895048102" +
            "05091901291015002501750195108102" +
            "05010939150025073500463B016514750495018142" +
            "6500750495018101C0");
    /** A connect that never leaves CONNECTING blocks every later request. */
    private static final long CONNECT_STUCK_MS = 10000;
    private static final long REREGISTER_MIN_INTERVAL_MS = 30000;
    /** Lets the stack drop the old host's link before the new host is paged. */
    private static final long SWITCH_SETTLE_MS = 900;
    /** Gives a host that just paired a moment to open the HID link itself. */
    private static final long NEW_HOST_SETTLE_MS = 2500;
    /** A device paired this soon after MAKE PHONE VISIBLE becomes the host. */
    private static final long PAIRING_WINDOW_MS = 5 * 60 * 1000;
    /** Bluetooth assigned number for Computer: Tablet; the SDK has no constant for it. */
    private static final int COMPUTER_TABLET = 0x011C;

    private final Activity activity;
    private final Handler handler;
    private final Listener listener;
    private final BluetoothAdapter adapter;

    private BluetoothHidDevice hid;
    private BluetoothDevice connectedHost;
    private boolean registered;
    private boolean binding;
    private boolean registrationPending;
    private boolean recoveryScheduled;
    private boolean foreground;
    private boolean active;
    private boolean destroyed;
    private int connectionState = BluetoothProfile.STATE_DISCONNECTED;
    private long lastConnectRequestAt;
    private long connectingSince;
    private int stuckConnects;
    private long lastReregisterAt;
    private int failedConnects;
    private BluetoothDevice pendingHost;
    private long expectNewHostUntil;
    private boolean bondReceiverRegistered;
    private long nextReportAt;

    private final BroadcastReceiver bondReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (!BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(intent.getAction())) return;
            notifyChanged();
            BluetoothDevice device = deviceExtra(intent);
            int state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE);
            if (state != BluetoothDevice.BOND_BONDED || device == null || destroyed || !active ||
                    SystemClock.uptimeMillis() > expectNewHostUntil || !looksLikeHost(device)) return;
            // Pairing began from MAKE PHONE VISIBLE: switch to the new computer or tablet
            // instead of paging the previous host, which would block its connection.
            expectNewHostUntil = 0;
            pendingHost = device;
            handler.postDelayed(BluetoothHidController.this::ensureReady, NEW_HOST_SETTLE_MS);
        }
    };

    BluetoothHidController(Activity activity, Handler handler, Listener listener) {
        this.activity = activity;
        this.handler = handler;
        this.listener = listener;
        this.adapter = BluetoothAdapter.getDefaultAdapter();
    }

    boolean hasPermission() {
        return Build.VERSION.SDK_INT < 31 || activity.checkSelfPermission(
                Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }

    void setForeground(boolean foreground) {
        this.foreground = foreground;
        if (foreground) ensureReady();
    }

    void setActive(boolean active) {
        this.active = active;
        if (active) ensureReady();
        notifyChanged();
    }

    void bind() {
        if (destroyed || binding || hid != null || adapter == null || !hasPermission()) {
            notifyChanged();
            return;
        }
        binding = true;
        registerBondReceiver();
        boolean requested = adapter.getProfileProxy(activity,
                new BluetoothProfile.ServiceListener() {
                    @Override public void onServiceConnected(int profile, BluetoothProfile proxy) {
                        binding = false;
                        hid = (BluetoothHidDevice) proxy;
                        ensureReady();
                    }

                    @Override public void onServiceDisconnected(int profile) {
                        binding = false;
                        hid = null;
                        registered = false;
                        registrationPending = false;
                        connectedHost = null;
                        connectionState = BluetoothProfile.STATE_DISCONNECTED;
                        Log.w(TAG, "Bluetooth HID profile disconnected");
                        notifyChanged();
                        scheduleRecovery();
                    }
                }, BluetoothProfile.HID_DEVICE);
        if (!requested) {
            binding = false;
            Log.w(TAG, "Bluetooth HID profile bind was rejected");
            scheduleRecovery();
        }
    }

    void connect(BluetoothDevice device) {
        active = true;
        if (switchHost(device, true)) handler.postDelayed(this::ensureReady, SWITCH_SETTLE_MS);
        else ensureReady();
    }

    /** Opens a short window in which a newly paired computer or tablet becomes the host. */
    void expectNewHost() {
        expectNewHostUntil = SystemClock.uptimeMillis() + PAIRING_WINDOW_MS;
    }

    /** The live host, or the remembered one while reconnecting. */
    BluetoothDevice currentHost() {
        return isInputLive() ? connectedHost : preferredHost();
    }

    /** True for hosts that look like a phone or tablet (AnkiDroid) rather than a computer. */
    boolean isMobileHost(BluetoothDevice device) {
        BluetoothClass type = deviceClass(device);
        if (type == null) return false;
        if (type.getMajorDeviceClass() == BluetoothClass.Device.Major.PHONE) return true;
        int kind = type.getDeviceClass();
        return kind == BluetoothClass.Device.COMPUTER_HANDHELD_PC_PDA ||
                kind == BluetoothClass.Device.COMPUTER_PALM_SIZE_PC_PDA ||
                kind == BluetoothClass.Device.COMPUTER_WEARABLE ||
                kind == COMPUTER_TABLET;
    }

    /** Computers, phones, and tablets can be HID hosts; earbuds, watches, and keyboards cannot. */
    boolean looksLikeHost(BluetoothDevice device) {
        BluetoothClass type = deviceClass(device);
        if (type == null) return false;
        int major = type.getMajorDeviceClass();
        return major == BluetoothClass.Device.Major.COMPUTER ||
                major == BluetoothClass.Device.Major.PHONE;
    }

    /**
     * Makes {@code device} the host and drops links or pending requests to any other one:
     * the phone serves one host at a time, and paging the old host blocks the new one.
     *
     * @param manual a user tap also clears a half-open request to {@code device} itself
     * @return true when a link was dropped and the stack needs a moment to settle
     */
    private boolean switchHost(BluetoothDevice device, boolean manual) {
        BluetoothDevice previous = preferredHost();
        rememberHost(device);
        failedConnects = 0;
        lastConnectRequestAt = 0;
        connectingSince = 0;
        if (hid == null || !registered || isConnectedTo(device)) return false;
        boolean dropped = false;
        if (connectedHost != null && !connectedHost.equals(device)) {
            hid.disconnect(connectedHost);
            dropped = true;
        }
        if (previous != null && !previous.equals(device) && !previous.equals(connectedHost)) {
            int state = hid.getConnectionState(previous);
            if (state == BluetoothProfile.STATE_CONNECTING ||
                    state == BluetoothProfile.STATE_CONNECTED) {
                hid.disconnect(previous);
                dropped = true;
            }
        }
        if (manual && hid.getConnectionState(device) == BluetoothProfile.STATE_CONNECTING) {
            hid.disconnect(device);
            dropped = true;
        }
        connectedHost = null;
        connectionState = BluetoothProfile.STATE_DISCONNECTED;
        return dropped;
    }

    Set<BluetoothDevice> bondedDevices() {
        if (!hasPermission() || adapter == null) return Collections.emptySet();
        return adapter.getBondedDevices();
    }

    boolean isConnectedTo(BluetoothDevice device) {
        return connectedHost != null && connectedHost.equals(device) && isInputLive();
    }

    boolean isInputLive() {
        return registered && connectedHost != null &&
                connectionState == BluetoothProfile.STATE_CONNECTED;
    }

    String shortStatus() {
        if (!hasPermission()) return "PERMISSION NEEDED";
        if (isInputLive()) return "INPUT CONNECTED ✓";
        if (preferredHost() == null) return "NOT PAIRED";
        if (!registered) return "PAIRED · STARTING HID";
        if (connectionState == BluetoothProfile.STATE_CONNECTING)
            return "PAIRED · CONNECTING";
        return "PAIRED · INPUT OFFLINE";
    }

    String detailedStatus() {
        if (!hasPermission()) return "NEARBY DEVICES PERMISSION NEEDED";
        if (isInputLive()) return "INPUT CONNECTED ✓\n" + safeName(connectedHost);
        if (preferredHost() == null) return "NOT PAIRED\nPair from your PC or tablet";
        if (!registered) return "PAIRED · STARTING INPUT SERVICE…";
        if (connectionState == BluetoothProfile.STATE_CONNECTING)
            return "CONNECTING INPUT…\n" + safeName(preferredHost());
        return "PAIRED · INPUT OFFLINE\nTap a device below to connect";
    }

    String safeName(BluetoothDevice device) {
        if (device == null || !hasPermission()) return "paired host";
        String name = device.getName();
        return name == null ? device.getAddress() : name;
    }

    void sendKey(int modifier, int usage) {
        if (!isInputLive()) return;
        BluetoothHidDevice targetHid = hid;
        BluetoothDevice targetHost = connectedHost;
        byte[] modifiersDown = new byte[]{(byte) modifier, 0, 0, 0, 0, 0, 0, 0};
        byte[] chordDown = new byte[]{(byte) modifier, 0, (byte) usage, 0, 0, 0, 0, 0};
        byte[] allUp = new byte[8];
        long start = reserveReportWindow(modifier != 0 && usage != 0 ? 190 : 130);
        Log.d(TAG, "BT key modifier=0x" + Integer.toHexString(modifier) +
                " usage=0x" + Integer.toHexString(usage));
        if (modifier != 0 && usage != 0) {
            postReport(targetHid, targetHost, 1, modifiersDown, start);
            postReport(targetHid, targetHost, 1, chordDown, start + 45);
            postReport(targetHid, targetHost, 1, modifiersDown, start + 115);
            postReport(targetHid, targetHost, 1, allUp, start + 160);
        } else {
            postReport(targetHid, targetHost, 1, chordDown, start);
            postReport(targetHid, targetHost, 1, allUp, start + 85);
        }
    }

    void sendConsumer(int usage) {
        if (!isInputLive()) return;
        BluetoothHidDevice targetHid = hid;
        BluetoothDevice targetHost = connectedHost;
        byte[] down = new byte[]{(byte) (usage & 0xFF), (byte) ((usage >> 8) & 0xFF)};
        long start = reserveReportWindow(130);
        Log.d(TAG, "BT consumer usage=0x" + Integer.toHexString(usage));
        postReport(targetHid, targetHost, 3, down, start);
        postReport(targetHid, targetHost, 3, new byte[2], start + 85);
    }

    void sendMouse(int buttons, int dx, int dy, int wheel) {
        if (!isInputLive()) return;
        BluetoothHidDevice targetHid = hid;
        BluetoothDevice targetHost = connectedHost;
        boolean empty = dx == 0 && dy == 0 && wheel == 0;
        while (dx != 0 || dy != 0 || wheel != 0) {
            int x = clamp(dx), y = clamp(dy), w = clamp(wheel);
            sendReport(targetHid, targetHost, 2,
                    new byte[]{(byte) buttons, (byte) x, (byte) y, (byte) w});
            dx -= x;
            dy -= y;
            wheel -= w;
        }
        if (empty) sendReport(targetHid, targetHost, 2,
                new byte[]{(byte) buttons, 0, 0, 0});
    }

    void sendGamepad(int buttons, int leftX, int leftY,
                     int rightX, int rightY, int hat) {
        if (!isInputLive()) return;
        sendReport(hid, connectedHost, 4, new byte[]{
                (byte) clamp(leftX), (byte) clamp(leftY),
                (byte) clamp(rightX), (byte) clamp(rightY),
                (byte) (buttons & 0xFF), (byte) ((buttons >> 8) & 0xFF),
                (byte) (hat & 0x0F)
        });
    }

    void destroy() {
        destroyed = true;
        if (bondReceiverRegistered) {
            try {
                activity.unregisterReceiver(bondReceiver);
            } catch (IllegalArgumentException ignored) {
                // Already unregistered.
            }
            bondReceiverRegistered = false;
        }
        if (hid != null && (registered || registrationPending)) hid.unregisterApp();
        if (adapter != null && hid != null)
            adapter.closeProfileProxy(BluetoothProfile.HID_DEVICE, hid);
    }

    private void ensureReady() {
        if (destroyed || !foreground || !active || !hasPermission()) return;
        if (hid == null) bind();
        else if (!registered) registerApp();
        else if (pendingHost != null) adoptPendingHost();
        else connectPreferredHost();
    }

    private void adoptPendingHost() {
        BluetoothDevice host = pendingHost;
        pendingHost = null;
        Log.d(TAG, "Using newly paired host " + safeName(host));
        if (switchHost(host, false)) handler.postDelayed(this::ensureReady, SWITCH_SETTLE_MS);
        else connectPreferredHost();
    }

    private void registerApp() {
        if (destroyed || !foreground || !active || hid == null || registered ||
                registrationPending) return;
        registrationPending = true;
        BluetoothHidDeviceAppSdpSettings sdp = new BluetoothHidDeviceAppSdpSettings(
                "GlassHID", "Offline keyboard, mouse, and gamepad", "Local", (byte) 0xC0,
                HID_DESCRIPTOR);
        boolean requested = hid.registerApp(sdp, null, null, activity.getMainExecutor(),
                new BluetoothHidDevice.Callback() {
                    @Override public void onAppStatusChanged(BluetoothDevice plugged,
                                                              boolean isRegistered) {
                        registrationPending = false;
                        registered = isRegistered;
                        Log.d(TAG, "Bluetooth HID app registered=" + isRegistered);
                        if (isRegistered) {
                            // Keep the host the user chose unless the stack reports a live link.
                            boolean pluggedLive = plugged != null && hid != null &&
                                    hid.getConnectionState(plugged) == BluetoothProfile.STATE_CONNECTED;
                            if (plugged != null && (pluggedLive || preferredHost() == null))
                                rememberHost(plugged);
                            BluetoothDevice candidate = pluggedLive ? plugged : preferredHost();
                            connectionState = candidate == null || hid == null
                                    ? BluetoothProfile.STATE_DISCONNECTED
                                    : hid.getConnectionState(candidate);
                            connectedHost = connectionState == BluetoothProfile.STATE_CONNECTED
                                    ? candidate : null;
                            connectPreferredHost();
                        } else {
                            connectedHost = null;
                            connectionState = BluetoothProfile.STATE_DISCONNECTED;
                            scheduleRecovery();
                        }
                        notifyChanged();
                    }

                    @Override public void onConnectionStateChanged(BluetoothDevice device,
                                                                    int state) {
                        Log.d(TAG, "Bluetooth HID state=" + state + " host=" + safeName(device));
                        boolean wasConnected = isInputLive();
                        if (state != BluetoothProfile.STATE_CONNECTED &&
                                !device.equals(connectedHost) && !device.equals(preferredHost())) {
                            // A host we already switched away from is still winding down.
                            notifyChanged();
                            return;
                        }
                        connectionState = state;
                        if (state != BluetoothProfile.STATE_CONNECTING) connectingSince = 0;
                        if (state == BluetoothProfile.STATE_CONNECTED) {
                            connectedHost = device;
                            rememberHost(device);
                            recoveryScheduled = false;
                            stuckConnects = 0;
                            failedConnects = 0;
                            if (!wasConnected) listener.onInputConnected(safeName(device));
                        } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                            if (device.equals(connectedHost)) connectedHost = null;
                            else if (!wasConnected) failedConnects++;
                            scheduleRecovery();
                        }
                        notifyChanged();
                    }
                });
        if (!requested) {
            registrationPending = false;
            Log.w(TAG, "Bluetooth HID app registration was rejected");
            // A registration left behind by an earlier instance, or one whose
            // callback was lost, rejects every new request. Clear ours and retry.
            hid.unregisterApp();
            scheduleRecovery();
        } else {
            handler.postDelayed(() -> {
                if (registrationPending) {
                    registrationPending = false;
                    Log.w(TAG, "Bluetooth HID registration timed out");
                    scheduleRecovery();
                }
            }, 4000);
        }
    }

    private void connectPreferredHost() {
        if (destroyed || !foreground || !active || hid == null || !registered ||
                connectedHost != null) return;
        BluetoothDevice preferred = preferredHost();
        if (preferred == null) {
            notifyChanged();
            return;
        }
        connectionState = hid.getConnectionState(preferred);
        if (connectionState == BluetoothProfile.STATE_CONNECTED) {
            connectedHost = preferred;
            connectingSince = 0;
            notifyChanged();
            return;
        }
        long now = SystemClock.uptimeMillis();
        if (connectionState == BluetoothProfile.STATE_CONNECTING ||
                connectionState == BluetoothProfile.STATE_DISCONNECTING) {
            if (connectingSince == 0) connectingSince = now;
            else if (now - connectingSince >= CONNECT_STUCK_MS) recoverStuckConnect(preferred);
            notifyChanged();
            scheduleRecovery();
            return;
        }
        connectingSince = 0;
        if (now - lastConnectRequestAt < connectRetryMs()) {
            notifyChanged();
            scheduleRecovery();
            return;
        }
        lastConnectRequestAt = now;
        Log.d(TAG, "Connecting preferred HID host " + safeName(preferred));
        if (hid.connect(preferred)) {
            connectionState = BluetoothProfile.STATE_CONNECTING;
            connectingSince = now;
        } else {
            failedConnects++;
            Log.w(TAG, "Bluetooth HID connect request was rejected");
        }
        notifyChanged();
        scheduleRecovery();
    }

    /**
     * Retries slow down while the host is away, leaving gaps in which another computer
     * or tablet can open its own connection to the phone.
     */
    private long connectRetryMs() {
        return failedConnects < 3 ? 3500 : failedConnects < 6 ? 6000 : 10000;
    }

    private BluetoothClass deviceClass(BluetoothDevice device) {
        if (device == null || !hasPermission()) return null;
        try {
            return device.getBluetoothClass();
        } catch (SecurityException e) {
            return null;
        }
    }

    private void registerBondReceiver() {
        if (bondReceiverRegistered) return;
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        if (Build.VERSION.SDK_INT >= 33) {
            activity.registerReceiver(bondReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            activity.registerReceiver(bondReceiver, filter);
        }
        bondReceiverRegistered = true;
    }

    @SuppressWarnings("deprecation")
    private static BluetoothDevice deviceExtra(Intent intent) {
        if (Build.VERSION.SDK_INT >= 33)
            return intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice.class);
        return intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
    }

    /** Drops a link stuck in CONNECTING; if that keeps happening, re-registers HID. */
    private void recoverStuckConnect(BluetoothDevice host) {
        Log.w(TAG, "Bluetooth HID connect to " + safeName(host) + " is stuck; resetting");
        connectingSince = 0;
        lastConnectRequestAt = 0;
        stuckConnects++;
        hid.disconnect(host);
        connectionState = BluetoothProfile.STATE_DISCONNECTED;
        long now = SystemClock.uptimeMillis();
        if (stuckConnects >= 2 && now - lastReregisterAt >= REREGISTER_MIN_INTERVAL_MS) {
            stuckConnects = 0;
            lastReregisterAt = now;
            Log.w(TAG, "Re-registering Bluetooth HID app");
            registered = false;
            registrationPending = false;
            connectedHost = null;
            hid.unregisterApp();
        }
    }

    private BluetoothDevice preferredHost() {
        if (!hasPermission() || adapter == null) return null;
        String address = activity.getSharedPreferences("controls", Context.MODE_PRIVATE)
                .getString("last_hid_host", null);
        if (address == null) return null;
        try {
            BluetoothDevice device = adapter.getRemoteDevice(address);
            return adapter.getBondedDevices().contains(device) ? device : null;
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "Saved Bluetooth host address is invalid", e);
            return null;
        }
    }

    private void rememberHost(BluetoothDevice device) {
        if (!hasPermission() || device == null) return;
        activity.getSharedPreferences("controls", Context.MODE_PRIVATE).edit()
                .putString("last_hid_host", device.getAddress()).apply();
    }

    private void scheduleRecovery() {
        if (destroyed || !foreground || !active || recoveryScheduled || isInputLive()) return;
        recoveryScheduled = true;
        handler.postDelayed(() -> {
            recoveryScheduled = false;
            ensureReady();
            if (!isInputLive()) scheduleRecovery();
        }, 3500);
    }

    private long reserveReportWindow(int durationMs) {
        synchronized (this) {
            long start = Math.max(SystemClock.uptimeMillis(), nextReportAt);
            nextReportAt = start + durationMs;
            return start;
        }
    }

    private void postReport(BluetoothHidDevice targetHid, BluetoothDevice targetHost,
                            int id, byte[] value, long at) {
        handler.postAtTime(() -> sendReport(targetHid, targetHost, id, value), at);
    }

    private void sendReport(BluetoothHidDevice targetHid, BluetoothDevice targetHost,
                            int id, byte[] value) {
        if (!targetHid.sendReport(targetHost, id, value)) {
            Log.w(TAG, "Bluetooth rejected HID report " + id);
            if (targetHost.equals(connectedHost)) connectedHost = null;
            connectionState = BluetoothProfile.STATE_DISCONNECTED;
            targetHid.disconnect(targetHost);
            notifyChanged();
            scheduleRecovery();
        }
    }

    private String compactName(BluetoothDevice device) {
        return safeName(device).replace("DESKTOP-", "");
    }

    private void notifyChanged() {
        activity.runOnUiThread(listener::onStateChanged);
    }

    private static int clamp(int value) {
        return Math.max(-127, Math.min(127, value));
    }

    private static byte[] hex(String value) {
        byte[] out = new byte[value.length() / 2];
        for (int i = 0; i < out.length; i++)
            out[i] = (byte) Integer.parseInt(value.substring(i * 2, i * 2 + 2), 16);
        return out;
    }
}
