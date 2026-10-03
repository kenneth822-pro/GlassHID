package com.nido.a05sinput;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ReviewSessionTest {
    @Test public void countsGradesAndUndo() {
        ReviewSession session = new ReviewSession();
        session.grade(3, 1_000);
        session.grade(1, 11_000);
        session.grade(3, 21_000);
        assertEquals(3, session.total());
        assertEquals(1, session.count(1));
        assertEquals(1 / 3.0, session.againRate(), 1e-9);
        assertEquals(3, session.undo(25_000));
        assertEquals(2, session.total());
        assertEquals(1, session.undo(26_000));
        assertEquals(3, session.undo(27_000));
        assertEquals(0, session.undo(28_000));
        assertEquals(0, session.total());
        assertEquals(0.0, session.againRate(), 0);
    }

    @Test public void studyTimeCapsLongGaps() {
        ReviewSession session = new ReviewSession();
        session.touch(0);
        session.grade(3, 10_000);
        session.grade(3, 10_000 + 10 * 60_000); // a ten-minute break counts as one minute
        assertEquals(10_000 + ReviewSession.MAX_CARD_MS, session.activeMs());
        assertEquals(2 / (70_000 / 60_000.0), session.cardsPerMinute(), 1e-9);
    }

    @Test public void paceNeedsHalfAMinuteOfData() {
        ReviewSession session = new ReviewSession();
        session.grade(3, 0);
        session.grade(3, 5_000);
        assertEquals(0.0, session.cardsPerMinute(), 0);
    }

    @Test public void goesStaleAfterInactivity() {
        ReviewSession session = new ReviewSession();
        assertFalse(session.isStale(1_000_000));
        session.grade(4, 0);
        assertFalse(session.isStale(ReviewSession.SESSION_TIMEOUT_MS));
        assertTrue(session.isStale(ReviewSession.SESSION_TIMEOUT_MS + 1));
        session.reset();
        assertEquals(0, session.total());
        assertEquals(0, session.startedAt());
    }

    @Test public void roundTripsThroughEncoding() {
        ReviewSession session = new ReviewSession();
        session.grade(1, 1_000);
        session.grade(2, 2_000);
        session.grade(4, 3_000);
        ReviewSession copy = ReviewSession.decode(session.encode());
        assertEquals(3, copy.total());
        assertEquals(session.activeMs(), copy.activeMs());
        assertEquals(session.startedAt(), copy.startedAt());
        assertEquals(4, copy.undo(4_000));
        assertEquals(2, copy.undo(5_000));
        assertEquals(0, ReviewSession.decode("garbage").total());
        assertEquals(0, ReviewSession.decode("1,2,3,19").total());
        assertEquals(0, ReviewSession.decode(null).total());
    }
}
