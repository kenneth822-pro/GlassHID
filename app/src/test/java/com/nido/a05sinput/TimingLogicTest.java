package com.nido.a05sinput;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.nido.a05sinput.PressGesture.Result;
import org.junit.Test;

public class TimingLogicTest {
    @Test public void focusTimerCyclesFocusThenBreak() {
        FocusTimer timer = new FocusTimer(25 * 60_000, 5 * 60_000);
        assertEquals(FocusTimer.Phase.OFF, timer.phase());
        timer.startFocus(0);
        assertEquals(FocusTimer.Event.NONE, timer.tick(24 * 60_000));
        assertEquals(60_000, timer.remaining(24 * 60_000));
        assertEquals(FocusTimer.Event.FOCUS_DONE, timer.tick(25 * 60_000));
        assertEquals(FocusTimer.Phase.BREAK, timer.phase());
        assertEquals(FocusTimer.Event.NONE, timer.tick(26 * 60_000));
        assertEquals(FocusTimer.Event.BREAK_DONE, timer.tick(30 * 60_000));
        assertEquals(FocusTimer.Phase.OFF, timer.phase());
        assertEquals(FocusTimer.Event.NONE, timer.tick(40 * 60_000));
        assertEquals(0, timer.remaining(40 * 60_000));
    }

    @Test public void focusTimerEnforcesAMinimumMinute() {
        FocusTimer timer = new FocusTimer(0, 0);
        timer.startFocus(0);
        assertEquals(FocusTimer.Event.NONE, timer.tick(59_999));
        assertEquals(FocusTimer.Event.FOCUS_DONE, timer.tick(60_000));
    }

    @Test public void nudgeFiresOnceAfterTheLimit() {
        AnswerNudge nudge = new AnswerNudge();
        assertFalse(nudge.due(1_000_000));
        nudge.arm(0, 15_000);
        assertTrue(nudge.armed());
        assertFalse(nudge.due(14_999));
        assertTrue(nudge.due(15_000));
        assertFalse(nudge.due(16_000));
        nudge.arm(0, 15_000);
        nudge.disarm();
        assertFalse(nudge.due(20_000));
        nudge.arm(0, 0);
        assertFalse(nudge.armed());
    }

    @Test public void shortPressResolvesOnRelease() {
        PressGesture gesture = new PressGesture();
        assertEquals(Result.NONE, gesture.press(0));
        assertTrue(gesture.isHeld());
        assertEquals(Result.SHORT, gesture.release(120));
        assertEquals(Result.NONE, gesture.release(130));
    }

    @Test public void longPressFiresOnceWhileHeld() {
        PressGesture gesture = new PressGesture();
        gesture.press(0);
        assertEquals(Result.NONE, gesture.press(450));
        assertEquals(Result.LONG, gesture.press(520));
        assertEquals(Result.NONE, gesture.press(570));
        assertEquals(Result.NONE, gesture.release(900));
    }

    @Test public void longHoldWithoutRepeatsResolvesAtRelease() {
        PressGesture gesture = new PressGesture();
        gesture.press(0);
        assertEquals(Result.LONG, gesture.release(800));
    }

    @Test public void quietPressTimesOutAsShort() {
        PressGesture gesture = new PressGesture();
        gesture.press(0);
        assertEquals(Result.NONE, gesture.timeout(600));
        assertEquals(Result.SHORT, gesture.timeout(PressGesture.RELEASE_GAP_MS));
        assertFalse(gesture.isHeld());
        gesture.press(1_000);
        gesture.press(1_500);
        assertEquals(Result.NONE, gesture.timeout(1_500 + PressGesture.RELEASE_GAP_MS));
        gesture.press(5_000);
        gesture.cancel();
        assertFalse(gesture.isHeld());
    }

    @Test public void aLostReleaseDoesNotTurnTheNextPressIntoALongPress() {
        PressGesture gesture = new PressGesture();
        gesture.press(0);
        // The release never arrives; a fresh press much later is a new short press.
        assertEquals(Result.NONE, gesture.press(10_000));
        assertEquals(Result.SHORT, gesture.release(10_090));
    }

    @Test public void studyStepperMovesThroughItsSteps() {
        assertEquals(50, StudyCoach.step(StudyCoach.GOAL_STEPS, 0, 1));
        assertEquals(0, StudyCoach.step(StudyCoach.GOAL_STEPS, 50, -1));
        assertEquals(0, StudyCoach.step(StudyCoach.GOAL_STEPS, 0, -1));
        assertEquals(1000, StudyCoach.step(StudyCoach.GOAL_STEPS, 1000, 1));
        assertEquals(150, StudyCoach.step(StudyCoach.GOAL_STEPS, 120, 1));
        assertEquals(20, StudyCoach.step(StudyCoach.FOCUS_STEPS, 25, -1));
        assertEquals(3, StudyCoach.step(StudyCoach.BREAK_STEPS, 1, -1));
    }

    @Test public void swipeClassifierReadsDirectionsAndTaps() {
        float slop = 20, min = 120;
        assertEquals(SwipeClassifier.Gesture.TAP, SwipeClassifier.classify(3, 4, 120, slop, min));
        assertEquals(SwipeClassifier.Gesture.NONE, SwipeClassifier.classify(3, 4, 800, slop, min));
        assertEquals(SwipeClassifier.Gesture.LEFT, SwipeClassifier.classify(-200, 30, 250, slop, min));
        assertEquals(SwipeClassifier.Gesture.RIGHT, SwipeClassifier.classify(200, -30, 250, slop, min));
        assertEquals(SwipeClassifier.Gesture.UP, SwipeClassifier.classify(10, -300, 250, slop, min));
        assertEquals(SwipeClassifier.Gesture.DOWN, SwipeClassifier.classify(-10, 300, 250, slop, min));
        assertEquals(SwipeClassifier.Gesture.NONE, SwipeClassifier.classify(150, 150, 250, slop, min));
        assertEquals(SwipeClassifier.Gesture.NONE, SwipeClassifier.classify(60, 0, 250, slop, min));
        assertEquals(SwipeClassifier.Gesture.NONE, SwipeClassifier.classify(400, 0, 2_000, slop, min));
    }
}
