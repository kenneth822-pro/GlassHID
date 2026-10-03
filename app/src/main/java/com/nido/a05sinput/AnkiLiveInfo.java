package com.nido.a05sinput;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Live Anki desktop state relayed by the Windows USB helper from the AnkiConnect add-on:
 * {@code ANKI state=review deck=<b64> new=3 learn=1 review=40 today=120 card=17 next=<b64>}.
 */
final class AnkiLiveInfo {
    static final String STATE_REVIEW = "review";
    static final String STATE_IDLE = "idle";
    static final String STATE_OFFLINE = "offline";

    final String state;
    final String deck;
    final int newCount;
    final int learnCount;
    final int reviewCount;
    final int reviewedToday;
    final long cardId;
    /** Next intervals for Again..Easy, e.g. {"<1m", "<6m", "<10m", "4d"}; may be empty. */
    final String[] nextIntervals;

    private AnkiLiveInfo(String state, String deck, int newCount, int learnCount, int reviewCount,
                         int reviewedToday, long cardId, String[] nextIntervals) {
        this.state = state;
        this.deck = deck;
        this.newCount = newCount;
        this.learnCount = learnCount;
        this.reviewCount = reviewCount;
        this.reviewedToday = reviewedToday;
        this.cardId = cardId;
        this.nextIntervals = nextIntervals;
    }

    boolean reviewing() {
        return STATE_REVIEW.equals(state);
    }

    boolean connected() {
        return !STATE_OFFLINE.equals(state);
    }

    /** Interval label for an ease (1-4), or "" when unknown. */
    String nextInterval(int ease) {
        return ease >= 1 && ease <= nextIntervals.length ? nextIntervals[ease - 1] : "";
    }

    /** Parses one helper line; returns null for anything that is not a well-formed ANKI line. */
    static AnkiLiveInfo parse(String line) {
        if (line == null || !line.startsWith("ANKI ")) return null;
        String state = null;
        String deck = "";
        int newCount = 0, learnCount = 0, reviewCount = 0, today = -1;
        long card = 0;
        String[] next = new String[0];
        for (String part : line.substring(5).trim().split("\\s+")) {
            int equals = part.indexOf('=');
            if (equals <= 0) continue;
            String key = part.substring(0, equals);
            String value = part.substring(equals + 1);
            try {
                switch (key) {
                    case "state": state = value; break;
                    case "deck": deck = decode(value); break;
                    case "new": newCount = Math.max(0, Integer.parseInt(value)); break;
                    case "learn": learnCount = Math.max(0, Integer.parseInt(value)); break;
                    case "review": reviewCount = Math.max(0, Integer.parseInt(value)); break;
                    case "today": today = Integer.parseInt(value); break;
                    case "card": card = Long.parseLong(value); break;
                    case "next":
                        String joined = decode(value);
                        next = joined.isEmpty() ? new String[0] : joined.split("\\|", -1);
                        break;
                    default: break;
                }
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        if (state == null || !(STATE_REVIEW.equals(state) || STATE_IDLE.equals(state) ||
                STATE_OFFLINE.equals(state))) return null;
        return new AnkiLiveInfo(state, deck, newCount, learnCount, reviewCount, today, card, next);
    }

    private static String decode(String value) {
        return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
    }
}
