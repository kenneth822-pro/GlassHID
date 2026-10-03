package com.nido.a05sinput;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Base64;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MainActivity extends Activity implements TrackpadGestureListener.Host,
        ScrollPadListener.Host {
    private static final int REQUEST_BT = 100;
    private static final int MODE_OFF = 0;
    private static final int MODE_BLUETOOTH = 1;
    private static final int MODE_USB = 2;
    private static final int INK = Color.rgb(24, 24, 24);
    private static final int PAPER = Color.rgb(247, 243, 234);
    private static final int GREEN = Color.rgb(139, 214, 170);
    private static final int YELLOW = Color.rgb(250, 204, 80);
    private static final int CORAL = Color.rgb(255, 126, 103);
    private static final int BLUE = Color.rgb(139, 188, 255);

    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private TextView status;
    private TextView systemStatsView;
    private TextView bluetoothStateView;
    private RadioButton bluetoothModeButton;
    private Button layoutSwitch;
    private LinearLayout normalTopBar;
    private LinearLayout controllerTopBar;
    private LinearLayout inputContainer;
    private LinearLayout pairedDevices;
    private volatile int mode = MODE_OFF;
    private volatile String pcStats = "LAPTOP BAT —";

    private BluetoothHidController bluetooth;
    private UsbBridgeServer usb;
    private FeedbackController feedback;
    private NeoUi neoUi;
    private ControllerPanel controllerPanel;
    private boolean shiftOn;
    private boolean capsOn;
    private boolean ctrlOn;
    private boolean altOn;
    private boolean winOn;
    private int mouseButtons;
    private boolean trackpadOnRight;
    private PopupWindow trackpadPopup;
    private PopupWindow toolsPopup;
    private PopupWindow systemPopup;
    private PopupWindow settingsPopup;
    private final Map<String, TextView> settingValueViews = new HashMap<>();
    private int dragHoldMs = 500;
    private int pointerPercent = 100;
    private int scrollPercent = 100;
    private int repeatMs = 55;
    private boolean hapticsOn = true;
    private boolean laptopClicksOn = true;
    private boolean controllerLayout;
    private boolean swapGamepadControls;
    private int gamepadLabelStyle;
    private final List<Button> shiftButtons = new ArrayList<>();
    private final List<Button> capsButtons = new ArrayList<>();
    private final List<Button> ctrlButtons = new ArrayList<>();
    private final List<Button> altButtons = new ArrayList<>();
    private final List<Button> winButtons = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        bluetooth = new BluetoothHidController(this, uiHandler,
                new BluetoothHidController.Listener() {
                    @Override public void onStateChanged() {
                        refreshBondedDevices();
                        updateStatus();
                    }

                    @Override public void onInputConnected(String hostName) {
                        Toast.makeText(MainActivity.this,
                                "Bluetooth input connected ✓\n" + hostName,
                                Toast.LENGTH_LONG).show();
                    }
                });
        loadSettings();
        usb = new UsbBridgeServer(new UsbBridgeServer.Listener() {
            @Override public void onHostLine(String line) {
                handleHostLine(line);
            }

            @Override public void onStateChanged() {
                if (!usb.hasClients()) pcStats = "LAPTOP BAT —";
                updateStatus();
            }
        });
        feedback = new FeedbackController(this,
                () -> usb.send("SOUND KEY"));
        feedback.setEnabled(hapticsOn);
        feedback.setLaptopClicksEnabled(laptopClicksOn);
        neoUi = new NeoUi(this, feedback, INK);
        setContentView(buildUi());
        usb.start();
        requestBluetoothPermission();
    }

    @Override
    protected void onResume() {
        super.onResume();
        bluetooth.setForeground(true);
        bluetooth.setActive(mode == MODE_BLUETOOTH);
    }

    @Override
    protected void onPause() {
        if (controllerPanel != null) controllerPanel.releaseAll();
        bluetooth.setForeground(false);
        super.onPause();
    }

    private View buildUi() {
        int pad = dp(8);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        root.setBackgroundColor(PAPER);

        LinearLayout top = new LinearLayout(this);
        normalTopBar = top;
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        Button trackpadMenu = neoButton("TRACKPAD ▾", GREEN);
        trackpadMenu.setOnClickListener(this::showTrackpadPopup);
        top.addView(trackpadMenu, new LinearLayout.LayoutParams(dp(116), dp(54)));

        Button toolsMenu = neoButton("TOOLS ▾", YELLOW);
        toolsMenu.setOnClickListener(this::showToolsPopup);
        top.addView(toolsMenu, new LinearLayout.LayoutParams(dp(96), dp(54)));

        Button settingsMenu = neoButton("SET", PAPER);
        settingsMenu.setOnClickListener(this::showSettingsPopup);
        top.addView(settingsMenu, new LinearLayout.LayoutParams(dp(60), dp(54)));

        layoutSwitch = neoButton("GAMEPAD", BLUE);
        layoutSwitch.setOnClickListener(v -> showInputLayout(true, true));
        top.addView(layoutSwitch, new LinearLayout.LayoutParams(dp(78), dp(54)));
        
        Button ankiSwitch = neoButton("ANKI", CORAL);
        ankiSwitch.setOnClickListener(v -> showAnkiLayout(true));
        top.addView(ankiSwitch, new LinearLayout.LayoutParams(dp(74), dp(54)));

        TextView title = text("GlassHID", 18);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(INK);
        title.setGravity(Gravity.CENTER);
        top.addView(title, new LinearLayout.LayoutParams(dp(96), dp(54)));

        status = text("Starting local input services…", 11);
        status.setTextSize(10);
        status.setTextColor(INK);
        status.setGravity(Gravity.CENTER_VERTICAL);
        status.setMaxLines(2);
        top.addView(status, new LinearLayout.LayoutParams(0, dp(54), 1));

        RadioGroup modes = new RadioGroup(this);
        modes.setOrientation(LinearLayout.HORIZONTAL);
        RadioButton off = radio("Off", MODE_OFF);
        bluetoothModeButton = radio("BT", MODE_BLUETOOTH);
        RadioButton usb = radio("USB", MODE_USB);
        modes.addView(off);
        modes.addView(bluetoothModeButton);
        modes.addView(usb);
        modes.setOnCheckedChangeListener((group, checkedId) -> {
            if (mode == MODE_BLUETOOTH && checkedId != MODE_BLUETOOTH &&
                    controllerPanel != null) controllerPanel.releaseAll();
            mode = checkedId;
            getSharedPreferences("controls", MODE_PRIVATE).edit()
                    .putInt("active_mode", mode).apply();
            bluetooth.setActive(mode == MODE_BLUETOOTH);
            updateStatus();
        });
        if (mode == MODE_BLUETOOTH) bluetoothModeButton.setChecked(true);
        else if (mode == MODE_USB) usb.setChecked(true);
        else off.setChecked(true);
        top.addView(modes, new LinearLayout.LayoutParams(dp(165), dp(54)));

        Button pair = neoButton("PAIR", BLUE);
        pair.setOnClickListener(this::showBluetoothPopup);
        top.addView(pair, new LinearLayout.LayoutParams(dp(64), dp(54)));
        root.addView(top, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(58)));

        controllerTopBar = new LinearLayout(this);
        controllerTopBar.setGravity(Gravity.CENTER);
        controllerTopBar.setMotionEventSplittingEnabled(true);
        controllerTopBar.addView(controllerShoulder("L2", 7, BLUE),
                new LinearLayout.LayoutParams(0, dp(50), 1));
        controllerTopBar.addView(controllerShoulder("L1", 5, PAPER),
                new LinearLayout.LayoutParams(0, dp(50), 1));
        Button keys = neoButton("KEYS", BLUE);
        keys.setOnClickListener(v -> showInputLayout(false, true));
        controllerTopBar.addView(keys, new LinearLayout.LayoutParams(dp(140), dp(46)));
        controllerTopBar.addView(controllerShoulder("R1", 6, PAPER),
                new LinearLayout.LayoutParams(0, dp(50), 1));
        controllerTopBar.addView(controllerShoulder("R2", 8, BLUE),
                new LinearLayout.LayoutParams(0, dp(50), 1));
        root.addView(controllerTopBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(54)));

        inputContainer = new LinearLayout(this);
        inputContainer.setOrientation(LinearLayout.VERTICAL);
        inputContainer.setMotionEventSplittingEnabled(true);
        root.addView(inputContainer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        showInputLayout(controllerLayout, false);
        return root;
    }

    private View buildKeyboard() {
        LinearLayout keyboard = new LinearLayout(this);
        keyboard.setOrientation(LinearLayout.VERTICAL);
        keyboard.setPadding(0, dp(5), 0, 0);

        LinearLayout r1 = keyboardRow();
        addSpecialKey(r1, "Esc", "ESC", 0x29, 1.1f, CORAL);
        addPrintableKey(r1, "1\n!", "1", "!", 1); addPrintableKey(r1, "2\n@", "2", "@", 1);
        addPrintableKey(r1, "3\n#", "3", "#", 1); addPrintableKey(r1, "4\n$", "4", "$", 1);
        addPrintableKey(r1, "5\n%", "5", "%", 1); addPrintableKey(r1, "6\n^", "6", "^", 1);
        addPrintableKey(r1, "7\n&", "7", "&", 1); addPrintableKey(r1, "8\n*", "8", "*", 1);
        addPrintableKey(r1, "9\n(", "9", "(", 1); addPrintableKey(r1, "0\n)", "0", ")", 1);
        addPrintableKey(r1, "−\n_", "-", "_", 1); addPrintableKey(r1, "=\n+", "=", "+", 1);
        addRepeatingSpecialKey(r1, "Back", "BACKSPACE", 0x2A, 1.65f, CORAL);

        LinearLayout r2 = keyboardRow();
        addSpecialKey(r2, "Tab", "TAB", 0x2B, 1.4f, BLUE);
        for (String letter : new String[]{"Q","W","E","R","T","Y","U","I","O","P"}) addLetterKey(r2, letter);
        addPrintableKey(r2, "[\n{", "[", "{", 1); addPrintableKey(r2, "]\n}", "]", "}", 1);
        addPrintableKey(r2, "\\\n|", "\\", "|", 1.3f);

        LinearLayout r3 = keyboardRow();
        addModifierKey(r3, "Caps", "CAPS", 1.65f);
        for (String letter : new String[]{"A","S","D","F","G","H","J","K","L"}) addLetterKey(r3, letter);
        addPrintableKey(r3, ";\n:", ";", ":", 1); addPrintableKey(r3, "'\n\"", "'", "\"", 1);
        addSpecialKey(r3, "Enter", "ENTER", 0x28, 2.05f, YELLOW);

        LinearLayout r4 = keyboardRow();
        addModifierKey(r4, "Shift", "SHIFT", 1.55f);
        for (String letter : new String[]{"Z","X","C","V","B","N","M"}) addLetterKey(r4, letter);
        addPrintableKey(r4, ",\n<", ",", "<", 1); addPrintableKey(r4, ".\n>", ".", ">", 1);
        addPrintableKey(r4, "/\n?", "/", "?", 1);
        addSpecialKey(r4, "↑", "UP", 0x52, 1, BLUE);
        addModifierKey(r4, "⇧", "SHIFT", 0.8f);

        LinearLayout r5 = keyboardRow();
        addModifierKey(r5, "Ctrl", "CTRL", 1.4f);
        addModifierKey(r5, "Win\nSuper", "WIN", 1.45f);
        addModifierKey(r5, "Alt", "ALT", 1.25f);
        addSpecialKey(r5, "Home", "HOME", 0x4A, 1.25f, BLUE);
        addSpecialKey(r5, "Space", "SPACE", 0x2C, 6.2f, YELLOW);
        addSpecialKey(r5, "End", "END", 0x4D, 1.25f, BLUE);
        addSpecialKey(r5, "←", "LEFT", 0x50, 1, BLUE);
        addSpecialKey(r5, "↓", "DOWN", 0x51, 1, BLUE);
        addSpecialKey(r5, "→", "RIGHT", 0x4F, 1, BLUE);

        keyboard.addView(r1, rowParams()); keyboard.addView(r2, rowParams());
        keyboard.addView(r3, rowParams()); keyboard.addView(r4, rowParams());
        keyboard.addView(r5, rowParams());
        return keyboard;
    }

    private void showInputLayout(boolean useController, boolean announce) {
        controllerLayout = useController;
        saveSettings();
        if (normalTopBar != null)
            normalTopBar.setVisibility(useController ? View.GONE : View.VISIBLE);
        if (controllerTopBar != null)
            controllerTopBar.setVisibility(useController ? View.VISIBLE : View.GONE);
        if (inputContainer == null) return;

        if (controllerPanel != null) controllerPanel.releaseAll();
        controllerPanel = null;
        inputContainer.removeAllViews();
        if (useController) {
            bluetoothModeButton.setChecked(true);
            controllerPanel = new ControllerPanel(this, neoUi, feedback,
                    this::sendGamepadReport, swapGamepadControls, gamepadLabelStyle);
            inputContainer.addView(controllerPanel.build(),
                    new LinearLayout.LayoutParams(-1, -1));
            if (announce) Toast.makeText(this,
                    "Bluetooth controller layout", Toast.LENGTH_SHORT).show();
        } else {
            inputContainer.addView(buildKeyboard(),
                    new LinearLayout.LayoutParams(-1, -1));
            if (announce) Toast.makeText(this,
                    "Keyboard layout", Toast.LENGTH_SHORT).show();
        }
        updateStatus();
    }

    private Button controllerShoulder(String label, int buttonNumber, int color) {
        Button button = neoButton(label, color);
        button.setOnTouchListener((view, event) -> {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                feedback.perform(view, HapticFeedbackConstants.KEYBOARD_TAP);
                if (controllerPanel != null) controllerPanel.setButton(buttonNumber, true);
                view.animate().scaleX(0.95f).scaleY(0.95f).setDuration(45).start();
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                if (controllerPanel != null) controllerPanel.setButton(buttonNumber, false);
                view.animate().scaleX(1f).scaleY(1f).setDuration(65).start();
            }
            return true;
        });
        return button;
    }
