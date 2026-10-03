package com.nido.a05sinput;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Per-day review totals given through the remote, with streaks; kept on the phone only. */
final class StudyHistory {
    static final int KEEP_DAYS = 120;

    static final class Day {
        final LocalDate date;
        final int[] counts = new int[5];
        long activeMs;

        Day(LocalDate date) {
            this.date = date;
        }

        int count(int ease) {
            return counts[ease];
        }

        int total() {
            return counts[1] + counts[2] + counts[3] + counts[4];
        }
    }

    private final TreeMap<LocalDate, Day> days = new TreeMap<>();

    void addGrade(LocalDate date, int ease, int delta) {
        if (ease < 1 || ease > 4) return;
        Day day = dayFor(date);
        day.counts[ease] = Math.max(0, day.counts[ease] + delta);
    }

    void addActive(LocalDate date, long ms) {
        if (ms > 0) dayFor(date).activeMs += ms;
    }

    /** The record for a date; an empty one if nothing was studied. */
    Day day(LocalDate date) {
        Day day = days.get(date);
        return day != null ? day : new Day(date);
    }

    /** Consecutive days with at least one review, ending today (or yesterday if today is empty). */
    int streak(LocalDate today) {
        LocalDate cursor = day(today).total() > 0 ? today : today.minusDays(1);
        int streak = 0;
        while (day(cursor).total() > 0) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }

    /** The last {@code count} days, oldest first, including empty ones. */
    List<Day> lastDays(LocalDate today, int count) {
        List<Day> out = new ArrayList<>();
        for (int i = count - 1; i >= 0; i--) out.add(day(today.minusDays(i)));
        return out;
    }

    int bestDay() {
        int best = 0;
        for (Day day : days.values()) best = Math.max(best, day.total());
        return best;
    }

    void clear() {
        days.clear();
    }

    void trim(LocalDate today) {
        LocalDate oldest = today.minusDays(KEEP_DAYS - 1);
        days.headMap(oldest).clear();
    }

    private Day dayFor(LocalDate date) {
        Day day = days.get(date);
        if (day == null) {
            day = new Day(date);
            days.put(date, day);
        }
        return day;
    }

    /** "2026-10-03:a,h,g,e,ms|..." for days with any data. */
    String encode() {
        StringBuilder out = new StringBuilder();
        for (Map.Entry<LocalDate, Day> entry : days.entrySet()) {
            Day day = entry.getValue();
            if (day.total() == 0 && day.activeMs == 0) continue;
            if (out.length() > 0) out.append('|');
            out.append(entry.getKey()).append(':')
                    .append(day.counts[1]).append(',').append(day.counts[2]).append(',')
                    .append(day.counts[3]).append(',').append(day.counts[4]).append(',')
                    .append(day.activeMs);
        }
        return out.toString();
    }

    static StudyHistory decode(String value) {
        StudyHistory history = new StudyHistory();
        if (value == null || value.isEmpty()) return history;
        for (String entry : value.split("\\|")) {
            int colon = entry.indexOf(':');
            if (colon <= 0) continue;
            String[] numbers = entry.substring(colon + 1).split(",");
            if (numbers.length != 5) continue;
            try {
                Day day = history.dayFor(LocalDate.parse(entry.substring(0, colon)));
                for (int ease = 1; ease <= 4; ease++)
                    day.counts[ease] = Math.max(0, Integer.parseInt(numbers[ease - 1]));
                day.activeMs = Math.max(0, Long.parseLong(numbers[4]));
            } catch (DateTimeParseException | NumberFormatException e) {
                // Skip a damaged entry rather than losing the whole history.
            }
        }
        return history;
    }
}
