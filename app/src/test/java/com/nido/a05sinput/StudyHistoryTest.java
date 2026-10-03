package com.nido.a05sinput;

import static org.junit.Assert.assertEquals;

import java.time.LocalDate;
import java.util.List;
import org.junit.Test;

public class StudyHistoryTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 3);

    @Test public void tracksDaysAndNeverGoesNegative() {
        StudyHistory history = new StudyHistory();
        history.addGrade(TODAY, 3, 1);
        history.addGrade(TODAY, 3, 1);
        history.addGrade(TODAY, 1, 1);
        history.addGrade(TODAY, 1, -1);
        history.addGrade(TODAY, 1, -1);
        history.addGrade(TODAY, 9, 1);
        assertEquals(2, history.day(TODAY).total());
        assertEquals(0, history.day(TODAY).count(1));
        assertEquals(0, history.day(TODAY.minusDays(1)).total());
    }

    @Test public void streakCountsBackFromTodayOrYesterday() {
        StudyHistory history = new StudyHistory();
        history.addGrade(TODAY.minusDays(1), 3, 1);
        history.addGrade(TODAY.minusDays(2), 3, 1);
        history.addGrade(TODAY.minusDays(4), 3, 1);
        assertEquals(2, history.streak(TODAY));
        history.addGrade(TODAY, 2, 1);
        assertEquals(3, history.streak(TODAY));
        assertEquals(0, new StudyHistory().streak(TODAY));
    }

    @Test public void lastDaysIsOldestFirstWithGaps() {
        StudyHistory history = new StudyHistory();
        history.addGrade(TODAY, 4, 1);
        history.addGrade(TODAY.minusDays(6), 4, 1);
        List<StudyHistory.Day> week = history.lastDays(TODAY, 7);
        assertEquals(7, week.size());
        assertEquals(TODAY.minusDays(6), week.get(0).date);
        assertEquals(1, week.get(0).total());
        assertEquals(0, week.get(3).total());
        assertEquals(TODAY, week.get(6).date);
    }

    @Test public void roundTripsTrimsAndSurvivesDamage() {
        StudyHistory history = new StudyHistory();
        history.addGrade(TODAY, 1, 1);
        history.addGrade(TODAY, 4, 1);
        history.addActive(TODAY, 90_000);
        history.addGrade(TODAY.minusDays(200), 3, 1);
        history.trim(TODAY);
        StudyHistory copy = StudyHistory.decode(history.encode() + "|nonsense|2026-13-40:1,1,1,1,1|2026-01-01:x,1,1,1,1");
        assertEquals(2, copy.day(TODAY).total());
        assertEquals(90_000, copy.day(TODAY).activeMs);
        assertEquals(0, copy.day(TODAY.minusDays(200)).total());
        assertEquals(2, copy.bestDay());
        assertEquals(0, StudyHistory.decode(null).bestDay());
    }
}
