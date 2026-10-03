package com.nido.a05sinput;

/**
 * Turns Anki remote actions into key presses and tracks which side of the card is showing.
 * The on-screen remote, the volume keys, swipes, and pocket mode all share one instance,
 * so they agree on whether the next Volume Down flips the card or grades it.
 */
final class AnkiCommander {
    interface Output {
        /** True when a computer or tablet is connected and can receive key presses. */
        boolean canSend();

        void send(KeyChord chord);

        AnkiKeymap.Target target();
    }

    interface Observer {
        /**
         * Called after every action.
         *
         * @param delivered whether the key press went to a connected host
         */
        void onAction(AnkiAction action, boolean delivered);
    }

    private final Output output;
    private AnkiKeymap keymap;
    private Observer observer;
    private boolean answerSide;

    AnkiCommander(Output output, AnkiKeymap keymap) {
        this.output = output;
        this.keymap = keymap;
    }

    void setObserver(Observer observer) {
        this.observer = observer;
    }

    void setKeymap(AnkiKeymap keymap) {
        this.keymap = keymap;
    }

    AnkiKeymap keymap() {
        return keymap;
    }

    boolean isAnswerSide() {
        return answerSide;
    }

    /** A new card's question is showing (a new review, or live info saw the card change). */
    void questionShown() {
        answerSide = false;
    }

    /** Sends the key for {@code action}; returns whether it reached a connected host. */
    boolean perform(AnkiAction action) {
        KeyChord chord = keymap.get(action, output.target());
        boolean delivered = chord != null && output.canSend();
        if (delivered) output.send(chord);
        if (action == AnkiAction.FLIP) answerSide = true;
        else if (action.showsNextQuestion()) answerSide = false;
        if (observer != null) observer.onAction(action, delivered);
        return delivered;
    }

    /** Volume Down: shows the answer, then grades Good. */
    AnkiAction volumeDown() {
        AnkiAction action = answerSide ? AnkiAction.GOOD : AnkiAction.FLIP;
        perform(action);
        return action;
    }

    /** Volume Up: grades Again. */
    AnkiAction volumeUp() {
        perform(AnkiAction.AGAIN);
        return AnkiAction.AGAIN;
    }
}
