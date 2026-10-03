package com.nido.a05sinput;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** USB HID keyboard usages the remote can send, with US-layout labels and characters. */
final class HidKeys {
    static final class Key {
        final int usage;
        final String label;
        /** Character typed without Shift, or 0 for keys that type nothing. */
        final char base;
        /** Character typed with Shift, or 0. */
        final char shifted;
        /** Name the Windows USB helper understands for KEY and HOTKEY commands. */
        final String usbName;

        Key(int usage, String label, char base, char shifted, String usbName) {
            this.usage = usage;
            this.label = label;
            this.base = base;
            this.shifted = shifted;
            this.usbName = usbName;
        }

        boolean printable() {
            return base != 0 && base != ' ';
        }
    }

    private static final Map<Integer, Key> BY_USAGE = new LinkedHashMap<>();

    static {
        for (char c = 'a'; c <= 'z'; c++) {
            String upper = String.valueOf(Character.toUpperCase(c));
            add(0x04 + c - 'a', upper, c, Character.toUpperCase(c), upper);
        }
        String shiftedDigits = "!@#$%^&*(";
        for (int i = 0; i < 9; i++) {
            char digit = (char) ('1' + i);
            add(0x1E + i, String.valueOf(digit), digit, shiftedDigits.charAt(i), String.valueOf(digit));
        }
        add(0x27, "0", '0', ')', "0");
        add(0x28, "Enter", (char) 0, (char) 0, "ENTER");
        add(0x29, "Esc", (char) 0, (char) 0, "ESC");
        add(0x2A, "Backspace", (char) 0, (char) 0, "BACKSPACE");
        add(0x2B, "Tab", (char) 0, (char) 0, "TAB");
        add(0x2C, "Space", ' ', ' ', "SPACE");
        add(0x2D, "-", '-', '_', "-");
        add(0x2E, "=", '=', '+', "=");
        add(0x2F, "[", '[', '{', "[");
        add(0x30, "]", ']', '}', "]");
        add(0x31, "\\", '\\', '|', "\\");
        add(0x33, ";", ';', ':', ";");
        add(0x34, "'", '\'', '"', "'");
        add(0x35, "`", '`', '~', "`");
        add(0x36, ",", ',', '<', ",");
        add(0x37, ".", '.', '>', ".");
        add(0x38, "/", '/', '?', "/");
        for (int i = 0; i < 12; i++) {
            String name = "F" + (i + 1);
            add(0x3A + i, name, (char) 0, (char) 0, name);
        }
        add(0x49, "Insert", (char) 0, (char) 0, "INSERT");
        add(0x4A, "Home", (char) 0, (char) 0, "HOME");
        add(0x4B, "Page Up", (char) 0, (char) 0, "PGUP");
        add(0x4C, "Delete", (char) 0, (char) 0, "DELETE");
        add(0x4D, "End", (char) 0, (char) 0, "END");
        add(0x4E, "Page Down", (char) 0, (char) 0, "PGDN");
        add(0x4F, "Right", (char) 0, (char) 0, "RIGHT");
        add(0x50, "Left", (char) 0, (char) 0, "LEFT");
        add(0x51, "Down", (char) 0, (char) 0, "DOWN");
        add(0x52, "Up", (char) 0, (char) 0, "UP");
    }

    private HidKeys() {
    }

    private static void add(int usage, String label, char base, char shifted, String usbName) {
        BY_USAGE.put(usage, new Key(usage, label, base, shifted, usbName));
    }

    /** The key for a usage, or null when the remote does not offer it. */
    static Key forUsage(int usage) {
        return BY_USAGE.get(usage);
    }

    /** Every key in picker order: letters, digits, specials, punctuation, F-keys, navigation. */
    static List<Key> all() {
        return Collections.unmodifiableList(new ArrayList<>(BY_USAGE.values()));
    }
}
