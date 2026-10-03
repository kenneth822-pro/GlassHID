package com.nido.a05sinput;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

public class VolumeKeysTest {
    private final List<String> actions = new ArrayList<>();
    private final List<KeyChord> sent = new ArrayList<>();
    private AnkiAction longUp = AnkiAction.UNDO;
    private AnkiAction longDown = AnkiAction.FLAG_RED;
    private VolumeKeys keys;

    @Before public void setUp() {
        AnkiCommander commander = new AnkiCommander(new AnkiCommander.Output() {
            @Override public boolean canSend() { return true; }
            @Override public void send(KeyChord chord) { sent.add(chord); }
            @Override public AnkiKeymap.Target target() { return AnkiKeymap.Target.ANKIDROID; }
        }, new AnkiKeymap());
        keys = new VolumeKeys(commander, volumeUp -> volumeUp ? longUp : longDown,
                (action, longPress) -> actions.add(action + (longPress ? "!" : "")));
    }

    @Test public void shortPressesActOnRelease() {
        keys.press(false, 0);
        assertTrue(actions.isEmpty());
        keys.release(false, 100);
        keys.press(false, 1_000);
        keys.release(false, 1_080);
        keys.press(true, 2_000);
        keys.release(true, 2_090);
        assertEquals("[FLIP, GOOD, AGAIN]", actions.toString());
        assertEquals(3, sent.size());
    }

    @Test public void longPressRunsTheChosenActionOnce() {
        keys.press(true, 0);
        keys.press(true, 500);
        keys.press(true, 550);
        keys.release(true, 900);
        keys.press(false, 2_000);
        keys.press(false, 2_520);
        keys.release(false, 2_700);
        assertEquals("[UNDO!, FLAG_RED!]", actions.toString());
        assertEquals(KeyChord.of(KeyChord.CTRL, 0x1D), sent.get(0));
        assertEquals(KeyChord.of(KeyChord.CTRL, 0x1E), sent.get(1));
    }

    @Test public void withoutALongActionTheKeyActsImmediatelyAndIgnoresRepeats() {
        longUp = null;
        longDown = null;
        keys.press(false, 0);
        assertEquals("[FLIP]", actions.toString());
        keys.press(false, 500);
        keys.press(false, 550);
        keys.release(false, 700);
        keys.press(false, 1_000);
        assertEquals("[FLIP, GOOD]", actions.toString());
    }

    @Test public void pocketReleasesAndTimeoutsResolveTheLastKey() {
        keys.press(true, 0);
        keys.releaseLast(80);
        assertEquals("[AGAIN]", actions.toString());
        keys.press(false, 1_000);
        assertTrue(keys.isHeld());
        keys.tick(1_000 + PressGesture.RELEASE_GAP_MS);
        assertFalse(keys.isHeld());
        assertEquals("[AGAIN, FLIP]", actions.toString());
        keys.press(false, 5_000);
        keys.cancel();
        keys.tick(9_000);
        assertEquals(2, actions.size());
    }
}