private void showAnkiLayout(boolean announce) {
    if (controllerPanel != null) controllerPanel.releaseAll();
    controllerPanel = null;

    if (normalTopBar != null) normalTopBar.setVisibility(View.VISIBLE);
    if (controllerTopBar != null) controllerTopBar.setVisibility(View.GONE);

    inputContainer.removeAllViews();
    inputContainer.addView(buildAnkiLayout(),
            new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT));

    if (announce) {
        Toast.makeText(this, "Anki review layout", Toast.LENGTH_SHORT).show();
    }
    updateStatus();
}

private View buildAnkiLayout() {
    LinearLayout root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setPadding(dp(8), dp(6), dp(8), dp(6));
    root.setBackgroundColor(PAPER);

    TextView title = text("ANKI REVIEW", 20);
    title.setTypeface(Typeface.DEFAULT_BOLD);
    title.setGravity(Gravity.CENTER);
    title.setTextColor(INK);
    root.addView(title, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(42)));

    TextView mapping = text(
            "FLIP = SPACE        AGAIN = 1        HARD = 2        GOOD = 3        EASY = 4",
            11);
    mapping.setTypeface(Typeface.DEFAULT_BOLD);
    mapping.setGravity(Gravity.CENTER);
    mapping.setTextColor(INK);
    root.addView(mapping, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(34)));

    LinearLayout flipRow = keyboardRow();
    addAnkiButton(
            flipRow,
            "FLIP\nSPACE",
            1.0f,
            YELLOW,
            () -> sendText(" "));
    root.addView(flipRow, rowParams());

    LinearLayout ratingRow = keyboardRow();
    addAnkiButton(
            ratingRow,
            "AGAIN\n1",
            1.0f,
            CORAL,
            () -> sendText("1"));
    addAnkiButton(
            ratingRow,
            "HARD\n2",
            1.0f,
            PAPER,
            () -> sendText("2"));
    addAnkiButton(
            ratingRow,
            "GOOD\n3",
            1.0f,
            GREEN,
            () -> sendText("3"));
    addAnkiButton(
            ratingRow,
            "EASY\n4",
            1.0f,
            BLUE,
            () -> sendText("4"));
    root.addView(ratingRow, rowParams());

    LinearLayout bottomRow = keyboardRow();

    addAnkiButton(
            bottomRow,
            "KEYBOARD",
            1.0f,
            PAPER,
            () -> showInputLayout(false, true));

    addAnkiButton(
            bottomRow,
            "GAMEPAD",
            1.0f,
            BLUE,
            () -> showInputLayout(true, true));

    root.addView(bottomRow, rowParams());

    return root;
}

