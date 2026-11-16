package org.example.drivesimui.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD DEMO — FuelCalculator
 *
 * The KEY message for this demo:
 *
 *   The original calculation lived inside FuelStrategyController.calculate().
 *   That method sets JavaFX Labels, updates colours, and touches UI nodes.
 *   There is NO way to test it without starting a JavaFX application.
 *
 *   Writing these tests FIRST forced us to extract the math into
 *   FuelCalculator — a plain class with no UI dependencies.
 *   The controller now delegates to it.
 *
 *   TDD didn't just verify correctness — it IMPROVED THE DESIGN.
 */
class FuelCalculatorTest {

    // ─────────────────────────────────────────────────────────────────────────
    // DEMO STEP 1 — write this test BEFORE FuelCalculator even exists.
    //               It won't compile — that IS the red phase.
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("basic 30-lap race, 1 pit stop, no safety car")
    void basicRace() {
        // 30 laps × 2.5 L = 75 L base
        // + 0 SC laps
        // + 1 lap buffer = 77.5 L total
        // / 2 stints     = 38.75 L per stint
        // 110 L tank - 77.5 = 32.5 L margin
        FuelCalculator.Result r = FuelCalculator.calculate(30, 110.0, 2.5, 0, 1);

        assertEquals(75.0,  r.totalFuelNeeded(), 0.01);
        assertEquals(77.5,  r.totalWithBuffer(), 0.01);
        assertEquals(38.75, r.fuelPerStint(),    0.01);
        assertEquals(32.5,  r.marginVsTank(),    0.01);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Safety-car laps consume 60 % of normal fuel
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("safety-car lap fuel reduction")
    class SafetyCarLaps {

        @Test
        @DisplayName("2 SC laps reduce total consumption by 40 % of those laps")
        void scLapsReduceFuel() {
            // No SC: 30 × 2.5 = 75.0 + buffer = 77.5
            // With SC: 30 × 2.5 + 2 × 2.5 × 0.6 = 75.0 + 3.0 = 78.0 + buffer = 80.5
            FuelCalculator.Result r = FuelCalculator.calculate(30, 110.0, 2.5, 2, 1);
            assertEquals(78.0, r.totalFuelNeeded(), 0.01);
            assertEquals(80.5, r.totalWithBuffer(), 0.01);
        }

        @Test
        @DisplayName("zero SC laps: no reduction applied")
        void noScLaps() {
            FuelCalculator.Result r = FuelCalculator.calculate(10, 50.0, 2.0, 0, 0);
            assertEquals(20.0, r.totalFuelNeeded(), 0.01);  // 10 × 2.0
            assertEquals(22.0, r.totalWithBuffer(), 0.01);  // + 1 buffer lap
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Stints = pitStops + 1
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("pit-stop count affects fuel per stint")
    class PitStops {

        @Test
        @DisplayName("0 pit stops = 1 stint: all fuel loaded at the start")
        void noPitStop() {
            FuelCalculator.Result r = FuelCalculator.calculate(10, 50.0, 2.0, 0, 0);
            assertEquals(22.0, r.fuelPerStint(), 0.01);  // 22 / 1
        }

        @Test
        @DisplayName("1 pit stop = 2 stints: fuel split in half")
        void onePitStop() {
            FuelCalculator.Result r = FuelCalculator.calculate(10, 50.0, 2.0, 0, 1);
            assertEquals(11.0, r.fuelPerStint(), 0.01);  // 22 / 2
        }

        @Test
        @DisplayName("2 pit stops = 3 stints: fuel split in thirds")
        void twoPitStops() {
            FuelCalculator.Result r = FuelCalculator.calculate(10, 50.0, 2.0, 0, 2);
            assertEquals(22.0 / 3, r.fuelPerStint(), 0.01);  // 22 / 3
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Margin — DEMO HIGHLIGHT
    // A negative margin means you WILL run out of fuel. The test makes
    // this invariant explicit — it's the whole point of the calculator.
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("margin vs tank capacity")
    class Margin {

        @Test
        @DisplayName("positive margin: tank is large enough")
        void positiveMargin() {
            FuelCalculator.Result r = FuelCalculator.calculate(10, 50.0, 2.0, 0, 0);
            assertTrue(r.marginVsTank() > 0, "Should have spare capacity");
            assertEquals(28.0, r.marginVsTank(), 0.01);  // 50 - 22
        }

        @Test
        @DisplayName("negative margin: fuel deficit — will run out before the end")
        void negativeMargin_fuelDeficit() {
            // 50 laps × 3.0 L = 150 + buffer = 153 L, but tank is only 100 L
            FuelCalculator.Result r = FuelCalculator.calculate(50, 100.0, 3.0, 0, 0);
            assertTrue(r.marginVsTank() < 0, "Should be a deficit");
        }

        @Test
        @DisplayName("exactly zero margin: just enough fuel, no spare")
        void zeroMargin() {
            // total = 10 × 2.0 + buffer(2.0) = 22.0, tank = 22.0
            FuelCalculator.Result r = FuelCalculator.calculate(10, 22.0, 2.0, 0, 0);
            assertEquals(0.0, r.marginVsTank(), 0.01);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Input validation — tests define the contract for callers
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("input validation")
    class Validation {

        @Test
        @DisplayName("zero race laps throws")
        void zeroRaceLaps() {
            assertThrows(IllegalArgumentException.class,
                    () -> FuelCalculator.calculate(0, 110.0, 2.5, 0, 1));
        }

        @Test
        @DisplayName("negative tank capacity throws")
        void negativeTankCap() {
            assertThrows(IllegalArgumentException.class,
                    () -> FuelCalculator.calculate(30, -10.0, 2.5, 0, 1));
        }

        @Test
        @DisplayName("negative fuel per lap throws")
        void negativeFuelPerLap() {
            assertThrows(IllegalArgumentException.class,
                    () -> FuelCalculator.calculate(30, 110.0, -1.0, 0, 1));
        }

        @Test
        @DisplayName("negative SC laps throws")
        void negativeScLaps() {
            assertThrows(IllegalArgumentException.class,
                    () -> FuelCalculator.calculate(30, 110.0, 2.5, -1, 1));
        }

        @Test
        @DisplayName("negative pit stops throws")
        void negativePitStops() {
            assertThrows(IllegalArgumentException.class,
                    () -> FuelCalculator.calculate(30, 110.0, 2.5, 0, -1));
        }
    }
}
