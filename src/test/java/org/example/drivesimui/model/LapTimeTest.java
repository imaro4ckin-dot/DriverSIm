package org.example.drivesimui.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD DEMO — LapTime domain model
 *
 * Key message: tests document *behaviour*, not just getters.
 * Every test reads like a sentence describing a rule from the domain.
 */
class LapTimeTest {

    // Shared test fixture — one real GT3 car, one lap at Spa
    private GT3Car car;
    private LapTime lap;

    @BeforeEach
    void setUp() {
        car = new GT3Car("Ferrari", "296 GT3", 2024);
        lap = new LapTime("Spa-Francorchamps", 84_500L, car);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Construction
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("a new lap starts unpersisted (id = 0)")
    void newLap_isUnpersisted() {
        assertEquals(0L, lap.getId());
    }

    @Test
    @DisplayName("a new lap defaults to DRY weather")
    void newLap_defaultWeatherIsDry() {
        assertEquals(LapTime.Weather.DRY, lap.getWeather());
    }

    @Test
    @DisplayName("a new lap has today's date")
    void newLap_hasDateRecorded() {
        assertNotNull(lap.getDateRecorded());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Time formatting
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getFormatedTime()")
    class FormattedTime {

        @Test
        @DisplayName("84 500 ms displays as 1:24.500")
        void formatsCorrectly() {
            assertEquals("1:24.500", lap.getFormatedTime());
        }

        @Test
        @DisplayName("sub-minute laps show 0 minutes")
        void subMinuteLap() {
            LapTime shortLap = new LapTime("Kart Track", 58_320L, car);
            assertEquals("0:58.320", shortLap.getFormatedTime());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Sectors — DEMO HIGHLIGHT
    // hasSectors() is a domain rule: ALL THREE sectors must be recorded.
    // Writing the test first forces you to think about the rule before the code.
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("hasSectors()")
    class Sectors {

        @Test
        @DisplayName("returns false when no sectors are set")
        void noSectors() {
            assertFalse(lap.hasSectors());
        }

        @Test
        @DisplayName("returns false with only S1 set — all three are required")
        void onlyS1() {
            lap.setS1Ms(28_000L);
            assertFalse(lap.hasSectors());
        }

        @Test
        @DisplayName("returns false with only S1 + S2 set")
        void onlyS1andS2() {
            lap.setS1Ms(28_000L);
            lap.setS2Ms(31_000L);
            assertFalse(lap.hasSectors());
        }

        @Test
        @DisplayName("returns true only when all three sectors are set")
        void allThreeSectors() {
            lap.setS1Ms(28_000L);
            lap.setS2Ms(31_000L);
            lap.setS3Ms(25_500L);
            assertTrue(lap.hasSectors());
        }

        @Test
        @DisplayName("sector display shows dash when not recorded")
        void sectorDisplayDash() {
            assertEquals("—", lap.getFormattedS1());
            assertEquals("—", lap.getFormattedS2());
            assertEquals("—", lap.getFormattedS3());
        }

        @Test
        @DisplayName("sector display shows formatted time when recorded")
        void sectorDisplayFormatted() {
            lap.setS1Ms(28_000L);
            assertEquals("0:28.000", lap.getFormattedS1());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Delta — gap to the best lap in the session
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getFormattedDelta()")
    class Delta {

        @Test
        @DisplayName("returns dash when no delta is set (e.g. the reference lap itself)")
        void noDelta() {
            assertEquals("—", lap.getFormattedDelta());
        }

        @Test
        @DisplayName("positive delta has a + prefix  (slower than best)")
        void positiveDelta() {
            lap.setDeltaMs(831L);
            assertEquals("+0.831", lap.getFormattedDelta());
        }

        @Test
        @DisplayName("negative delta has no + prefix  (faster — new best)")
        void negativeDelta() {
            lap.setDeltaMs(-412L);
            assertEquals("-0.412", lap.getFormattedDelta());
        }

        @Test
        @DisplayName("delta of exactly zero shows a dash, not +0.000")
        void zeroDeltaShowsDash() {
            lap.setDeltaMs(0L);
            assertEquals("—", lap.getFormattedDelta());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Serialisation — tests protect the CSV/JSON contract
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("toCSV()")
    class CsvExport {

        @Test
        @DisplayName("CSV contains track name")
        void containsTrack() {
            assertTrue(lap.toCSV().contains("Spa-Francorchamps"));
        }

        @Test
        @DisplayName("CSV contains lap time in milliseconds")
        void containsLapTimeMs() {
            assertTrue(lap.toCSV().contains("84500"));
        }

        @Test
        @DisplayName("CSV contains car make and model separated by a dash")
        void containsCarIdentifier() {
            assertTrue(lap.toCSV().contains("Ferrari-296 GT3"));
        }
    }

    @Nested
    @DisplayName("toJSON()")
    class JsonExport {

        @Test
        @DisplayName("JSON contains lapTimeMs field")
        void containsLapTimeMs() {
            assertTrue(lap.toJSON().contains("\"lapTimeMs\": 84500"));
        }

        @Test
        @DisplayName("JSON contains weather field")
        void containsWeather() {
            assertTrue(lap.toJSON().contains("\"weather\": \"DRY\""));
        }

        @Test
        @DisplayName("JSON contains nested car type")
        void containsCarType() {
            assertTrue(lap.toJSON().contains("\"type\": \"GT3\""));
        }
    }
}
