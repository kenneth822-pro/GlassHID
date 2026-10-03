package com.nido.a05sinput;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

public class AnkiCommanderTest {
    private final List<KeyChord> sent = new ArrayList<>();
    private final List<String> observed = new ArrayList<>();
    private boolean connected = true;
    private AnkiKeymap.Target target = AnkiKeymap.Target.DESKTOP;
    private AnkiCommander commander;

    @Before public void setUp() {
        commander = new AnkiCommander(new AnkiCommander.Output() {
            @Override public boolean canSend() { return connected; }
            @Override public void send(KeyChord chord) { sent.add(chord); }
            @Override public AnkiKeymap.Target target() { return target; }
        }, new AnkiKeymap());
        commander.setObserver((action, delivered) -> observed.add(action + ":" + delivered));
    }

    @Test public void volumeDownFlipsThenGradesGood() {
        assertEquals(AnkiAction.FLIP, commander.volumeDown());
        assertTrue(commander.isAnswerSide());
        assertEquals(AnkiAction.GOOD, commander.volumeDown());
        assertFalse(commander.isAnswerSide());
        assertEquals(AnkiAction.FLIP, commander.volumeDown());
        assertEquals(3, sent.size());
        for (KeyChord chord : sent) assertEquals(KeyChord.key(0x2C), chord);
    }

    @Test public void volumeUpGradesAgainAndReturnsToQuestion() {
        commander.perform(AnkiAction.FLIP);
        assertEquals(AnkiAction.AGAIN, commander.volumeUp());
        assertFalse(commander.isAnswerSide());
        assertEquals(KeyChord.key(0x1E), sent.get(1));
    }

    @Test public void cardChangingActionsResetToQuestion() {
        for (AnkiAction action : new AnkiAction[]{AnkiAction.UNDO, AnkiAction.BURY_CARD,
                AnkiAction.SUSPEND_NOTE, AnkiAction.EASY}) {
            commander.perform(AnkiAction.FLIP);
            commander.perform(action);
            assertFalse(action.name(), commander.isAnswerSide());
        }
        commander.perform(AnkiAction.FLIP);
        commander.perform(AnkiAction.MARK);
        commander.perform(AnkiAction.FLAG_RED);
        commander.perform(AnkiAction.REPLAY);
        assertTrue(commander.isAnswerSide());
        commander.questionShown();
        assertFalse(commander.isAnswerSide());
    }

    @Test public void nothingIsSentWhileDisconnectedButStateStillMoves() {
        connected = false;
        commander.perform(AnkiAction.FLIP);
        assertTrue(sent.isEmpty());
        assertTrue(commander.isAnswerSide());
        assertEquals("FLIP:false", observed.get(0));
    }

    @Test public void ankiDroidHasNoMoreMenuKeyAndUsesCustomBindings() {
        target = AnkiKeymap.Target.ANKIDROID;
        assertFalse(commander.perform(AnkiAction.MORE_MENU));
        assertTrue(sent.isEmpty());
        commander.keymap().set(AnkiAction.GOOD, target, KeyChord.key(0x20));
        commander.perform(AnkiAction.FLIP);
        commander.perform(AnkiAction.GOOD);
        assertEquals(KeyChord.key(0x20), sent.get(1));
        assertEquals("GOOD:true", observed.get(observed.size() - 1));
    }
}
