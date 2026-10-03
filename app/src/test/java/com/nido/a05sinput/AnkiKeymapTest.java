package com.nido.a05sinput;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.nido.a05sinput.AnkiKeymap.Target;
import org.junit.Test;

public class AnkiKeymapTest {
    @Test public void defaultsMatchStockShortcuts() {
        AnkiKeymap keymap = new AnkiKeymap();
        for (Target target : Target.values()) {
            assertEquals(KeyChord.key(0x2C), keymap.get(AnkiAction.FLIP, target));
            assertEquals(KeyChord.key(0x1E), keymap.get(AnkiAction.AGAIN, target));
            assertEquals(KeyChord.key(0x1F), keymap.get(AnkiAction.HARD, target));
            assertEquals(KeyChord.key(0x2C), keymap.get(AnkiAction.GOOD, target));
            assertEquals(KeyChord.key(0x21), keymap.get(AnkiAction.EASY, target));
            assertEquals(KeyChord.of(KeyChord.CTRL, 0x1D), keymap.get(AnkiAction.UNDO, target));
            assertEquals('r', keymap.get(AnkiAction.REPLAY, target).typedChar());
            assertEquals('*', keymap.get(AnkiAction.MARK, target).typedChar());
            assertEquals('-', keymap.get(AnkiAction.BURY_CARD, target).typedChar());
            assertEquals('=', keymap.get(AnkiAction.BURY_NOTE, target).typedChar());
            assertEquals('@', keymap.get(AnkiAction.SUSPEND_CARD, target).typedChar());
            assertEquals('!', keymap.get(AnkiAction.SUSPEND_NOTE, target).typedChar());
            assertEquals('e', keymap.get(AnkiAction.EDIT, target).typedChar());
            assertEquals("Ctrl+4", keymap.get(AnkiAction.FLAG_BLUE, target).label());
        }
        assertEquals('m', keymap.get(AnkiAction.MORE_MENU, Target.DESKTOP).typedChar());
        assertNull(keymap.get(AnkiAction.MORE_MENU, Target.ANKIDROID));
    }

    @Test public void everyActionHasADesktopDefault() {
        for (AnkiAction action : AnkiAction.values())
            assertNotNull(action.name(), AnkiKeymap.defaultChord(action, Target.DESKTOP));
    }

    @Test public void overridesArePerTargetAndRoundTrip() {
        AnkiKeymap keymap = new AnkiKeymap();
        keymap.set(AnkiAction.GOOD, Target.ANKIDROID, KeyChord.key(0x20));
        keymap.set(AnkiAction.UNDO, Target.DESKTOP, KeyChord.key(0x1D));
        assertTrue(keymap.isCustom(AnkiAction.GOOD, Target.ANKIDROID));
        assertFalse(keymap.isCustom(AnkiAction.GOOD, Target.DESKTOP));

        AnkiKeymap copy = AnkiKeymap.decode(keymap.encode());
        assertEquals(KeyChord.key(0x20), copy.get(AnkiAction.GOOD, Target.ANKIDROID));
        assertEquals(KeyChord.key(0x2C), copy.get(AnkiAction.GOOD, Target.DESKTOP));
        assertEquals(KeyChord.key(0x1D), copy.get(AnkiAction.UNDO, Target.DESKTOP));
    }

    @Test public void settingTheDefaultClearsTheOverride() {
        AnkiKeymap keymap = new AnkiKeymap();
        keymap.set(AnkiAction.EASY, Target.DESKTOP, KeyChord.key(0x22));
        keymap.set(AnkiAction.EASY, Target.DESKTOP, KeyChord.key(0x21));
        assertFalse(keymap.isCustom(AnkiAction.EASY, Target.DESKTOP));
        keymap.set(AnkiAction.EASY, Target.DESKTOP, KeyChord.key(0x22));
        keymap.resetAll(Target.DESKTOP);
        assertEquals("", keymap.encode());
    }

    @Test public void decodeSkipsDamagedEntries() {
        AnkiKeymap keymap = AnkiKeymap.decode("BOGUS.GOOD=0:30;DESKTOP.NOPE=0:30;DESKTOP.AGAIN=x;"
                + "DESKTOP.HARD=0:32;;=;ANKIDROID");
        assertEquals(KeyChord.key(0x20), keymap.get(AnkiAction.HARD, Target.DESKTOP));
        assertEquals(KeyChord.key(0x1E), keymap.get(AnkiAction.AGAIN, Target.DESKTOP));
        assertEquals("DESKTOP.HARD=0:32", keymap.encode());
    }
}
