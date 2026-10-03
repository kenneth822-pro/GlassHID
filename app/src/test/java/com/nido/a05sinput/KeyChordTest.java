package com.nido.a05sinput;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.Test;

public class KeyChordTest {
    private static String text(String value) {
        return "TEXT " + Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    @Test public void typesUsLayoutCharacters() {
        assertEquals(KeyChord.key(0x1E), KeyChord.forChar('1'));
        assertEquals(KeyChord.of(KeyChord.SHIFT, 0x25), KeyChord.forChar('*'));
        assertEquals(KeyChord.of(KeyChord.SHIFT, 0x1F), KeyChord.forChar('@'));
        assertEquals(KeyChord.key(0x2C), KeyChord.forChar(' '));
        assertEquals(KeyChord.of(KeyChord.SHIFT, 0x04), KeyChord.forChar('A'));
        assertNull(KeyChord.forChar('é'));
    }

    @Test public void agreesWithLegacyKeyStrokeTable() {
        for (char c = 32; c < 127; c++) {
            KeyStroke legacy = KeyStroke.forChar(c);
            KeyChord chord = KeyChord.forChar(c);
            if (legacy == null) continue;
            assertEquals("char " + c, KeyChord.of(legacy.modifier, legacy.usage), chord);
            assertEquals("typed " + c, c, chord.typedChar());
        }
    }

    @Test public void labelsReadLikeShortcuts() {
        assertEquals("Space", KeyChord.key(0x2C).label());
        assertEquals("1", KeyChord.key(0x1E).label());
        assertEquals("*", KeyChord.of(KeyChord.SHIFT, 0x25).label());
        assertEquals("Shift+A", KeyChord.of(KeyChord.SHIFT, 0x04).label());
        assertEquals("Ctrl+Shift+Z", KeyChord.of(KeyChord.CTRL | KeyChord.SHIFT, 0x1D).label());
        assertEquals("Alt+F4", KeyChord.of(KeyChord.ALT, 0x3D).label());
    }

    @Test public void usbCommandsMatchTheExistingHelperProtocol() {
        assertEquals("KEY SPACE", KeyChord.key(0x2C).usbCommand());
        assertEquals(text("1"), KeyChord.key(0x1E).usbCommand());
        assertEquals(text("r"), KeyChord.key(0x15).usbCommand());
        assertEquals(text("*"), KeyChord.of(KeyChord.SHIFT, 0x25).usbCommand());
        assertEquals("HOTKEY CTRL+Z", KeyChord.of(KeyChord.CTRL, 0x1D).usbCommand());
        assertEquals("HOTKEY CTRL+SHIFT+Z", KeyChord.of(KeyChord.CTRL | KeyChord.SHIFT, 0x1D).usbCommand());
        assertEquals("HOTKEY CTRL+1", KeyChord.of(KeyChord.CTRL, 0x1E).usbCommand());
        assertEquals("HOTKEY CTRL+-", KeyChord.of(KeyChord.CTRL, 0x2D).usbCommand());
        assertEquals("KEY F5", KeyChord.key(0x3E).usbCommand());
        assertEquals("KEY ENTER", KeyChord.key(0x28).usbCommand());
    }

    @Test public void encodesAndRejectsGarbage() {
        KeyChord chord = KeyChord.of(KeyChord.CTRL | KeyChord.ALT, 0x0C);
        assertEquals(chord, KeyChord.decode(chord.encode()));
        assertNull(KeyChord.decode(null));
        assertNull(KeyChord.decode(""));
        assertNull(KeyChord.decode("1:"));
        assertNull(KeyChord.decode(":4"));
        assertNull(KeyChord.decode("x:4"));
        assertNull(KeyChord.decode("0:999"));
        assertNull(KeyChord.decode("99:4"));
    }
}
