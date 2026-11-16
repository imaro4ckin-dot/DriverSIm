package org.example.drivesimui.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD DEMO — TimeFormatter
 *
 * Slide flow:
 *  1. Start with a single failing test (RED)
 *  2. Implement the minimal code to pass it (GREEN)
 *  3. Add edge-case tests — they catch real bugs (REFACTOR / SAFETY NET)
 */
class TimeFormatterTest {

    // ─────────────────────────────────────────────────────────────────────────
    // DEMO STEP 1 — RED: write this test BEFORE touching TimeFormatter
    //               It fails because the method doesn't exist yet.
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("formats 84 500 ms as 1:24.500")
    void formatMs_basicLapTime() {
        assertEquals("1:24.500", TimeFormatter.formatMs(84_500L));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DEMO STEP 2 — GREEN: implement formatMs(), run again — passes.
    //               Now add the next test without touching production code first.
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("formatMs()")
    class FormatMs {

        @Test
        @DisplayName("zero milliseconds → 0:00.000")
        void zero() {
            assertEquals("0:00.000", TimeFormatter.formatMs(0L));
        }

        @Test
        @DisplayName("exactly one minute → 1:00.000")
        void exactMinute() {
            assertEquals("1:00.000", TimeFormatter.formatMs(60_000L));
        }

        @Test
        @DisplayName("milliseconds are zero-padded to 3 digits")
        void millisPadded() {
            assertEquals("0:01.007", TimeFormatter.formatMs(1_007L));
        }

        @Test
        @DisplayName("seconds are zero-padded to 2 digits")
        void secondsPadded() {
            assertEquals("1:04.000", TimeFormatter.formatMs(64_000L));
        }

        @Test
        @DisplayName("multi-minute times are handled correctly")
        void multiMinute() {
            // 2 min 3 sec 450 ms = 123 450 ms
            assertEquals("2:03.450", TimeFormatter.formatMs(123_450L));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DEMO STEP 3 — the SAFETY NET: these tests uncover real edge cases
    //               that would otherwise only surface as user-reported bugs.
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("parseTimeInput() — colon format  m:ss.mmm")
    class ParseColonFormat {

        @Test
        @DisplayName("1:24.500 → 84 500 ms")
        void standard() {
            assertEquals(84_500L, TimeFormatter.parseTimeInput("1:24.500"));
        }

        @Test
        @DisplayName("2-digit milliseconds are treated as centiseconds (× 10)")
        void twoDigitMs() {
            assertEquals(84_500L, TimeFormatter.parseTimeInput("1:24.50"));
        }

        @Test
        @DisplayName("leading spaces are ignored")
        void leadingSpaces() {
            assertEquals(84_500L, TimeFormatter.parseTimeInput("  1:24.500"));
        }
    }

    @Nested
    @DisplayName("parseTimeInput() — dot format  m.ss.mmm")
    class ParseDotFormat {

        @Test
        @DisplayName("1.24.500 → 84 500 ms")
        void standard() {
            assertEquals(84_500L, TimeFormatter.parseTimeInput("1.24.500"));
        }

        @Test
        @DisplayName("2-digit milliseconds are treated as centiseconds (× 10)")
        void twoDigitMs() {
            assertEquals(84_500L, TimeFormatter.parseTimeInput("1.24.50"));
        }
    }

    @Nested
    @DisplayName("parseTimeInput() — raw milliseconds fallback")
    class ParseRawMs {

        @Test
        @DisplayName("84500 → 84 500 ms")
        void rawMs() {
            assertEquals(84_500L, TimeFormatter.parseTimeInput("84500"));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DEMO HIGHLIGHT — these tests WOULD HAVE caught real bugs:
    //   • seconds = 65 is physically impossible, yet without a test
    //     the parser silently produced a corrupted lap time.
    //   • null/blank input caused a NullPointerException in production.
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("parseTimeInput() — validation / error cases")
    class Validation {

        @Test
        @DisplayName("null input throws with a helpful message")
        void nullInput() {
            var ex = assertThrows(IllegalArgumentException.class,
                    () -> TimeFormatter.parseTimeInput(null));
            assertTrue(ex.getMessage().contains("lap time"),
                    "Error message should mention 'lap time'");
        }

        @Test
        @DisplayName("blank string throws")
        void blankInput() {
            assertThrows(IllegalArgumentException.class,
                    () -> TimeFormatter.parseTimeInput("  "));
        }

        @Test
        @DisplayName("seconds ≥ 60 are rejected — 1:65.000 is physically impossible")
        void invalidSeconds() {
            assertThrows(IllegalArgumentException.class,
                    () -> TimeFormatter.parseTimeInput("1:65.000"));
        }

        @Test
        @DisplayName("milliseconds ≥ 1000 are rejected")
        void invalidMillis() {
            assertThrows(IllegalArgumentException.class,
                    () -> TimeFormatter.parseTimeInput("1:24.1000"));
        }

        @Test
        @DisplayName("completely garbled input throws")
        void garbledInput() {
            assertThrows(IllegalArgumentException.class,
                    () -> TimeFormatter.parseTimeInput("abc"));
        }

        @Test
        @DisplayName("zero raw ms is rejected — a lap must take positive time")
        void zeroMs() {
            assertThrows(IllegalArgumentException.class,
                    () -> TimeFormatter.parseTimeInput("0"));
        }

        @Test
        @DisplayName("negative raw ms is rejected")
        void negativeMs() {
            assertThrows(IllegalArgumentException.class,
                    () -> TimeFormatter.parseTimeInput("-500"));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // BONUS — round-trip property: parse(format(x)) == x
    //         This kind of test is impossible to overlook in TDD.
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("round-trip: formatMs then parseTimeInput returns the original value")
    void roundTrip() {
        long originalMs = 84_731L;
        String formatted = TimeFormatter.formatMs(originalMs);       // "1:24.731"
        long parsed      = TimeFormatter.parseTimeInput(formatted);  // back to 84731
        assertEquals(originalMs, parsed);
    }
}
