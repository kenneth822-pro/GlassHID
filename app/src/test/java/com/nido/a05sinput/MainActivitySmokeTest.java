package com.nido.a05sinput;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowDialog;

/** Drives the real UI under Robolectric: every screen, popup, layout, and mode must work. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class MainActivitySmokeTest {
    private ActivityController<MainActivity> controller;

    @Before public void setUp() {
        GlassHid.resetForTests();
    }

    @After public void tearDown() {
        if (controller != null) {
            try {
                controller.pause().stop().destroy();
            } catch (RuntimeException ignored) {
                // Already destroyed by the test.
            }
        }
        GlassHid.resetForTests();
    }

    private MainActivity launch(boolean setupDone, boolean bluetoothPermission) {
        Application app = RuntimeEnvironment.getApplication();
        if (bluetoothPermission) {
            shadowOf(app).grantPermissions(Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.POST_NOTIFICATIONS);
        }
        app.getSharedPreferences("controls", Context.MODE_PRIVATE).edit()
                .putBoolean("setup_done", setupDone).apply();
        controller = Robolectric.buildActivity(MainActivity.class).setup();
        idle();
        return controller.get();
    }

    private static void idle() {
        shadowOf(Looper.getMainLooper()).idle();
    }

    @SuppressWarnings("unchecked")
    private static List<View> windows() {
        try {
            Class<?> global = Class.forName("android.view.WindowManagerGlobal");
            Object instance = global.getMethod("getInstance").invoke(null);
            Field views = global.getDeclaredField("mViews");
            views.setAccessible(true);
            return new ArrayList<>((List<View>) views.get(instance));
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    /** The topmost visible view whose text starts with {@code prefix}. */
    private static View find(String prefix) {
        List<View> roots = windows();
        for (int i = roots.size() - 1; i >= 0; i--) {
            View found = find(roots.get(i), prefix);
            if (found != null) return found;
        }
        return null;
    }

    private static View find(View view, String prefix) {
        if (view.getVisibility() != View.VISIBLE) return null;
        if (view instanceof TextView && ((TextView) view).getText().toString().startsWith(prefix)) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = group.getChildCount() - 1; i >= 0; i--) {
                View found = find(group.getChildAt(i), prefix);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static <T extends View> T findType(Class<T> type) {
        for (View root : windows()) {
            T found = findType(root, type);
            if (found != null) return found;
        }
        return null;
    }

    private static <T extends View> T findType(View view, Class<T> type) {
        if (type.isInstance(view) && view.getVisibility() == View.VISIBLE) return type.cast(view);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                T found = findType(group.getChildAt(i), type);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void click(String prefix) {
        View view = find(prefix);
        assertNotNull("No view labelled " + prefix, view);
        assertTrue(prefix + " is not clickable", view.performClick());
        idle();
    }

    private static void dismissPopups() {
        // Back on every popup window by touching outside is not modelled; press the
        // activity's own controls instead, which dismiss Anki popups before rebuilding.
        idle();
    }

    private static void touch(View view, float fromX, float fromY, float toX, float toY, long holdMs) {
        long down = SystemClock.uptimeMillis();
        view.dispatchTouchEvent(MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN, fromX, fromY, 0));
        view.dispatchTouchEvent(MotionEvent.obtain(down, down + holdMs / 2, MotionEvent.ACTION_MOVE,
                (fromX + toX) / 2, (fromY + toY) / 2, 0));
        view.dispatchTouchEvent(MotionEvent.obtain(down, down + holdMs, MotionEvent.ACTION_UP, toX, toY, 0));
        idle();
    }

    private static String b64(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    // ------------------------------------------------------------------ Tests

    @Test public void firstLaunchWalksThroughTheSetupGuide() {
        MainActivity activity = launch(false, true);
        assertNotNull(ShadowDialog.getLatestDialog());
        assertTrue(ShadowDialog.getLatestDialog().isShowing());
        click("START");
        click("ALLOW");
        click("NEXT");
        click("ANDROID TABLET");
        click("MAKE PHONE VISIBLE");
        click("NEXT");
        click("FINISH");
        assertFalse(ShadowDialog.getLatestDialog().isShowing());
        assertTrue(activity.getSharedPreferences("controls", Context.MODE_PRIVATE)
                .getBoolean("setup_done", false));
    }

    @Test public void keyboardGamepadAndToolPopupsOpen() {
        launch(true, true);
        click("TRACKPAD ▾");
        click("LEFT");
        click("TOOLS ▾");
        click("F KEYS + NAV");
        click("F5");
        click("TOOLS ▾");
        click("SYSTEM");
        click("VOL +");
        click("SET");
        click("HAPTICS");
        click("SETUP GUIDE");
        ShadowDialog.getLatestDialog().dismiss();
        idle();
        click("PAIR");
        click("BLUETOOTH SETTINGS");
        click("GAMEPAD");
        click("KEYS");
        click("Q");
        click("Shift");
        click("A");
    }

    @Test public void ankiRemoteWorksInEveryLayoutAndHand() {
        MainActivity activity = launch(true, true);
        click("ANKI");
        GlassHid runtime = GlassHid.get(activity);
        assertEquals("Opening the remote switches Off to Bluetooth", GlassHid.MODE_BLUETOOTH, runtime.mode());
        for (int layout = 0; layout < 3; layout++) {
            for (int hand = 0; hand < 2; hand++) {
                if (find("FLIP") != null) click("FLIP");
                if (find("AGAIN") != null) click("AGAIN");
                if (find("GOOD") != null) click("GOOD");
                if (find("HARD") != null) click("HARD");
                if (find("EASY") != null) click("EASY");
                click("UNDO");
                click("REPLAY");
                click("MARK");
                click("MORE");
                click("SET ▾");
                click("HAND:");
            }
            click("SET ▾");
            click("LAYOUT:");
        }
        assertTrue("Back to the center layout", find("FLIP") != null);
        View scroll = find("SCROLL");
        assertNotNull(scroll);
        touch(scroll, 10, 10, 10, 300, 200);
        View tap = find("TAP");
        assertNotNull(tap);
        touch(tap, 20, 20, 20, 20, 50);
        touch(tap, 20, 20, 80, 90, 200);
        click("EXIT");
        assertNotNull("Keyboard is back", find("Space"));
    }

    @Test public void ankiDroidMoreActionsAndSettings() {
        MainActivity activity = launch(true, true);
        GlassHid.get(activity).setTargetSetting(GlassHid.TARGET_ANKIDROID);
        click("ANKI");
        click("MORE ▾");
        click("● RED");
        click("MORE ▾");
        click("BURY CARD");
        click("MORE ▾");
        click("REDO");
        click("SET ▾");
        click("TARGET:");
        click("TARGET:");
        click("ROTATION:");
        click("OLED BLACK:");
        click("SET ▾");
        click("STUDY BAR:");
        click("SET ▾");
        click("HAPTICS:");
        click("SOUND:");
        click("HOLD VOL UP:");
        click("HOLD VOL DOWN:");
        click("BACKGROUND INPUT:");
    }

    @Test public void studyToolsAndKeymapEditor() {
        MainActivity activity = launch(true, true);
        click("ANKI");
        click("SET ▾");
        click("STUDY TOOLS");
        for (String label : new String[]{"+", "−"}) {
            View step = find(label);
            assertNotNull(step);
            step.performClick();
            idle();
        }
        click("START FOCUS");
        click("STOP");
        click("NEW SESSION");
        click("CLEAR HISTORY");
        click("TAP AGAIN TO CLEAR");
        click("SET ▾");
        click("KEYS");
        click("EDITING:");
        click("EDITING:");
        View undoKey = find("Ctrl+Z");
        assertNotNull(undoKey);
        undoKey.performClick();
        idle();
        click("SHIFT");
        click("Y");
        click("SAVE");
        GlassHid runtime = GlassHid.get(activity);
        assertEquals(KeyChord.of(KeyChord.CTRL | KeyChord.SHIFT, 0x1C),
                runtime.commander.keymap().get(AnkiAction.UNDO, runtime.target()));
        assertTrue(runtime.prefs.getString("anki_keymap", "").contains("UNDO"));
        click("RESET ALL");
        assertEquals(KeyChord.of(KeyChord.CTRL, 0x1D),
                runtime.commander.keymap().get(AnkiAction.UNDO, runtime.target()));
        click("SET ▾");
        click("SETUP GUIDE");
        assertTrue(ShadowDialog.getLatestDialog().isShowing());
    }

    @Test public void volumeKeysStealthAndPocketMode() {
        MainActivity activity = launch(true, true);
        click("ANKI");
        GlassHid runtime = GlassHid.get(activity);
        long now = SystemClock.uptimeMillis();
        assertTrue(activity.dispatchKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_DOWN, 0)));
        assertTrue(activity.dispatchKeyEvent(new KeyEvent(now, now + 80, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_VOLUME_DOWN, 0)));
        assertTrue("Volume Down showed the answer", runtime.commander.isAnswerSide());
        assertTrue(activity.dispatchKeyEvent(new KeyEvent(now, now + 200, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP, 0)));
        assertTrue(activity.dispatchKeyEvent(new KeyEvent(now, now + 900, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP, 1)));
        assertTrue(activity.dispatchKeyEvent(new KeyEvent(now, now + 950, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_VOLUME_UP, 0)));
        idle();

        click("STEALTH");
        View surface = findType(View.class);
        assertNotNull(surface);
        click("EXIT");
        click("POCKET");
        assertTrue(runtime.pocket.isActive());
        click("POCKET ✓");
        assertFalse(runtime.pocket.isActive());
        click("EXIT");
        long later = SystemClock.uptimeMillis();
        assertFalse("Outside the remote the volume keys are the system's",
                activity.dispatchKeyEvent(new KeyEvent(later, later, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_DOWN, 0)));
    }

    @Test public void portraitRotationKeepsTheRemote() {
        MainActivity activity = launch(true, true);
        click("ANKI");
        RuntimeEnvironment.setQualifiers("+port");
        activity.onConfigurationChanged(activity.getResources().getConfiguration());
        idle();
        assertNotNull("Remote still showing after rotation", find("FLIP"));
        click("GOOD");
        click("SET ▾");
        click("LAYOUT:");
        click("SET ▾");
        click("LAYOUT:");
        assertNotNull(findType(SwipePadView.class));
        RuntimeEnvironment.setQualifiers("+land");
        activity.onConfigurationChanged(activity.getResources().getConfiguration());
        idle();
        assertNotNull(findType(SwipePadView.class));
    }

    @Test public void swipePadGradesAndHoldUndoes() {
        RuntimeEnvironment.getApplication().getSharedPreferences("controls", Context.MODE_PRIVATE)
                .edit().putInt("anki_layout_style", AnkiRemote.LAYOUT_SWIPE).apply();
        MainActivity activity = launch(true, true);
        GlassHid runtime = GlassHid.get(activity);
        click("ANKI");
        SwipePadView pad = findType(SwipePadView.class);
        assertNotNull(pad);
        pad.layout(0, 0, 800, 600);
        touch(pad, 400, 300, 400, 300, 60);
        assertTrue("Tap flips", runtime.commander.isAnswerSide());
        touch(pad, 600, 300, 200, 300, 200);
        assertFalse("Swipe left grades Again", runtime.commander.isAnswerSide());
        long down = SystemClock.uptimeMillis();
        pad.dispatchTouchEvent(MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN, 400, 300, 0));
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(800));
        pad.dispatchTouchEvent(MotionEvent.obtain(down, down + 800, MotionEvent.ACTION_UP, 400, 300, 0));
        idle();
    }

    @Test public void liveAnkiInfoShowsIntervalsAndResetsTheCardSide() {
        MainActivity activity = launch(true, true);
        GlassHid runtime = GlassHid.get(activity);
        runtime.setMode(GlassHid.MODE_USB);
        idle();
        click("ANKI");
        runtime.commander.perform(AnkiAction.FLIP);
        runtime.setLiveInfo(AnkiLiveInfo.parse("ANKI state=review deck=" + b64("Biology")
                + " new=3 learn=1 review=40 today=120 card=7 next=" + b64("<1m|<6m|<10m|4d")));
        idle();
        assertFalse("A new card resets to the question side", runtime.commander.isAnswerSide());
        assertNotNull(find("GOOD · 3  <10m"));
        assertNotNull(find("AGAIN · 1  <1m"));
        runtime.setLiveInfo(AnkiLiveInfo.parse("ANKI state=idle today=121"));
        idle();
        assertNotNull(find("GOOD · 3\n"));
    }

    @Test public void backgroundServiceStartsStopsAndHandlesActions() {
        MainActivity activity = launch(true, true);
        GlassHid runtime = GlassHid.get(activity);
        runtime.setMode(GlassHid.MODE_BLUETOOTH);
        ServiceController<HidService> service = Robolectric.buildService(HidService.class).create();
        assertTrue(HidService.isRunning());
        service.startCommand(0, 1);
        assertTrue(runtime.pocket.start());
        service.withIntent(new Intent(activity, HidService.class).setAction(HidService.ACTION_STOP_POCKET))
                .startCommand(0, 2);
        idle();
        assertFalse(runtime.pocket.isActive());
        service.withIntent(new Intent(activity, HidService.class).setAction(HidService.ACTION_DISCONNECT))
                .startCommand(0, 3);
        idle();
        assertEquals(GlassHid.MODE_OFF, runtime.mode());
        service.destroy();
        assertFalse(HidService.isRunning());
    }

    @Test public void worksWithoutBluetoothPermission() {
        launch(true, false);
        click("ANKI");
        click("POCKET");
        click("FLIP");
        click("EXIT");
        click("PAIR");
    }
}
