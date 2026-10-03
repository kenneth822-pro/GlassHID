package com.nido.a05sinput;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.Test;

public class AnkiLiveInfoTest {
    private static String b64(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    @Test public void parsesAReviewLine() {
        AnkiLiveInfo info = AnkiLiveInfo.parse("ANKI state=review deck=" + b64("Biology::Cells 🧬")
                + " new=3 learn=1 review=40 today=120 card=1700000000123 next=" + b64("<1m|<6m|<10m|4d"));
        assertTrue(info.reviewing());
        assertTrue(info.connected());
        assertEquals("Biology::Cells 🧬", info.deck);
        assertEquals(3, info.newCount);
        assertEquals(1, info.learnCount);
        assertEquals(40, info.reviewCount);
        assertEquals(120, info.reviewedToday);
        assertEquals(1700000000123L, info.cardId);
        assertEquals("<1m", info.nextInterval(1));
        assertEquals("4d", info.nextInterval(4));
        assertEquals("", info.nextInterval(5));
    }

    @Test public void parsesIdleAndOffline() {
        AnkiLiveInfo idle = AnkiLiveInfo.parse("ANKI state=idle today=5");
        assertFalse(idle.reviewing());
        assertTrue(idle.connected());
        assertEquals(5, idle.reviewedToday);
        assertEquals("", idle.nextInterval(1));
        AnkiLiveInfo offline = AnkiLiveInfo.parse("ANKI state=offline");
        assertFalse(offline.connected());
        assertEquals(-1, offline.reviewedToday);
    }

    @Test public void rejectsMalformedLines() {
        assertNull(AnkiLiveInfo.parse(null));
        assertNull(AnkiLiveInfo.parse("BATTERY 50 1"));
        assertNull(AnkiLiveInfo.parse("ANKI deck=abc"));
        assertNull(AnkiLiveInfo.parse("ANKI state=dancing"));
        assertNull(AnkiLiveInfo.parse("ANKI state=review new=many"));
        assertNull(AnkiLiveInfo.parse("ANKI state=review deck=%%%"));
        AnkiLiveInfo ignoresUnknown = AnkiLiveInfo.parse("ANKI state=review future=1 junk");
        assertTrue(ignoresUnknown.reviewing());
    }
}
