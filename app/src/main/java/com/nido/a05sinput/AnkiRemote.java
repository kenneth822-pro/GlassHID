package com.nido.a05sinput;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The Anki remote screen: review buttons in three layouts, a tap/scroll column, stealth and
 * pocket modes, volume keys, the study bar, and settings. Every action goes through the
 * shared {@link AnkiCommander}, so counts and the flip/grade state stay consistent with
 * pocket mode.
 */
final class AnkiRemote implements GlassHid.Listener, StudyCoach.Listener,
        BluetoothHidController.Listener, ScrollPadListener.Host {
    interface Host {
        Activity activity();

        NeoUi neoUi();

        LinearLayout inputContainer();

        void setTopBarsVisible(boolean visible);

        void setRootDark(boolean dark);

        void exitToKeyboard();

        void showBluetoothPopup(View anchor, int xOffset);

        /** Switches an "Off" mode to Bluetooth so the remote has somewhere to send keys. */
        void ensureInputMode();

        void scroll(int amount);

        void move(int dx, int dy);

        void clickLeft();

        int scrollPercent();

        int pointerPercent();

        void haptic(View view, int kind);

        void openSetupGuide();
    }

    static final int LAYOUT_CENTER = 0;
    static final int LAYOUT_SPLIT = 1;
    static final int LAYOUT_SWIPE = 2;

    private final Host host;
    private final Activity activity;
    private final GlassHid runtime;
    private final SharedPreferences prefs;
    private final AnkiUi ui;
    private final AnkiSounds sounds = new AnkiSounds();
    private final VolumeKeys volumeKeys;
    private final AnkiStudyPanel studyPanel;
    private final KeymapEditor keymapEditor;
    private final List<PopupWindow> popups = new ArrayList<>();
    private final Map<Integer, Button> gradeButtons = new HashMap<>();
    private final Map<Integer, String> gradeLabels = new HashMap<>();

    private boolean active;
    private boolean stealth;
    private int layoutStyle;
    private int orientationMode;
    private boolean leftHanded;
    private boolean haptics;
    private boolean sound;
    private boolean statsBar;

    private TextView statusChip;
    private Button moreButton;
    private Button pocketButton;
    private TextView statsText;
    private StudyViews.GoalRing goalRing;

    AnkiRemote(Host host) {
        this.host = host;
        activity = host.activity();
        runtime = GlassHid.get(activity);
        prefs = runtime.prefs;
        layoutStyle = prefs.getInt("anki_layout_style", LAYOUT_CENTER);
        orientationMode = prefs.getInt("anki_orientation_mode", 0);
        leftHanded = prefs.getBoolean("anki_left_handed", false);
        haptics = prefs.getBoolean("anki_haptics", true);
        sound = prefs.getBoolean("anki_sound", true);
        statsBar = prefs.getBoolean("anki_stats_bar", true);
        ui = new AnkiUi(activity, host.neoUi(), prefs.getBoolean("anki_oled_mode", true));
        volumeKeys = new VolumeKeys(runtime.commander, runtime::longPressAction,
                (action, longPress) -> {
                    feedback(action);
                    if (longPress) toast(action.label);
                });
        studyPanel = new AnkiStudyPanel(activity, ui, runtime.coach, runtime);
        keymapEditor = new KeymapEditor(activity, ui, runtime);
    }

    boolean isActive() {
        return active;
    }

    // ---------------------------------------------------------------- Lifecycle

    void show(boolean announce) {
        boolean entering = !active;
        active = true;
        stealth = false;
        if (entering) {
            runtime.commander.questionShown();
            runtime.addListener(this);
            runtime.coach.addListener(this);
            runtime.bluetooth().addListener(this);
            runtime.coach.setEngaged("remote", true);
        }
        host.ensureInputMode();
        applyOrientation();
        render();
        if (announce) toast("Anki remote");
    }

    void hide() {
        if (!active) return;
        active = false;
        stealth = false;
        dismissPopups();
        volumeKeys.cancel();
        runtime.removeListener(this);
        runtime.coach.removeListener(this);
        runtime.bluetooth().removeListener(this);
        runtime.coach.setEngaged("remote", false);
        clearViewRefs();
        host.setRootDark(false);
    }

    void onPause() {
        volumeKeys.cancel();
        if (active) {
            runtime.coach.setEngaged("remote", false);
            runtime.bluetooth().removeListener(this);
        }
    }

    void onResume() {
        if (active) {
            runtime.coach.setEngaged("remote", true);
            // The controller may have been recreated while the app was in the background.
            runtime.bluetooth().addListener(this);
            refresh();
        }
    }

    void release() {
        hide();
        sounds.release();
    }

    void onConfigurationChanged() {
        if (active) render();
    }

    /** Volume keys drive reviews while the remote is on screen. */
    boolean onKeyEvent(KeyEvent event) {
        if (!active) return false;
        int code = event.getKeyCode();
        if (code != KeyEvent.KEYCODE_VOLUME_UP && code != KeyEvent.KEYCODE_VOLUME_DOWN) return false;
        boolean up = code == KeyEvent.KEYCODE_VOLUME_UP;
        if (event.getAction() == KeyEvent.ACTION_DOWN) volumeKeys.press(up, event.getEventTime());
        else if (event.getAction() == KeyEvent.ACTION_UP) volumeKeys.release(up, event.getEventTime());
        return true;
    }

    /** Requested orientation for the remote. */
    void applyOrientation() {
        int requested = orientationMode == 1 ? ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                : orientationMode == 2 ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                : ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR;
        try {
            activity.setRequestedOrientation(requested);
        } catch (RuntimeException ignored) {
        }
    }

    // ---------------------------------------------------------------- Listeners

    @Override public void onRuntimeChanged() {
        refresh();
    }

    @Override public void onCoachChanged() {
        refreshStats();
    }

    @Override public void onCoachAlert(String message) {
        if (active) Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
    }

    @Override public void onStateChanged() {
        refresh();
    }

    @Override public void onInputConnected(String hostName) {
        refresh();
    }

    // ---------------------------------------------------------------- Rendering

    private void render() {
        dismissPopups();
        clearViewRefs();
        host.setTopBarsVisible(false);
        ui.oled = prefs.getBoolean("anki_oled_mode", true);
        host.setRootDark(ui.oled || stealth);
        LinearLayout container = host.inputContainer();
        if (container == null) return;
        container.removeAllViews();
        container.addView(stealth ? buildStealth() : buildRemote(), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));
        refresh();
    }

    private boolean portrait() {
        return activity.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
    }

    private View buildRemote() {
        boolean portrait = portrait();
        LinearLayout outer = ui.column();
        outer.setBackgroundColor(ui.background());

        LinearLayout header = ui.row();
        statusChip = ui.label("", portrait ? 10 : 11, Palette.YELLOW);
        statusChip.setGravity(Gravity.CENTER_VERTICAL);
        statusChip.setPadding(ui.dp(10), 0, ui.dp(6), 0);
        statusChip.setMaxLines(2);
        statusChip.setLineSpacing(0, 0.9f);
        statusChip.setEllipsize(TextUtils.TruncateAt.END);
        statusChip.setOnClickListener(v -> host.showBluetoothPopup(v, 0));
        header.addView(statusChip, ui.slot(portrait ? 2.2f : 2.6f));
        if (!portrait) addUtilityButtons(header, 11);
        int headerText = portrait ? 10 : 11;
        addHeaderButton(header, "STEALTH", Palette.CORAL, headerText, v -> {
            stealth = true;
            render();
        });
        pocketButton = addHeaderButton(header, "POCKET", Palette.GREEN, headerText, v -> togglePocket());
        addHeaderButton(header, "SET ▾", Palette.BLUE, headerText, this::showSettings);
        addHeaderButton(header, "EXIT", Palette.CORAL, headerText, v -> host.exitToKeyboard());
        outer.addView(header, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ui.dp(42)));

        if (portrait) {
            LinearLayout tools = ui.row();
            addUtilityButtons(tools, 11);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, ui.dp(42));
            params.topMargin = ui.dp(4);
            outer.addView(tools, params);
        }

        if (statsBar) {
            LinearLayout stats = ui.row();
            stats.setPadding(ui.dp(6), 0, ui.dp(6), 0);
            goalRing = new StudyViews.GoalRing(activity,
                    ui.oled ? Color.rgb(45, 45, 45) : Color.rgb(210, 205, 195), Palette.GREEN);
            stats.addView(goalRing, new LinearLayout.LayoutParams(ui.dp(22), ui.dp(22)));
            statsText = ui.label("", 11, Palette.BLUE);
            statsText.setSingleLine(true);
            statsText.setEllipsize(TextUtils.TruncateAt.END);
            statsText.setPadding(ui.dp(8), 0, 0, 0);
            stats.addView(statsText, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1));
            stats.setOnClickListener(this::showStudy);
            stats.setContentDescription("Study statistics");
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, ui.dp(28));
            params.topMargin = ui.dp(4);
            outer.addView(stats, params);
        }

        LinearLayout body = ui.row();
        body.setGravity(Gravity.NO_GRAVITY);
        LinearLayout.LayoutParams columnParams = new LinearLayout.LayoutParams(
                ui.dp(portrait ? 64 : 76), LinearLayout.LayoutParams.MATCH_PARENT);
        View pad = buildReviewPad(portrait);
        LinearLayout.LayoutParams padParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1);
        if (leftHanded) {
            columnParams.rightMargin = ui.dp(6);
            body.addView(buildPointerColumn(), columnParams);
            body.addView(pad, padParams);
        } else {
            columnParams.leftMargin = ui.dp(6);
            body.addView(pad, padParams);
            body.addView(buildPointerColumn(), columnParams);
        }
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1);
        bodyParams.topMargin = ui.dp(6);
        outer.addView(body, bodyParams);
        return outer;
    }

    private void addUtilityButtons(LinearLayout row, int textSize) {
        addHeaderButton(row, "UNDO", Palette.PAPER, textSize, v -> perform(AnkiAction.UNDO));
        addHeaderButton(row, "REPLAY", Palette.YELLOW, textSize, v -> perform(AnkiAction.REPLAY));
        addHeaderButton(row, "MARK", Palette.GREEN, textSize, v -> perform(AnkiAction.MARK));
        moreButton = addHeaderButton(row, "MORE", Palette.BLUE, textSize, this::onMore);
    }

    private Button addHeaderButton(LinearLayout row, String label, int color, int textSize, View.OnClickListener click) {
        Button button = ui.topButton(label, color);
        button.setTextSize(textSize);
        button.setOnClickListener(click);
        row.addView(button, ui.slot(1));
        return button;
    }

    private View buildReviewPad(boolean portrait) {
        int match = LinearLayout.LayoutParams.MATCH_PARENT;
        if (layoutStyle == LAYOUT_SWIPE) {
            SwipePadView swipe = new SwipePadView(activity, new SwipePadView.Callback() {
                @Override public void onGesture(SwipeClassifier.Gesture gesture) {
                    switch (gesture) {
                        case TAP: perform(AnkiAction.FLIP); break;
                        case LEFT: perform(AnkiAction.AGAIN); break;
                        case RIGHT: perform(AnkiAction.GOOD); break;
                        case UP: perform(AnkiAction.EASY); break;
                        case DOWN: perform(AnkiAction.HARD); break;
                        default: break;
                    }
                }

                @Override public void onHold() {
                    perform(AnkiAction.UNDO);
                    toast("Undo");
                }
            }, ui.oled ? Palette.BLUE : Palette.INK);
            ui.surface(swipe, false);
            return swipe;
        }

        LinearLayout pad = ui.column();
        if (layoutStyle == LAYOUT_CENTER || portrait) {
            Button flip = gradeButton(0, "FLIP / SPACE\n(Vol Down)", Palette.YELLOW, portrait ? 22 : 18);
            pad.addView(flip, ui.cell(match, 0, 1.4f));
            LinearLayout primary = ui.row();
            addGrade(primary, leftHanded ? 3 : 1);
            addGrade(primary, leftHanded ? 1 : 3);
            pad.addView(primary, new LinearLayout.LayoutParams(match, 0, 1f));
            LinearLayout secondary = ui.row();
            addGrade(secondary, leftHanded ? 4 : 2);
            addGrade(secondary, leftHanded ? 2 : 4);
            pad.addView(secondary, new LinearLayout.LayoutParams(match, 0, 0.6f));
            return pad;
        }

        pad.setOrientation(LinearLayout.HORIZONTAL);
        Button flip = gradeButton(0, "FLIP\nSPACE\n\n(Vol Down)", Palette.YELLOW, 20);
        LinearLayout ratings = ui.column();
        ratings.addView(gradeButton(3, null, Palette.GREEN, 17), ui.cell(match, 0, 1f));
        ratings.addView(gradeButton(1, null, Palette.CORAL, 17), ui.cell(match, 0, 1f));
        LinearLayout secondary = ui.row();
        addGrade(secondary, leftHanded ? 4 : 2);
        addGrade(secondary, leftHanded ? 2 : 4);
        ratings.addView(secondary, new LinearLayout.LayoutParams(match, 0, 0.6f));
        if (leftHanded) {
            pad.addView(ratings, new LinearLayout.LayoutParams(0, match, 1f));
            pad.addView(flip, ui.cell(0, match, 1.1f));
        } else {
            pad.addView(flip, ui.cell(0, match, 1.1f));
            pad.addView(ratings, new LinearLayout.LayoutParams(0, match, 1f));
        }
        return pad;
    }

    private void addGrade(LinearLayout row, int ease) {
        int color = ease == 1 ? Palette.CORAL : ease == 2 ? Palette.PAPER : ease == 3 ? Palette.GREEN : Palette.BLUE;
        row.addView(gradeButton(ease, null, color, 15), ui.cell(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
    }

    /** ease 0 is the Flip button; 1-4 are grades whose labels show live intervals. */
    private Button gradeButton(int ease, String flipLabel, int color, int textSize) {
        String label = ease == 0 ? flipLabel : gradeLabel(ease);
        Button button = ui.mainButton(label, color);
        button.setTextSize(textSize);
        AnkiAction action = ease == 0 ? AnkiAction.FLIP : ease == 1 ? AnkiAction.AGAIN
                : ease == 2 ? AnkiAction.HARD : ease == 3 ? AnkiAction.GOOD : AnkiAction.EASY;
        button.setOnClickListener(v -> perform(action));
        if (ease > 0) {
            gradeButtons.put(ease, button);
            gradeLabels.put(ease, label);
        }
        return button;
    }

    private static String gradeLabel(int ease) {
        switch (ease) {
            case 1: return "AGAIN · 1\n(Vol Up)";
            case 2: return "HARD · 2";
            case 3: return "GOOD · 3\n(Vol Down)";
            default: return "EASY · 4";
        }
    }

    private View buildPointerColumn() {
        LinearLayout column = ui.column();
        TextView tapPad = ui.label("TAP\n◎\ndrag\nto aim", 11, Palette.BLUE);
        tapPad.setGravity(Gravity.CENTER);
        ui.surface(tapPad, false);
        tapPad.setOnTouchListener(new PointerPadListener(this));
        tapPad.setContentDescription("Tap to click at the pointer, drag to move it");
        column.addView(tapPad, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 0.36f));

        TextView scroll = ui.label("SCROLL\n\n▲\n\n↕\n\n▼", 14, Palette.BLUE);
        scroll.setGravity(Gravity.CENTER);
        ui.surface(scroll, false);
        scroll.setOnTouchListener(new ScrollPadListener(this));
        scroll.setContentDescription("Scroll the card");
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        params.topMargin = ui.dp(6);
        column.addView(scroll, params);
        return column;
    }

    private View buildStealth() {
        LinearLayout root = ui.column();
        root.setBackgroundColor(Color.BLACK);
        LinearLayout top = ui.row();
        top.setPadding(ui.dp(12), ui.dp(4), ui.dp(12), ui.dp(4));
        TextView hint = new TextView(activity);
        hint.setText("● STEALTH (Vol Down: Flip/Good · Vol Up: Again · Hold: shortcut · Swipe: Scroll · Double-tap: Tap)");
        hint.setTextSize(10);
        hint.setTextColor(Color.rgb(40, 75, 45));
        top.addView(hint, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        Button exit = new Button(activity);
        exit.setText("EXIT");
        exit.setTextSize(11);
        exit.setTextColor(Color.rgb(130, 130, 130));
        exit.setMinHeight(0);
        exit.setMinWidth(0);
        exit.setStateListAnimator(null);
        exit.setBackground(host.neoUi().rounded(Color.rgb(18, 18, 18)));
        exit.setOnClickListener(v -> {
            stealth = false;
            render();
        });
        top.addView(exit, new LinearLayout.LayoutParams(ui.dp(64), ui.dp(34)));
        root.addView(top, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ui.dp(42)));

        View surface = new View(activity);
        surface.setBackgroundColor(Color.BLACK);
        surface.setContentDescription("Stealth surface: swipe to scroll, double-tap to click");
        surface.setOnTouchListener(new ScrollPadListener(this, () -> {
            host.clickLeft();
            haptic(HapticFeedbackConstants.LONG_PRESS);
        }));
        root.addView(surface, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        return root;
    }

    // ---------------------------------------------------------------- Refresh

    private void refresh() {
        if (!active || stealth) return;
        refreshStatus();
        refreshStats();
        refreshGradeLabels();
    }

    private void refreshStatus() {
        if (statusChip == null) return;
        int mode = runtime.mode();
        boolean live;
        String line1;
        String line2;
        AnkiLiveInfo info = runtime.liveInfo();
        if (mode == GlassHid.MODE_BLUETOOTH) {
            BluetoothHidController hid = runtime.bluetooth();
            live = hid.isInputLive();
            line1 = "BT · " + hid.shortStatus();
            String hostName = hid.currentHost() == null ? "" : hid.safeName(hid.currentHost()) + " · ";
            line2 = hostName + targetLabel();
        } else if (mode == GlassHid.MODE_USB) {
            live = runtime.usbConnected();
            line1 = "USB · " + (live ? "PC CONNECTED" : "WAITING FOR PC");
            line2 = info != null && info.reviewing() && !info.deck.isEmpty() ? info.deck : targetLabel();
        } else {
            live = false;
            line1 = "OFF · TAP TO CONNECT";
            line2 = targetLabel();
        }
        statusChip.setText("● " + line1 + "\n" + line2);
        ui.chip(statusChip, live);
        if (moreButton != null)
            moreButton.setText(runtime.target() == AnkiKeymap.Target.ANKIDROID ? "MORE ▾" : "MORE");
        if (pocketButton != null) pocketButton.setText(runtime.pocket.isActive() ? "POCKET ✓" : "POCKET");
    }

    private String targetLabel() {
        String app = runtime.target() == AnkiKeymap.Target.ANKIDROID ? "ANKIDROID" : "ANKI DESKTOP";
        return runtime.targetSetting() == GlassHid.TARGET_AUTO ? app + " (AUTO)" : app;
    }

    private void refreshStats() {
        if (statsText == null) return;
        StudyCoach coach = runtime.coach;
        ReviewSession session = coach.session();
        StringBuilder text = new StringBuilder();
        AnkiLiveInfo info = runtime.liveInfo();
        if (info != null && info.reviewing()) {
            text.append(info.newCount).append(" new · ").append(info.learnCount).append(" lrn · ")
                    .append(info.reviewCount).append(" due │ ");
        }
        if (session.total() > 0) {
            text.append("Session ").append(session.total());
            double pace = session.cardsPerMinute();
            if (pace > 0) text.append(" · ").append(String.format(Locale.US, "%.1f/min", pace));
            text.append(" · Again ").append(Math.round(session.againRate() * 100)).append('%');
            text.append(" · ").append(minutes(session.activeMs())).append(" │ ");
        }
        int today = info != null && info.reviewedToday >= 0 ? info.reviewedToday : coach.todayTotal();
        text.append("Today ").append(today);
        if (coach.goal() > 0) text.append('/').append(coach.goal());
        int streak = coach.streak();
        if (streak > 0) text.append(" · 🔥").append(streak);
        if (coach.focusPhase() == FocusTimer.Phase.FOCUS) text.append(" │ Focus ").append(clock(coach.focusRemainingMs()));
        else if (coach.focusPhase() == FocusTimer.Phase.BREAK) text.append(" │ BREAK ").append(clock(coach.focusRemainingMs()));
        if (session.total() == 0 && coach.focusPhase() == FocusTimer.Phase.OFF) text.append(" · tap for study tools");
        statsText.setText(text.toString());
        if (goalRing != null) goalRing.setFraction(coach.goal() > 0 ? today / (float) coach.goal() : 0f);
    }

    private void refreshGradeLabels() {
        AnkiLiveInfo info = runtime.liveInfo();
        for (Map.Entry<Integer, Button> entry : gradeButtons.entrySet()) {
            String base = gradeLabels.get(entry.getKey());
            String interval = info != null && info.reviewing() ? info.nextInterval(entry.getKey()) : "";
            entry.getValue().setText(interval.isEmpty() ? base : withInterval(base, interval));
        }
    }

    /** "GOOD · 3\n(Vol Down)" + "4d" → "GOOD · 3  4d\n(Vol Down)". */
    static String withInterval(String base, String interval) {
        int newline = base.indexOf('\n');
        return newline < 0 ? base + "  " + interval
                : base.substring(0, newline) + "  " + interval + base.substring(newline);
    }

    private static String minutes(long ms) {
        long minutes = ms / 60_000;
        return minutes < 60 ? minutes + "m" : (minutes / 60) + "h " + (minutes % 60) + "m";
    }

    private static String clock(long ms) {
        long seconds = (ms + 999) / 1000;
        return String.format(Locale.US, "%d:%02d", seconds / 60, seconds % 60);
    }

    // ---------------------------------------------------------------- Actions

    private void perform(AnkiAction action) {
        runtime.commander.perform(action);
        feedback(action);
    }

    private void feedback(AnkiAction action) {
        boolean modern = Build.VERSION.SDK_INT >= 30;
        int kind;
        switch (action) {
            case GOOD: kind = modern ? HapticFeedbackConstants.CONFIRM : HapticFeedbackConstants.VIRTUAL_KEY; break;
            case AGAIN: kind = modern ? HapticFeedbackConstants.REJECT : HapticFeedbackConstants.LONG_PRESS; break;
            case HARD: kind = HapticFeedbackConstants.LONG_PRESS; break;
            case EASY: kind = HapticFeedbackConstants.CLOCK_TICK; break;
            case FLIP: kind = HapticFeedbackConstants.KEYBOARD_TAP; break;
            default: kind = HapticFeedbackConstants.VIRTUAL_KEY; break;
        }
        haptic(kind);
        if (sound) sounds.play(action);
    }

    private void haptic(int kind) {
        if (!haptics) return;
        View decor = activity.getWindow().getDecorView();
        decor.performHapticFeedback(kind, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
    }

    private void onMore(View anchor) {
        if (runtime.target() == AnkiKeymap.Target.ANKIDROID) showMoreActions(anchor);
        else perform(AnkiAction.MORE_MENU);
    }

    private void togglePocket() {
        if (runtime.pocket.isActive()) {
            runtime.pocket.stop();
            toast("Pocket mode off");
        } else if (runtime.mode() != GlassHid.MODE_BLUETOOTH) {
            toast("Pocket mode works over Bluetooth. Switch the mode to BT first.");
        } else if (runtime.pocket.start()) {
            toast("Pocket mode on: lock the screen and keep reviewing with the volume keys. "
                    + "It ends after 20 minutes without a press.");
        } else {
            toast("Pocket mode could not start on this phone.");
        }
        refreshStatus();
    }

    // ---------------------------------------------------------------- Popups

    private void showMoreActions(View anchor) {
        dismissPopups();
        LinearLayout card = ui.column();
        card.setPadding(ui.dp(8), ui.dp(8), ui.dp(8), ui.dp(6));
        ui.panel(card, Palette.BLUE);
        LinearLayout bury = ui.row();
        addAction(bury, "BURY CARD", Palette.PAPER, AnkiAction.BURY_CARD);
        addAction(bury, "SUSPEND CARD", Palette.YELLOW, AnkiAction.SUSPEND_CARD);
        card.addView(bury, ui.rowParams(44));
        LinearLayout note = ui.row();
        addAction(note, "BURY NOTE", Palette.PAPER, AnkiAction.BURY_NOTE);
        addAction(note, "SUSPEND NOTE", Palette.YELLOW, AnkiAction.SUSPEND_NOTE);
        card.addView(note, ui.rowParams(44));
        LinearLayout flags = ui.row();
        addAction(flags, "● RED", Palette.FLAG_RED, AnkiAction.FLAG_RED);
        addAction(flags, "● ORANGE", Palette.FLAG_ORANGE, AnkiAction.FLAG_ORANGE);
        addAction(flags, "● GREEN", Palette.GREEN, AnkiAction.FLAG_GREEN);
        addAction(flags, "● BLUE", Palette.BLUE, AnkiAction.FLAG_BLUE);
        card.addView(flags, ui.rowParams(44));
        LinearLayout edit = ui.row();
        addAction(edit, "EDIT NOTE", Palette.BLUE, AnkiAction.EDIT);
        addAction(edit, "REDO", Palette.PAPER, AnkiAction.REDO);
        card.addView(edit, ui.rowParams(44));
        show(ui.popup(card, 340, 330), anchor);
    }

    private void addAction(LinearLayout row, String label, int color, AnkiAction action) {
        Button button = ui.topButton(label, color);
        button.setOnClickListener(v -> {
            perform(action);
            dismissPopups();
        });
        row.addView(button, ui.slot(1));
    }

    private void showSettings(View anchor) {
        dismissPopups();
        boolean portrait = portrait();
        LinearLayout card = ui.column();
        card.setPadding(ui.dp(8), ui.dp(8), ui.dp(8), ui.dp(4));
        ui.panel(card, Palette.BLUE);

        Button target = setting(card, targetSettingLabel(), Palette.CORAL);
        target.setOnClickListener(v -> {
            int next = (runtime.targetSetting() + 1) % 3;
            runtime.setTargetSetting(next);
            target.setText(targetSettingLabel());
        });

        Button layout = setting(card, layoutLabel(portrait), Palette.BLUE);
        layout.setOnClickListener(v -> {
            layoutStyle = (layoutStyle + 1) % 3;
            prefs.edit().putInt("anki_layout_style", layoutStyle).apply();
            render();
        });

        Button hand = setting(card, "HAND: " + (leftHanded ? "LEFT" : "RIGHT"), Palette.BLUE);
        hand.setOnClickListener(v -> {
            leftHanded = !leftHanded;
            prefs.edit().putBoolean("anki_left_handed", leftHanded).apply();
            render();
        });

        Button rotation = setting(card, rotationLabel(), Palette.BLUE);
        rotation.setOnClickListener(v -> {
            orientationMode = (orientationMode + 1) % 3;
            prefs.edit().putInt("anki_orientation_mode", orientationMode).apply();
            rotation.setText(rotationLabel());
            applyOrientation();
        });

        Button oled = setting(card, "OLED BLACK: " + onOff(ui.oled), Palette.GREEN);
        oled.setOnClickListener(v -> {
            prefs.edit().putBoolean("anki_oled_mode", !ui.oled).apply();
            render();
        });

        Button stats = setting(card, "STUDY BAR: " + onOff(statsBar), Palette.GREEN);
        stats.setOnClickListener(v -> {
            statsBar = !statsBar;
            prefs.edit().putBoolean("anki_stats_bar", statsBar).apply();
            render();
        });

        Button hapticsButton = setting(card, "HAPTICS: " + onOff(haptics), Palette.GREEN);
        hapticsButton.setOnClickListener(v -> {
            haptics = !haptics;
            prefs.edit().putBoolean("anki_haptics", haptics).apply();
            hapticsButton.setText("HAPTICS: " + onOff(haptics));
            haptic(HapticFeedbackConstants.KEYBOARD_TAP);
        });

        Button soundButton = setting(card, "SOUND: " + onOff(sound), Palette.YELLOW);
        soundButton.setOnClickListener(v -> {
            sound = !sound;
            prefs.edit().putBoolean("anki_sound", sound).apply();
            soundButton.setText("SOUND: " + onOff(sound));
            if (sound) sounds.play(AnkiAction.FLIP);
        });

        Button holdUp = setting(card, longPressLabel(true), Palette.YELLOW);
        holdUp.setOnClickListener(v -> {
            runtime.setLongPressAction(true, nextLongPress(runtime.longPressAction(true)));
            holdUp.setText(longPressLabel(true));
        });
        Button holdDown = setting(card, longPressLabel(false), Palette.YELLOW);
        holdDown.setOnClickListener(v -> {
            runtime.setLongPressAction(false, nextLongPress(runtime.longPressAction(false)));
            holdDown.setText(longPressLabel(false));
        });

        Button background = setting(card, "BACKGROUND INPUT: " + onOff(runtime.backgroundEnabled()), Palette.GREEN);
        background.setOnClickListener(v -> {
            runtime.setBackgroundEnabled(!runtime.backgroundEnabled());
            background.setText("BACKGROUND INPUT: " + onOff(runtime.backgroundEnabled()));
        });

        Button study = setting(card, "STUDY TOOLS…", Palette.CORAL);
        study.setOnClickListener(v -> showStudy(anchor));
        Button keys = setting(card, "KEYS…", Palette.PAPER);
        keys.setOnClickListener(v -> {
            dismissPopups();
            show(keymapEditor.build(), anchor);
        });
        Button guide = setting(card, "SETUP GUIDE…", Palette.PAPER);
        guide.setOnClickListener(v -> {
            dismissPopups();
            host.openSetupGuide();
        });
        show(ui.popup(card, 260, portrait ? 520 : 330), anchor);
    }

    private void showStudy(View anchor) {
        dismissPopups();
        show(studyPanel.build(), anchor);
    }

    private Button setting(LinearLayout card, String label, int color) {
        Button button = ui.topButton(label, color);
        button.setTextSize(12);
        card.addView(button, ui.rowParams(42));
        return button;
    }

    private String targetSettingLabel() {
        int setting = runtime.targetSetting();
        if (setting == GlassHid.TARGET_DESKTOP) return "TARGET: ANKI DESKTOP";
        if (setting == GlassHid.TARGET_ANKIDROID) return "TARGET: ANKIDROID";
        return "TARGET: AUTO · " + (runtime.target() == AnkiKeymap.Target.ANKIDROID ? "ANKIDROID" : "DESKTOP");
    }

    private String layoutLabel(boolean portrait) {
        if (layoutStyle == LAYOUT_SWIPE) return "LAYOUT: SWIPE";
        if (layoutStyle == LAYOUT_SPLIT) return portrait ? "LAYOUT: SPLIT (LANDSCAPE)" : "LAYOUT: SPLIT";
        return "LAYOUT: CENTER";
    }

    private String rotationLabel() {
        return orientationMode == 1 ? "ROTATION: PORTRAIT"
                : orientationMode == 2 ? "ROTATION: LANDSCAPE" : "ROTATION: AUTO";
    }

    private String longPressLabel(boolean volumeUp) {
        AnkiAction action = runtime.longPressAction(volumeUp);
        return (volumeUp ? "HOLD VOL UP: " : "HOLD VOL DOWN: ") +
                (action == null ? "OFF" : action.label.toUpperCase(Locale.US));
    }

    private static AnkiAction nextLongPress(AnkiAction current) {
        AnkiAction[] options = GlassHid.LONG_PRESS_OPTIONS;
        for (int i = 0; i < options.length; i++)
            if (options[i] == current) return options[(i + 1) % options.length];
        return options[0];
    }

    private static String onOff(boolean value) {
        return value ? "ON" : "OFF";
    }

    private void show(PopupWindow popup, View anchor) {
        popups.add(popup);
        popup.setOnDismissListener(() -> popups.remove(popup));
        try {
            popup.showAsDropDown(anchor, 0, ui.dp(4));
        } catch (RuntimeException e) {
            popups.remove(popup);
        }
    }

    private void dismissPopups() {
        for (PopupWindow popup : new ArrayList<>(popups)) {
            try {
                popup.dismiss();
            } catch (RuntimeException ignored) {
            }
        }
        popups.clear();
    }

    private void clearViewRefs() {
        statusChip = null;
        moreButton = null;
        pocketButton = null;
        statsText = null;
        goalRing = null;
        gradeButtons.clear();
        gradeLabels.clear();
    }

    private void toast(String message) {
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
    }

    // ---------------------------------------------------------------- Touch surfaces

    @Override public int dp(int value) {
        return ui.dp(value);
    }

    @Override public int scrollPercent() {
        return host.scrollPercent();
    }

    @Override public void scroll(int amount) {
        host.scroll(amount);
    }

    @Override public void haptic(View view, int kind) {
        host.haptic(view, kind);
    }

    @Override public void scrollVisual(View view, boolean pressed) {
        if (stealth) view.setBackgroundColor(Color.BLACK);
        else ui.surface(view, pressed);
    }

    @Override public int pointerPercent() {
        return host.pointerPercent();
    }

    @Override public void move(int dx, int dy) {
        host.move(dx, dy);
    }

    @Override public void clickLeft() {
        host.clickLeft();
    }

    /**
     * Android re-evaluates hover only when the pointer moves, so after a scroll the image now
     * under the tablet's pointer would not react as it does on a PC. A 1 px nudge and back
     * applies the card's hover styling (for example an image zoom).
     */
    @Override public void scrollFinished() {
        if (runtime.mode() != GlassHid.MODE_BLUETOOTH || runtime.target() != AnkiKeymap.Target.ANKIDROID) return;
        host.move(1, 0);
        runtime.handler.postDelayed(() -> host.move(-1, 0), 45);
    }
}
