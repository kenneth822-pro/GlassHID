package com.nido.a05sinput;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** One key press with optional modifiers, as a HID usage plus a modifier bitmask. */
final class KeyChord {
    static final int CTRL = 0x01;
    static final int SHIFT = 0x02;
    static final int ALT = 0x04;
    static final int WIN = 0x08;

    final int modifiers;
    final int usage;

    private KeyChord(int modifiers, int usage) {
        this.modifiers = modifiers & 0x0F;
        this.usage = usage;
    }

    static KeyChord of(int modifiers, int usage) {
        return new KeyChord(modifiers, usage);
    }

    static KeyChord key(int usage) {
        return new KeyChord(0, usage);
    }

    /** The chord that types {@code c} on a US layout, or null if the remote cannot type it. */
    static KeyChord forChar(char c) {
        for (HidKeys.Key key : HidKeys.all()) {
            if (key.base == c) return new KeyChord(0, key.usage);
            if (key.shifted == c && key.shifted != key.base) return new KeyChord(SHIFT, key.usage);
        }
        return null;
    }

    /** The character this chord types on a US layout, or 0 when it is a shortcut. */
    char typedChar() {
        HidKeys.Key key = HidKeys.forUsage(usage);
        if (key == null || key.base == 0) return 0;
        if (modifiers == 0) return key.base;
        if (modifiers == SHIFT) return key.shifted;
        return 0;
    }

    /** Human label such as "1", "*", "Space", or "Ctrl+Shift+Z". */
    String label() {
        HidKeys.Key key = HidKeys.forUsage(usage);
        String keyLabel = key == null ? String.format("0x%02X", usage) : key.label;
        char typed = typedChar();
        if (typed != 0 && typed != ' ' && modifiers == SHIFT && !Character.isLetter(typed))
            return String.valueOf(typed);
        StringBuilder out = new StringBuilder();
        if ((modifiers & CTRL) != 0) out.append("Ctrl+");
        if ((modifiers & SHIFT) != 0) out.append("Shift+");
        if ((modifiers & ALT) != 0) out.append("Alt+");
        if ((modifiers & WIN) != 0) out.append("Win+");
        return out.append(keyLabel).toString();
    }

    /**
     * The line the Windows USB helper executes for this chord: typed characters go as
     * Unicode text, lone special keys as KEY, and shortcuts as HOTKEY.
     */
    String usbCommand() {
        HidKeys.Key key = HidKeys.forUsage(usage);
        if (key == null) return null;
        char typed = typedChar();
        if (typed != 0 && typed != ' ') {
            return "TEXT " + Base64.getEncoder().encodeToString(
                    String.valueOf(typed).getBytes(StandardCharsets.UTF_8));
        }
        if (modifiers == 0) return "KEY " + key.usbName;
        StringBuilder chord = new StringBuilder("HOTKEY ");
        if ((modifiers & CTRL) != 0) chord.append("CTRL+");
        if ((modifiers & SHIFT) != 0) chord.append("SHIFT+");
        if ((modifiers & ALT) != 0) chord.append("ALT+");
        if ((modifiers & WIN) != 0) chord.append("WIN+");
        return chord.append(key.usbName).toString();
    }

    String encode() {
        return modifiers + ":" + usage;
    }

    /** Parses {@link #encode()} output; returns null for anything malformed. */
    static KeyChord decode(String value) {
        if (value == null) return null;
        int colon = value.indexOf(':');
        if (colon <= 0 || colon == value.length() - 1) return null;
        try {
            int modifiers = Integer.parseInt(value.substring(0, colon));
            int usage = Integer.parseInt(value.substring(colon + 1));
            if (modifiers < 0 || modifiers > 0x0F || HidKeys.forUsage(usage) == null) return null;
            return new KeyChord(modifiers, usage);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override public boolean equals(Object other) {
        if (!(other instanceof KeyChord)) return false;
        KeyChord chord = (KeyChord) other;
        return chord.modifiers == modifiers && chord.usage == usage;
    }

    @Override public int hashCode() {
        return modifiers * 31 + usage;
    }

    @Override public String toString() {
        return label();
    }
}
