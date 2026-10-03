package com.nido.a05sinput;

import java.util.EnumMap;
import java.util.Map;

/**
 * Which key each Anki action sends, per target app. Defaults follow the stock shortcuts of
 * Anki desktop and AnkiDroid; users who changed theirs can override any action.
 */
final class AnkiKeymap {
    enum Target { DESKTOP, ANKIDROID }

    private final Map<Target, EnumMap<AnkiAction, KeyChord>> overrides = new EnumMap<>(Target.class);

    AnkiKeymap() {
        for (Target target : Target.values()) overrides.put(target, new EnumMap<>(AnkiAction.class));
    }

    /** Stock shortcut, or null when the target app has none (AnkiDroid's More menu). */
    static KeyChord defaultChord(AnkiAction action, Target target) {
        switch (action) {
            case FLIP: return KeyChord.key(0x2C);                       // Space
            case AGAIN: return KeyChord.key(0x1E);                      // 1
            case HARD: return KeyChord.key(0x1F);                       // 2
            case GOOD: return KeyChord.key(0x2C);                       // Space = default (Good)
            case EASY: return KeyChord.key(0x21);                       // 4
            case UNDO: return KeyChord.of(KeyChord.CTRL, 0x1D);         // Ctrl+Z
            case REDO: return KeyChord.of(KeyChord.CTRL | KeyChord.SHIFT, 0x1D);
            case REPLAY: return KeyChord.key(0x15);                     // r
            case MARK: return KeyChord.of(KeyChord.SHIFT, 0x25);        // *
            case MORE_MENU: return target == Target.DESKTOP ? KeyChord.key(0x10) : null; // m
            case BURY_CARD: return KeyChord.key(0x2D);                  // -
            case BURY_NOTE: return KeyChord.key(0x2E);                  // =
            case SUSPEND_CARD: return KeyChord.of(KeyChord.SHIFT, 0x1F); // @
            case SUSPEND_NOTE: return KeyChord.of(KeyChord.SHIFT, 0x1E); // !
            case FLAG_RED: return KeyChord.of(KeyChord.CTRL, 0x1E);     // Ctrl+1
            case FLAG_ORANGE: return KeyChord.of(KeyChord.CTRL, 0x1F);
            case FLAG_GREEN: return KeyChord.of(KeyChord.CTRL, 0x20);
            case FLAG_BLUE: return KeyChord.of(KeyChord.CTRL, 0x21);
            case EDIT: return KeyChord.key(0x08);                       // e
            default: return null;
        }
    }

    KeyChord get(AnkiAction action, Target target) {
        KeyChord custom = overrides.get(target).get(action);
        return custom != null ? custom : defaultChord(action, target);
    }

    boolean isCustom(AnkiAction action, Target target) {
        return overrides.get(target).containsKey(action);
    }

    /** Sets a custom chord; null (or the stock chord) restores the default. */
    void set(AnkiAction action, Target target, KeyChord chord) {
        if (chord == null || chord.equals(defaultChord(action, target))) {
            overrides.get(target).remove(action);
        } else {
            overrides.get(target).put(action, chord);
        }
    }

    void resetAll(Target target) {
        overrides.get(target).clear();
    }

    /** Only overrides are stored, e.g. "DESKTOP.UNDO=1:29;ANKIDROID.GOOD=0:32". */
    String encode() {
        StringBuilder out = new StringBuilder();
        for (Target target : Target.values()) {
            for (Map.Entry<AnkiAction, KeyChord> entry : overrides.get(target).entrySet()) {
                if (out.length() > 0) out.append(';');
                out.append(target.name()).append('.').append(entry.getKey().name())
                        .append('=').append(entry.getValue().encode());
            }
        }
        return out.toString();
    }

    /** Reads {@link #encode()} output, skipping any entry it does not understand. */
    static AnkiKeymap decode(String value) {
        AnkiKeymap keymap = new AnkiKeymap();
        if (value == null || value.isEmpty()) return keymap;
        for (String entry : value.split(";")) {
            int dot = entry.indexOf('.');
            int equals = entry.indexOf('=');
            if (dot <= 0 || equals <= dot + 1) continue;
            Target target;
            try {
                target = Target.valueOf(entry.substring(0, dot));
            } catch (IllegalArgumentException e) {
                continue;
            }
            AnkiAction action = AnkiAction.fromName(entry.substring(dot + 1, equals));
            KeyChord chord = KeyChord.decode(entry.substring(equals + 1));
            if (action != null && chord != null) keymap.set(action, target, chord);
        }
        return keymap;
    }
}
