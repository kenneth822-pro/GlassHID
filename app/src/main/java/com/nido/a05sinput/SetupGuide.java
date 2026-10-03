package com.nido.a05sinput;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** First-run walkthrough: permissions, choosing a host, pairing, and the Anki remote. */
final class SetupGuide {
    interface Host {
        boolean bluetoothPermissionGranted();

        boolean notificationsGranted();

        void requestPermissions();

        void makeVisible();

        void openBluetoothSettings();

        void openAnkiRemote();

        String connectionStatus();

        boolean connected();

        void onGuideFinished();
    }

    private static final int STEP_WELCOME = 0;
    private static final int STEP_PERMISSIONS = 1;
    private static final int STEP_HOST = 2;
    private static final int STEP_PAIR = 3;
    private static final int STEP_ANKI = 4;
    private static final int HOST_WINDOWS = 0;
    private static final int HOST_TABLET = 1;
    private static final int HOST_OTHER = 2;

    private final Activity activity;
    private final NeoUi neo;
    private final Host host;
    private Dialog dialog;
    private LinearLayout content;
    private TextView liveStatus;
    private int step;
    private int hostType = HOST_WINDOWS;

    SetupGuide(Activity activity, NeoUi neo, Host host) {
        this.activity = activity;
        this.neo = neo;
        this.host = host;
    }

    boolean isShowing() {
        return dialog != null && dialog.isShowing();
    }

    void show() {
        if (isShowing()) return;
        step = STEP_WELCOME;
        dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(neo.dp(16), neo.dp(14), neo.dp(16), neo.dp(12));
        content.setBackground(neo.background(Palette.YELLOW));
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(content);
        dialog.setContentView(scroll);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int width = Math.min(activity.getResources().getDisplayMetrics().widthPixels - neo.dp(32), neo.dp(560));
            window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        dialog.setOnDismissListener(d -> {
            liveStatus = null;
            dialog = null;
        });
        render();
        dialog.show();
    }

    /** Bluetooth or permission state changed while the guide is open. */
    void refresh() {
        if (!isShowing()) return;
        if (step == STEP_PERMISSIONS) render();
        else if (liveStatus != null) updateLiveStatus();
    }

    void dismiss() {
        if (dialog != null) dialog.dismiss();
    }

    private void render() {
        content.removeAllViews();
        liveStatus = null;
        switch (step) {
            case STEP_WELCOME:
                title("Welcome to GlassHID");
                body("This phone becomes a Bluetooth keyboard, mouse, gamepad, and Anki remote for "
                        + "your PC or tablet. Nothing to install on the other device, and no Wi-Fi.");
                buttons(button("START", Palette.GREEN, () -> go(STEP_PERMISSIONS)),
                        button("SKIP", Palette.PAPER, this::finish));
                break;
            case STEP_PERMISSIONS:
                title("1 · Permissions");
                body((host.bluetoothPermissionGranted() ? "✓ " : "• ") + "Nearby devices — needed for Bluetooth input.\n"
                        + (host.notificationsGranted() ? "✓ " : "• ") + "Notifications — shows the "
                        + "connection while GlassHID runs in the background and in pocket mode.");
                buttons(button("ALLOW", Palette.YELLOW, host::requestPermissions),
                        button("NEXT", Palette.GREEN, () -> go(STEP_HOST)));
                break;
            case STEP_HOST:
                title("2 · What will you control?");
                body("Pick the device that runs Anki (or that you want to type on).");
                stacked(button("WINDOWS PC", Palette.BLUE, () -> pickHost(HOST_WINDOWS)),
                        button("ANDROID TABLET OR PHONE (ANKIDROID)", Palette.GREEN, () -> pickHost(HOST_TABLET)),
                        button("MAC, LINUX, OR CHROMEBOOK", Palette.PAPER, () -> pickHost(HOST_OTHER)));
                break;
            case STEP_PAIR:
                title("3 · Pair");
                body(pairingSteps());
                liveStatus = neo.text("", 13);
                liveStatus.setGravity(Gravity.CENTER);
                liveStatus.setTextColor(Palette.INK);
                content.addView(liveStatus, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, neo.dp(56)));
                updateLiveStatus();
                buttons(button("MAKE PHONE VISIBLE", Palette.YELLOW, host::makeVisible),
                        button("BT SETTINGS", Palette.PAPER, host::openBluetoothSettings));
                buttons(button("BACK", Palette.PAPER, () -> go(STEP_HOST)),
                        button("NEXT", Palette.GREEN, () -> go(STEP_ANKI)));
                break;
            default:
                title("4 · Review Anki");
                body("Tap ANKI on the top bar. Volume Down shows the answer, then grades Good; "
                        + "Volume Up grades Again; holding a volume key runs a shortcut (Undo by default).\n\n"
                        + "SET ▾ picks the target (Anki desktop or AnkiDroid), layout (center, split, "
                        + "swipe), hand, and study tools. POCKET lets you keep reviewing with the screen off.");
                buttons(button("OPEN ANKI REMOTE", Palette.CORAL, () -> {
                            finish();
                            host.openAnkiRemote();
                        }),
                        button("FINISH", Palette.GREEN, this::finish));
                break;
        }
    }

    private String pairingSteps() {
        switch (hostType) {
            case HOST_TABLET:
                return "1. Tap MAKE PHONE VISIBLE.\n"
                        + "2. On the tablet: Settings → Connected devices → Pair new device, pick this phone, "
                        + "and confirm the code on both screens.\n"
                        + "3. If input does not connect, open the phone's entry in the tablet's Bluetooth "
                        + "settings and switch on Input device.";
            case HOST_OTHER:
                return "1. Tap MAKE PHONE VISIBLE.\n"
                        + "2. On the computer, open Bluetooth settings, pair this phone, and confirm the code.\n"
                        + "3. Back here, the status below turns green once input is connected.";
            default:
                return "1. Tap MAKE PHONE VISIBLE.\n"
                        + "2. On the PC: Settings → Bluetooth & devices → Add device → Bluetooth, pick this "
                        + "phone, and confirm the code on both screens.\n"
                        + "3. The status below turns green once keyboard and mouse input is connected.";
        }
    }

    private void updateLiveStatus() {
        if (liveStatus == null) return;
        liveStatus.setText(host.connectionStatus());
        liveStatus.setBackground(neo.rounded(host.connected() ? Palette.GREEN : Palette.PAPER));
    }

    private void pickHost(int type) {
        hostType = type;
        go(STEP_PAIR);
    }

    private void go(int next) {
        step = next;
        render();
    }

    private void finish() {
        host.onGuideFinished();
        dismiss();
    }

    private void title(String text) {
        TextView view = neo.text(text, 18);
        view.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        view.setTextColor(Palette.INK);
        content.addView(view, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    private void body(String text) {
        TextView view = neo.text(text, 14);
        view.setTextColor(Palette.INK);
        view.setLineSpacing(neo.dp(2), 1f);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, neo.dp(8), 0, neo.dp(10));
        content.addView(view, params);
    }

    private Button button(String label, int color, Runnable action) {
        Button button = neo.button(label, color);
        button.setOnClickListener(v -> action.run());
        return button;
    }

    private void buttons(Button... buttons) {
        LinearLayout row = new LinearLayout(activity);
        for (Button button : buttons)
            row.addView(button, new LinearLayout.LayoutParams(0, neo.dp(50), 1));
        content.addView(row, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, neo.dp(54)));
    }

    private void stacked(Button... buttons) {
        for (Button button : buttons)
            content.addView(button, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, neo.dp(52)));
    }
}