private void addAnkiButton(
        LinearLayout row,
        String label,
        float weight,
        int color,
        Runnable action) {

    Button button = neoButton(label, color);

    button.setTextSize(16);
    button.setTypeface(Typeface.DEFAULT_BOLD);

    button.setOnClickListener(v -> {
        feedback.perform(v, HapticFeedbackConstants.KEYBOARD_TAP);
        action.run();
    });

    row.addView(button, keyParams(weight));
}
    private void showTrackpadPopup(View anchor) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(8), dp(8), dp(8), dp(8));
        card.setBackground(neoBackground(GREEN));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView dockLabel = text(trackpadOnRight ? "RIGHT DOCK" : "LEFT DOCK", 12);
        dockLabel.setTypeface(Typeface.DEFAULT_BOLD);
        dockLabel.setTextColor(INK);
        header.addView(dockLabel, new LinearLayout.LayoutParams(0, dp(42), 1));
        Button moveSide = neoButton(trackpadOnRight ? "← LEFT" : "RIGHT →", BLUE);
        moveSide.setOnClickListener(v -> {
            trackpadOnRight = !trackpadOnRight;
            if (trackpadPopup != null) trackpadPopup.dismiss();
            showTrackpadPopup(anchor);
        });
        header.addView(moveSide, new LinearLayout.LayoutParams(dp(105), dp(42)));
        card.addView(header, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(44)));

        LinearLayout surfaces = new LinearLayout(this);
        surfaces.setOrientation(LinearLayout.HORIZONTAL);

        TextView trackpad = text("TRACKPAD\nTap · hold/double-tap drag\n3 fingers · swipe", 15);
        trackpad.setTypeface(Typeface.DEFAULT_BOLD);
        trackpad.setGravity(Gravity.CENTER);
        trackpad.setTextColor(PAPER);
        trackpad.setBackground(rounded(INK));
        trackpad.setOnTouchListener(new TrackpadGestureListener(uiHandler, this));
        trackpad.setOnHoverListener((view, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_HOVER_ENTER) {
                view.animate().scaleX(1.015f).scaleY(1.015f).setDuration(80).start();
            } else if (event.getActionMasked() == MotionEvent.ACTION_HOVER_EXIT) {
                view.animate().scaleX(1f).scaleY(1f).setDuration(80).start();
            }
            return false;
        });
        surfaces.addView(trackpad, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, 1));

        TextView scrollPad = text("SCROLL\n▲\n↕\n▼", 12);
        scrollPad.setTypeface(Typeface.DEFAULT_BOLD);
        scrollPad.setGravity(Gravity.CENTER);
        scrollPad.setTextColor(INK);
        scrollPad.setBackground(rounded(BLUE));
        scrollPad.setOnTouchListener(new ScrollPadListener(this));
 scrollPad.setOnHoverListener((view, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_HOVER_ENTER) {
                view.animate().scaleX(1.025f).scaleY(1.015f).setDuration(80).start();
            } else if (event.getActionMasked() == MotionEvent.ACTION_HOVER_EXIT) {
                view.animate().scaleX(1f).scaleY(1f).setDuration(80).start();
            }
            return false;
        });
        LinearLayout.LayoutParams scrollPadParams = new LinearLayout.LayoutParams(
                dp(62), LinearLayout.LayoutParams.MATCH_PARENT);
        scrollPadParams.setMargins(dp(6), 0, 0, 0);
        surfaces.addView(scrollPad, scrollPadParams);

        card.addView(surfaces, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout clicks = new LinearLayout(this);
        Button left = neoButton("LEFT", YELLOW);
        left.setOnClickListener(v -> click("left"));
        Button right = neoButton("RIGHT", CORAL);
        right.setOnClickListener(v -> click("right"));
        clicks.addView(left, weighted()); clicks.addView(right, weighted());
        card.addView(clicks, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52)));

        int trackpadWidth = dp(520);
        trackpadPopup = new PopupWindow(card, trackpadWidth, dp(300), true);
        trackpadPopup.setBackgroundDrawable(rounded(Color.TRANSPARENT));
        trackpadPopup.setOutsideTouchable(true);
        trackpadPopup.setElevation(dp(12));
        int[] location = new int[2];
        anchor.getLocationOnScreen(location);
        int xOffset = trackpadOnRight
                ? getResources().getDisplayMetrics().widthPixels - trackpadWidth - location[0]
                : 0;
        trackpadPopup.showAsDropDown(anchor, xOffset, dp(4));
    }

    private void showSystemPopup(View anchor) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(8), dp(8), dp(8), dp(8));
        card.setBackground(neoBackground(BLUE));

        systemStatsView = text(pcStats, 13);
        systemStatsView.setTypeface(Typeface.DEFAULT_BOLD);
        systemStatsView.setTextColor(INK);
        systemStatsView.setGravity(Gravity.CENTER);
        systemStatsView.setMaxLines(2);
        card.addView(systemStatsView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48)));

        LinearLayout volume = keyboardRow();
        addSystemKey(volume, "VOL −", "VOLUME_DOWN", 0xEA, CORAL);
        addSystemKey(volume, "MUTE", "VOLUME_MUTE", 0xE2, YELLOW);
        addSystemKey(volume, "VOL +", "VOLUME_UP", 0xE9, GREEN);
        card.addView(volume, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52)));

        LinearLayout brightness = keyboardRow();
        addSystemKey(brightness, "BRIGHT −", "BRIGHTNESS_DOWN", 0x70, CORAL);
        addSystemKey(brightness, "BRIGHT +", "BRIGHTNESS_UP", 0x6F, GREEN);
        card.addView(brightness, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52)));

        systemPopup = new PopupWindow(card, dp(310), dp(176), true);
        systemPopup.setBackgroundDrawable(rounded(Color.TRANSPARENT));
        systemPopup.setOutsideTouchable(true);
        systemPopup.setElevation(dp(12));
        systemPopup.setOnDismissListener(() -> systemStatsView = null);
        systemPopup.showAsDropDown(anchor, 0, dp(4));
    }

    private void showToolsPopup(View anchor) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(8), dp(8), dp(8), dp(8));
        card.setBackground(neoBackground(YELLOW));

        Button functions = neoButton("F KEYS + NAV", PAPER);
        functions.setOnClickListener(v -> {
            toolsPopup.dismiss();
            uiHandler.post(() -> showFunctionPopup(anchor));
        });
        card.addView(functions, new LinearLayout.LayoutParams(dp(190), dp(52)));

        Button system = neoButton("SYSTEM", BLUE);
        system.setOnClickListener(v -> {
            toolsPopup.dismiss();
            uiHandler.post(() -> showSystemPopup(anchor));
        });
        card.addView(system, new LinearLayout.LayoutParams(dp(190), dp(52)));

        toolsPopup = new PopupWindow(card, dp(206), dp(120), true);
        toolsPopup.setBackgroundDrawable(rounded(Color.TRANSPARENT));
        toolsPopup.setOutsideTouchable(true);
        toolsPopup.setElevation(dp(12));
        toolsPopup.showAsDropDown(anchor, 0, dp(4));
    }

    private void addSystemKey(LinearLayout row, String label, String name, int usage, int color) {
        Button key = neoButton(label, color);
        key.setOnClickListener(v -> sendSystemControl(name, usage));
        row.addView(key, weighted());
    }

    private void loadSettings() {
        SharedPreferences values = getSharedPreferences("controls", MODE_PRIVATE);
        mode = values.getInt("active_mode", MODE_OFF);
        dragHoldMs = values.getInt("drag_hold_ms", 500);
        pointerPercent = values.getInt("pointer_percent", 100);
        scrollPercent = values.getInt("scroll_percent", 100);
        repeatMs = values.getInt("repeat_ms", 55);
        hapticsOn = values.getBoolean("haptics", true);
        laptopClicksOn = values.getBoolean("laptop_clicks", true);
        controllerLayout = values.getBoolean("controller_layout", false);
        swapGamepadControls = values.getBoolean("swap_gamepad_controls", false);
        gamepadLabelStyle = values.getInt("gamepad_label_style", 0);
    }

    private void saveSettings() {
        getSharedPreferences("controls", MODE_PRIVATE).edit()
                .putInt("drag_hold_ms", dragHoldMs)
                .putInt("pointer_percent", pointerPercent)
                .putInt("scroll_percent", scrollPercent)
                .putInt("repeat_ms", repeatMs)
                .putBoolean("haptics", hapticsOn)
                .putBoolean("laptop_clicks", laptopClicksOn)
                .putBoolean("controller_layout", controllerLayout)
                .putBoolean("swap_gamepad_controls", swapGamepadControls)
                .putInt("gamepad_label_style", gamepadLabelStyle)
                .apply();
    }

    private void showSettingsPopup(View anchor) {
        settingValueViews.clear();
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(8), dp(8), dp(8), dp(8));
        card.setBackground(neoBackground(YELLOW));

        TextView title = text("INPUT SETTINGS", 15);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        title.setTextColor(INK);
        card.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(36)));

        card.addView(settingRow("DRAG", "Drag hold"), new LinearLayout.LayoutParams(-1, dp(46)));
        card.addView(settingRow("POINTER", "Pointer speed"), new LinearLayout.LayoutParams(-1, dp(46)));
        card.addView(settingRow("SCROLL", "Scroll speed"), new LinearLayout.LayoutParams(-1, dp(46)));
        card.addView(settingRow("REPEAT", "Key repeat"), new LinearLayout.LayoutParams(-1, dp(46)));

        Button haptics = neoButton("HAPTICS · " + (hapticsOn ? "ON" : "OFF"), GREEN);
        settingValueViews.put("HAPTICS", haptics);
        haptics.setOnClickListener(v -> {
            hapticsOn = !hapticsOn;
            saveSettings();
            feedback.setEnabled(hapticsOn);
            refreshSettingValues();
            haptic(v, HapticFeedbackConstants.CLOCK_TICK);
        });
        card.addView(haptics, new LinearLayout.LayoutParams(-1, dp(48)));

        Button laptopClicks = neoButton("LAPTOP CLICKS · " +
                (laptopClicksOn ? "ON" : "OFF"), BLUE);
        settingValueViews.put("LAPTOP_CLICKS", laptopClicks);
        laptopClicks.setOnClickListener(v -> {
            laptopClicksOn = !laptopClicksOn;
            feedback.setLaptopClicksEnabled(laptopClicksOn);
            saveSettings();
            refreshSettingValues();
        });
        card.addView(laptopClicks, new LinearLayout.LayoutParams(-1, dp(48)));

        Button controllerSwap = neoButton("SWAP PAD / STICKS · " +
                (swapGamepadControls ? "ON" : "OFF"), CORAL);
        settingValueViews.put("CONTROLLER_SWAP", controllerSwap);
        controllerSwap.setOnClickListener(v -> {
            swapGamepadControls = !swapGamepadControls;
            saveSettings();
            refreshSettingValues();
        });
        card.addView(controllerSwap, new LinearLayout.LayoutParams(-1, dp(48)));

        Button controllerLabels = neoButton("CONTROLLER LABELS · " +
                gamepadLabelStyleName(), BLUE);
        settingValueViews.put("CONTROLLER_LABELS", controllerLabels);
        controllerLabels.setOnClickListener(v -> {
            gamepadLabelStyle = (gamepadLabelStyle + 1) % 3;
            saveSettings();
            refreshSettingValues();
            haptic(v, HapticFeedbackConstants.CLOCK_TICK);
        });
        card.addView(controllerLabels, new LinearLayout.LayoutParams(-1, dp(48)));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(card, new ScrollView.LayoutParams(-1,
                ScrollView.LayoutParams.WRAP_CONTENT));
        settingsPopup = new PopupWindow(scroll, dp(315), dp(334), true);
        settingsPopup.setBackgroundDrawable(rounded(Color.TRANSPARENT));
        settingsPopup.setOutsideTouchable(true);
        settingsPopup.setElevation(dp(12));
        settingsPopup.setOnDismissListener(settingValueViews::clear);
        settingsPopup.showAsDropDown(anchor, 0, dp(4));
        refreshSettingValues();
    }

    private LinearLayout settingRow(String key, String label) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = text(label, 12);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setTextColor(INK);
        row.addView(name, new LinearLayout.LayoutParams(0, -1, 1));

        Button minus = neoButton("−", CORAL);
        minus.setOnClickListener(v -> adjustSetting(key, -1));
        row.addView(minus, new LinearLayout.LayoutParams(dp(46), dp(42)));

        TextView value = text("", 12);
        value.setTypeface(Typeface.DEFAULT_BOLD);
        value.setGravity(Gravity.CENTER);
        value.setTextColor(INK);
        settingValueViews.put(key, value);
        row.addView(value, new LinearLayout.LayoutParams(dp(72), -1));

        Button plus = neoButton("+", GREEN);
        plus.setOnClickListener(v -> adjustSetting(key, 1));
        row.addView(plus, new LinearLayout.LayoutParams(dp(46), dp(42)));
        return row;
    }

    private void adjustSetting(String key, int direction) {
        switch (key) {
            case "DRAG":
                dragHoldMs = Math.max(350, Math.min(1000, dragHoldMs + direction * 50));
                break;
            case "POINTER":
                pointerPercent = Math.max(50, Math.min(200, pointerPercent + direction * 10));
                break;
            case "SCROLL":
                scrollPercent = Math.max(50, Math.min(200, scrollPercent + direction * 10));
                break;
            case "REPEAT":
                repeatMs = Math.max(35, Math.min(145, repeatMs + direction * 10));
                break;
        }
        saveSettings();
        refreshSettingValues();
    }

    private void refreshSettingValues() {
        if (settingValueViews.containsKey("DRAG"))
            settingValueViews.get("DRAG").setText(dragHoldMs + " ms");
        if (settingValueViews.containsKey("POINTER"))
            settingValueViews.get("POINTER").setText(pointerPercent + "%");
        if (settingValueViews.containsKey("SCROLL"))
            settingValueViews.get("SCROLL").setText(scrollPercent + "%");
        if (settingValueViews.containsKey("REPEAT"))
            settingValueViews.get("REPEAT").setText(repeatMs + " ms");
        if (settingValueViews.containsKey("HAPTICS"))
            settingValueViews.get("HAPTICS").setText("HAPTICS · " + (hapticsOn ? "ON" : "OFF"));
        if (settingValueViews.containsKey("LAPTOP_CLICKS"))
            settingValueViews.get("LAPTOP_CLICKS").setText("LAPTOP CLICKS · " +
                    (laptopClicksOn ? "ON" : "OFF"));
        if (settingValueViews.containsKey("CONTROLLER_SWAP"))
            settingValueViews.get("CONTROLLER_SWAP").setText("SWAP PAD / STICKS · " +
                    (swapGamepadControls ? "ON" : "OFF"));
        if (settingValueViews.containsKey("CONTROLLER_LABELS"))
            settingValueViews.get("CONTROLLER_LABELS").setText("CONTROLLER LABELS · " +
                    gamepadLabelStyleName());
    }

    private String gamepadLabelStyleName() {
        return gamepadLabelStyle == 1 ? "ABXY" : gamepadLabelStyle == 2 ? "1–4" : "PS";
    }

    private void showFunctionPopup(View anchor) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(8), dp(8), dp(8), dp(8));
        card.setBackground(neoBackground(YELLOW));

        LinearLayout functions = keyboardRow();
        for (int number = 1; number <= 12; number++) {
            addSpecialKey(functions, "F" + number, "F" + number,
                    0x39 + number, 1, PAPER);
        }
        card.addView(functions, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(58)));

        LinearLayout navigation = keyboardRow();
        addSpecialKey(navigation, "PrtSc", "PRINTSCREEN", 0x46, 1.35f, BLUE);
        addSpecialKey(navigation, "ScrLk", "SCROLLLOCK", 0x47, 1.35f, BLUE);
        addSpecialKey(navigation, "Pause", "PAUSE", 0x48, 1.35f, BLUE);
        addSpecialKey(navigation, "Ins", "INSERT", 0x49, 1, PAPER);
        addSpecialKey(navigation, "Del", "DELETE", 0x4C, 1, CORAL);
        addSpecialKey(navigation, "PgUp", "PGUP", 0x4B, 1.2f, PAPER);
        addSpecialKey(navigation, "PgDn", "PGDN", 0x4E, 1.2f, PAPER);
        addSpecialKey(navigation, "Home", "HOME", 0x4A, 1.3f, PAPER);
        addSpecialKey(navigation, "End", "END", 0x4D, 1.1f, PAPER);
        card.addView(navigation, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(58)));

        PopupWindow popup = new PopupWindow(card, dp(750), dp(132), true);
        popup.setBackgroundDrawable(rounded(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setElevation(dp(12));
        popup.showAsDropDown(anchor, 0, dp(4));
    }

    private void showBluetoothPopup(View anchor) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(8), dp(8), dp(8), dp(8));
        card.setBackground(neoBackground(BLUE));

        bluetoothStateView = text(bluetoothInputStatus(), 13);
        bluetoothStateView.setTypeface(Typeface.DEFAULT_BOLD);
        bluetoothStateView.setTextColor(INK);
        bluetoothStateView.setGravity(Gravity.CENTER);
        bluetoothStateView.setBackground(rounded(bluetooth.isInputLive() ? GREEN : PAPER));
        card.addView(bluetoothStateView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(58)));

        Button visible = neoButton("MAKE PHONE VISIBLE", YELLOW);
        visible.setOnClickListener(v -> {
            bluetoothModeButton.setChecked(true);
            Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            intent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
            startActivity(intent);
        });
        card.addView(visible, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52)));

        Button settings = neoButton("BLUETOOTH SETTINGS", PAPER);
        settings.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)));
        card.addView(settings, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52)));

        pairedDevices = new LinearLayout(this);
        pairedDevices.setOrientation(LinearLayout.VERTICAL);
        card.addView(pairedDevices);
        refreshBondedDevices();

        PopupWindow popup = new PopupWindow(card, dp(360),
                LinearLayout.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(rounded(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setElevation(dp(12));
        popup.setOnDismissListener(() -> {
            pairedDevices = null;
            bluetoothStateView = null;
        });
        popup.showAsDropDown(anchor, -dp(275), dp(4));
    }

    private void requestBluetoothPermission() {
        if (Build.VERSION.SDK_INT >= 31 &&
                !bluetooth.hasPermission()) {
            requestPermissions(new String[]{
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN
            }, REQUEST_BT);
        } else {
            bluetooth.bind();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_BT && grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            bluetooth.bind();
        } else {
            updateStatus();
        }
    }

    private void refreshBondedDevices() {
        runOnUiThread(() -> {
            if (pairedDevices == null) return;
            pairedDevices.removeAllViews();
            if (!bluetooth.hasPermission()) return;
            Set<BluetoothDevice> devices = bluetooth.bondedDevices();
            for (BluetoothDevice device : devices) {
                boolean connected = bluetooth.isConnectedTo(device);
                Button connect = neoButton((connected ? "INPUT CONNECTED ✓ · " :
                        "CONNECT INPUT · ") + bluetooth.safeName(device), connected ? GREEN : PAPER);
                connect.setOnClickListener(v -> {
                    if (connected) {
                        Toast.makeText(this, "Keyboard and mouse input is live",
                                Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Connecting keyboard and mouse…",
                                Toast.LENGTH_SHORT).show();
                        bluetoothModeButton.setChecked(true);
                        bluetooth.connect(device);
                    }
                    updateStatus();
                });
                pairedDevices.addView(connect, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dp(52)));
            }
            updateBluetoothPanel();
        });
    }

    private String bluetoothInputStatus() {
        return bluetooth.detailedStatus();
    }

    private void updateBluetoothPanel() {
        if (bluetoothStateView == null) return;
        bluetoothStateView.setText(bluetoothInputStatus());
        bluetoothStateView.setBackground(rounded(bluetooth.isInputLive() ? GREEN : PAPER));
    }

    private void handleHostLine(String line) {
        if (!line.startsWith("BATTERY ")) return;
        String[] values = line.substring(8).trim().split("\\s+");
        if (values.length < 2) return;
        pcStats = "LAPTOP BAT " + metric(values[0], "%") +
                ("1".equals(values[1]) ? " ⚡" : "");
        updateStatus();
    }

    private String metric(String raw, String suffix) {
        try {
            int value = Integer.parseInt(raw);
            return value < 0 ? "—" : value + suffix;
        } catch (NumberFormatException e) {
            return "—";
        }
    }

    private void broadcast(String line) {
        if (mode != MODE_USB) return;
        usb.send(line);
    }

    private void sendText(String value) {
        if (mode == MODE_USB) {
            String encoded = Base64.encodeToString(value.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
            broadcast("TEXT " + encoded);
        } else if (mode == MODE_BLUETOOTH) {
            for (char c : value.toCharArray()) sendBluetoothChar(c);
        }
    }

    private void sendBluetoothChar(char c) {
        KeyStroke key = KeyStroke.forChar(c);
        if (key == null) return;
        sendBluetoothKey(key.modifier, key.usage);
    }

    private void sendSpecial(String name, int usage) {
        if (mode == MODE_USB) broadcast("KEY " + name);
        else if (mode == MODE_BLUETOOTH) sendBluetoothKey(0, usage);
    }

    private void pressSpecial(String name, int usage) {
        int modifier = activeBluetoothModifier();
        if (modifier == 0) {
            sendSpecial(name, usage);
        } else if (mode == MODE_USB) {
            broadcast("HOTKEY " + activeChordPrefix() + name);
        } else if (mode == MODE_BLUETOOTH) {
            sendBluetoothKey(modifier, usage);
        }
        clearOneShotModifiers();
    }

    private void pressPrintable(String normal, String shifted) {
        boolean letter = normal.length() == 1 && Character.isLetter(normal.charAt(0));
        if (ctrlOn || altOn || winOn) {
            KeyStroke key = KeyStroke.forChar(normal.charAt(0));
            if (key != null) {
                if (mode == MODE_USB) {
                    broadcast("HOTKEY " + activeChordPrefix() + normal.toUpperCase());
                } else if (mode == MODE_BLUETOOTH) {
                    sendBluetoothKey(activeBluetoothModifier(), key.usage);
                }
            }
            clearOneShotModifiers();
            return;
        }

        boolean useShift = shiftOn;
        String value;
        if (letter) value = (capsOn ^ useShift) ? normal.toUpperCase() : normal.toLowerCase();
        else value = useShift ? shifted : normal;
        sendText(value);
        if (shiftOn) {
            shiftOn = false;
            refreshModifierStyles();
        }
    }

    private int activeBluetoothModifier() {
        return (ctrlOn ? 0x01 : 0) | (shiftOn ? 0x02 : 0) |
                (altOn ? 0x04 : 0) | (winOn ? 0x08 : 0);
    }

    private String activeChordPrefix() {
        StringBuilder value = new StringBuilder();
        if (ctrlOn) value.append("CTRL+");
        if (shiftOn) value.append("SHIFT+");
        if (altOn) value.append("ALT+");
        if (winOn) value.append("WIN+");
        return value.toString();
    }

    private void clearOneShotModifiers() {
        shiftOn = false;
        ctrlOn = false;
        altOn = false;
        winOn = false;
        refreshModifierStyles();
    }

    private void sendStandaloneWindowsKey() {
        if (mode == MODE_USB) broadcast("KEY WIN");
        else if (mode == MODE_BLUETOOTH) sendBluetoothKey(0x08, 0);
    }

    private void sendBluetoothKey(int modifier, int usage) {
        bluetooth.sendKey(modifier, usage);
    }

    private void sendSystemControl(String name, int consumerUsage) {
        // Laptop brightness is deliberately cable-only. Windows Bluetooth HID
        // implementations interpret brightness usages inconsistently, and socket
        // writes must never block Android's UI thread.
        if (name.startsWith("BRIGHTNESS") || mode != MODE_BLUETOOTH) {
            usb.send("MEDIA " + name);
        } else {
            sendBluetoothConsumer(consumerUsage);
        }
    }

    private void sendBluetoothConsumer(int usage) {
        bluetooth.sendConsumer(usage);
    }

    @Override
    public void move(int dx, int dy) {
        if (mode == MODE_USB) broadcast("MOVE " + dx + " " + dy);
        else if (mode == MODE_BLUETOOTH) sendBluetoothMouse(mouseButtons, dx, dy, 0);
    }

    @Override
    public void scroll(int amount) {
        if (mode == MODE_USB) broadcast("SCROLL " + amount);
        else if (mode == MODE_BLUETOOTH) sendBluetoothMouse(mouseButtons, 0, 0, amount);
    }

    private void click(String which) {
        setMouseButton(which, true);
        uiHandler.postDelayed(() -> setMouseButton(which, false), 38);
    }

    @Override
    public void clickLeft() {
        click("left");
    }

    @Override
    public void setLeftButton(boolean down) {
        setMouseButton("left", down);
    }

    @Override
    public void threeFingerSwipe(TrackpadGestureListener.Swipe direction) {
        String usbChord;
        int modifier;
        int usage;
        switch (direction) {
            case LEFT:
                usbChord = "WIN+CTRL+LEFT";
                modifier = 0x09;
                usage = 0x50;
                break;
            case RIGHT:
                usbChord = "WIN+CTRL+RIGHT";
                modifier = 0x09;
                usage = 0x4F;
                break;
            case UP:
                usbChord = "WIN+TAB";
                modifier = 0x08;
                usage = 0x2B;
                break;
            default:
                usbChord = "WIN+D";
                modifier = 0x08;
                usage = 0x07;
                break;
        }
        if (mode == MODE_USB) broadcast("HOTKEY " + usbChord);
        else if (mode == MODE_BLUETOOTH) bluetooth.sendKey(modifier, usage);
        Toast.makeText(this, "3-finger " + direction.name().toLowerCase(),
                Toast.LENGTH_SHORT).show();
    }

    private void setMouseButton(String which, boolean down) {
        int mask = which.equals("right") ? 2 : 1;
        if (down) mouseButtons |= mask;
        else mouseButtons &= ~mask;
        if (mode == MODE_USB) {
            broadcast("BUTTON " + which.toUpperCase() + " " + (down ? "DOWN" : "UP"));
        } else if (mode == MODE_BLUETOOTH) {
            sendBluetoothMouse(mouseButtons, 0, 0, 0);
        }
    }

    private void sendBluetoothMouse(int buttons, int dx, int dy, int wheel) {
        bluetooth.sendMouse(buttons, dx, dy, wheel);
    }

    private void sendGamepadReport(int buttons, int leftX, int leftY,
                                   int rightX, int rightY, int hat) {
        if (mode == MODE_BLUETOOTH)
            bluetooth.sendGamepad(buttons, leftX, leftY, rightX, rightY, hat);
    }

    private void updateStatus() {
        if (status == null) return;
        runOnUiThread(() -> {
            String selected = mode == MODE_USB ? "USB" : mode == MODE_BLUETOOTH ? "BT" : "OFF";
            String connection;
            if (mode == MODE_USB) {
                connection = !usb.isStarted() ? "starting" :
                        (!usb.hasClients() ? "waiting for PC" : "PC connected");
            } else if (mode == MODE_BLUETOOTH) {
                connection = bluetooth.shortStatus();
            } else {
                connection = !usb.hasClients() ? "local" : "PC linked";
            }
            String detail = pcStats;
            if (activeBluetoothModifier() != 0) {
                String chord = activeChordPrefix();
                detail = "NEXT · " + chord.substring(0, chord.length() - 1);
            }
            status.setText(selected + " · " + connection + "\n" + detail);
            if (controllerPanel != null) {
                String controllerConnection = mode == MODE_BLUETOOTH &&
                        bluetooth.isInputLive() ? "BT · INPUT CONNECTED ✓" :
                        selected + " · " + connection;
                controllerPanel.setConnectionStatus(controllerConnection);
            }
            if (systemStatsView != null) systemStatsView.setText(pcStats);
            updateBluetoothPanel();
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        bluetooth.destroy();
        usb.close();
    }

    private LinearLayout keyboardRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        return row;
    }

    private LinearLayout.LayoutParams rowParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1);
        params.setMargins(0, dp(1), 0, dp(1));
        return params;
    }

    private LinearLayout.LayoutParams keyParams(float weight) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, weight);
        params.setMargins(dp(2), dp(1), dp(2), dp(1));
        return params;
    }

    private void addLetterKey(LinearLayout row, String letter) {
        addPrintableKey(row, letter, letter.toLowerCase(), letter.toUpperCase(), 1);
    }

    private void addPrintableKey(LinearLayout row, String label, String normal, String shifted, float weight) {
        Button key = neoButton(label, PAPER);
        key.setOnClickListener(v -> pressPrintable(normal, shifted));
        row.addView(key, keyParams(weight));
    }

    private void addSpecialKey(LinearLayout row, String label, String name, int usage,
                               float weight, int color) {
        Button key = neoButton(label, color);
        key.setOnClickListener(v -> pressSpecial(name, usage));
        row.addView(key, keyParams(weight));
    }

    private void addRepeatingSpecialKey(LinearLayout row, String label, String name, int usage,
                                        float weight, int color) {
        Button key = neoButton(label, color);
        key.setOnClickListener(v -> pressSpecial(name, usage));
        Handler handler = new Handler(Looper.getMainLooper());
        Runnable[] repeat = new Runnable[1];
        repeat[0] = () -> {
            if (key.isPressed()) {
                pressSpecial(name, usage);
                handler.postDelayed(repeat[0], repeatMs);
            }
        };
        key.setOnLongClickListener(v -> {
            repeat[0].run();
            return true;
        });
        row.addView(key, keyParams(weight));
    }

    private void addModifierKey(LinearLayout row, String label, String modifier, float weight) {
        Button key = neoButton(label, PAPER);
        switch (modifier) {
            case "SHIFT": shiftButtons.add(key); break;
            case "CAPS": capsButtons.add(key); break;
            case "CTRL": ctrlButtons.add(key); break;
            case "ALT": altButtons.add(key); break;
            case "WIN": winButtons.add(key); break;
        }
        key.setOnClickListener(v -> {
            switch (modifier) {
                case "SHIFT": shiftOn = !shiftOn; break;
                case "CAPS": capsOn = !capsOn; break;
                case "CTRL": ctrlOn = !ctrlOn; break;
                case "ALT": altOn = !altOn; break;
                case "WIN": winOn = !winOn; break;
            }
            refreshModifierStyles();
        });
        if (modifier.equals("WIN")) {
            key.setOnLongClickListener(v -> {
                winOn = false;
                sendStandaloneWindowsKey();
                refreshModifierStyles();
                return true;
            });
        }
        row.addView(key, keyParams(weight));
    }

    private void refreshModifierStyles() {
        styleModifiers(shiftButtons, shiftOn);
        styleModifiers(capsButtons, capsOn);
        styleModifiers(ctrlButtons, ctrlOn);
        styleModifiers(altButtons, altOn);
        styleModifiers(winButtons, winOn);
        updateStatus();
    }

    private void styleModifiers(List<Button> buttons, boolean active) {
        for (Button button : buttons) button.setBackground(interactiveNeoBackground(active ? GREEN : PAPER));
    }

    private RadioButton radio(String label, int id) {
        return neoUi.radio(label, id);
    }

    private Button button(String label) {
        return neoButton(label, PAPER);
    }

    private Button neoButton(String label, int color) {
        return neoUi.button(label, color);
    }

    private TextView text(String value, int sp) {
        return neoUi.text(value, sp);
    }

    private LinearLayout.LayoutParams weighted() {
        return new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
    }

    private GradientDrawable rounded(int color) {
        return neoUi.rounded(color);
    }

    private LayerDrawable neoBackground(int color) {
        return neoUi.background(color);
    }

    private StateListDrawable interactiveNeoBackground(int color) {
        return neoUi.interactiveBackground(color);
    }

    @Override
    public int dp(int value) {
        return neoUi.dp(value);
    }

    @Override
    public int dragHoldMs() {
        return dragHoldMs;
    }

    @Override
    public int pointerPercent() {
        return pointerPercent;
    }

    @Override
    public int scrollPercent() {
        return scrollPercent;
    }

    @Override
    public void haptic(View view, int feedback) {
        this.feedback.perform(view, feedback);
    }

    @Override
    public void trackpadVisual(View view, int state) {
        int color = state == TrackpadGestureListener.VISUAL_DRAGGING
                ? Color.rgb(72, 72, 72)
                : state == TrackpadGestureListener.VISUAL_PRESSED
                ? Color.rgb(45, 45, 45) : INK;
        view.setBackground(rounded(color));
    }

    @Override
    public void scrollVisual(View view, boolean pressed) {
        view.setBackground(rounded(pressed ? Color.rgb(104, 157, 222) : BLUE));
    }

}
