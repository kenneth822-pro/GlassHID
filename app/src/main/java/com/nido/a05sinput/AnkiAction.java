package com.nido.a05sinput;

/** Everything the Anki remote can ask Anki desktop or AnkiDroid to do. */
enum AnkiAction {
    FLIP("Flip / show answer", 0),
    AGAIN("Again", 1),
    HARD("Hard", 2),
    GOOD("Good", 3),
    EASY("Easy", 4),
    UNDO("Undo", 0),
    REDO("Redo", 0),
    REPLAY("Replay audio", 0),
    MARK("Mark note", 0),
    MORE_MENU("More menu (desktop)", 0),
    BURY_CARD("Bury card", 0),
    BURY_NOTE("Bury note", 0),
    SUSPEND_CARD("Suspend card", 0),
    SUSPEND_NOTE("Suspend note", 0),
    FLAG_RED("Flag red", 0),
    FLAG_ORANGE("Flag orange", 0),
    FLAG_GREEN("Flag green", 0),
    FLAG_BLUE("Flag blue", 0),
    EDIT("Edit note", 0);

    final String label;
    /** Anki's ease number for grade actions, 0 otherwise. */
    final int ease;

    AnkiAction(String label, int ease) {
        this.label = label;
        this.ease = ease;
    }

    boolean isGrade() {
        return ease > 0;
    }

    /** True when Anki moves on to the next card's question after this action. */
    boolean showsNextQuestion() {
        return isGrade() || this == UNDO || this == BURY_CARD || this == BURY_NOTE ||
                this == SUSPEND_CARD || this == SUSPEND_NOTE;
    }

    static AnkiAction fromName(String name) {
        for (AnkiAction action : values()) if (action.name().equals(name)) return action;
        return null;
    }
}
