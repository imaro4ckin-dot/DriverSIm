package org.example.drivesimui.service;

/**
 * Pure fuel-strategy calculation logic, extracted from FuelStrategyController
 * so that it can be tested without a JavaFX environment.
 *
 * TDD DEMO — this class was *created* by TDD:
 *   1. We tried to test FuelStrategyController.calculate() — couldn't, it's UI code.
 *   2. We wrote a test for FuelCalculator.calculate() first (RED).
 *   3. We extracted the math here to make it pass (GREEN).
 *   4. FuelStrategyController now delegates to this class (no logic duplication).
 */
public final class FuelCalculator {

    private FuelCalculator() {}

    /**
     * Result record — immutable, easy to assert against in tests.
     */
    public record Result(
            double totalFuelNeeded,   // base race consumption (no buffer)
            double totalWithBuffer,   // + 1 extra lap as safety margin
            double fuelPerStint,      // totalWithBuffer / stints
            double marginVsTank       // tankCapacity - totalWithBuffer  (negative = deficit)
    ) {}

    /**
     * @param raceLaps    total race laps
     * @param tankCap     tank capacity in litres / kg
     * @param fuelPerLap  average consumption per lap
     * @param scLaps      estimated safety-car laps (consume ~60 % of normal fuel)
     * @param pitStops    number of planned pit stops  (stints = pitStops + 1)
     */
    public static Result calculate(int raceLaps, double tankCap,
                                   double fuelPerLap, int scLaps, int pitStops) {
        if (raceLaps <= 0)   throw new IllegalArgumentException("Race must have at least 1 lap.");
        if (tankCap  <= 0)   throw new IllegalArgumentException("Tank capacity must be positive.");
        if (fuelPerLap <= 0) throw new IllegalArgumentException("Fuel per lap must be positive.");
        if (scLaps < 0)      throw new IllegalArgumentException("Safety-car laps cannot be negative.");
        if (pitStops < 0)    throw new IllegalArgumentException("Pit stops cannot be negative.");

        int    stints          = pitStops + 1;
        double totalFuelNeeded = (raceLaps * fuelPerLap) + (scLaps * fuelPerLap * 0.6);
        double totalWithBuffer = totalFuelNeeded + fuelPerLap;   // +1 lap safety buffer
        double fuelPerStint    = totalWithBuffer / stints;
        double margin          = tankCap - totalWithBuffer;

        return new Result(totalFuelNeeded, totalWithBuffer, fuelPerStint, margin);
    }
}
