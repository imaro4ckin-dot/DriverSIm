package org.example.drivesimui;

import java.time.LocalDate;

public class LapTime implements DataExport {

    // ── Core fields ──────────────────────────────────────────────────
    private String    trackName;
    private long      timeInMilSec;
    private LocalDate dateRecorded;
    private Vehicle   carUsed;
    private long      deltaMs = 0;

    // ── Sector times (0 = not recorded) ─────────────────────────────
    private long s1Ms = 0;
    private long s2Ms = 0;
    private long s3Ms = 0;

    // ── Weather condition ────────────────────────────────────────────
    public enum Weather { DRY, DAMP, WET, COLD, HOT }
    private Weather weather = Weather.DRY;

    // ─────────────────────────────────────────────────────────────────
    public LapTime(String trackName, long timeInMilSec, Vehicle carUsed) {
        this.trackName    = trackName;
        this.timeInMilSec = timeInMilSec;
        this.carUsed      = carUsed;
        this.dateRecorded = LocalDate.now();
    }

    // ── Formatted lap time ───────────────────────────────────────────
    public String getFormatedTime() {
        return formatMs(timeInMilSec);
    }

    // ── Formatted sector times ───────────────────────────────────────
    public String getFormattedS1() { return s1Ms > 0 ? formatMs(s1Ms) : "—"; }
    public String getFormattedS2() { return s2Ms > 0 ? formatMs(s2Ms) : "—"; }
    public String getFormattedS3() { return s3Ms > 0 ? formatMs(s3Ms) : "—"; }

    /** True if all 3 sectors were recorded. */
    public boolean hasSectors() { return s1Ms > 0 && s2Ms > 0 && s3Ms > 0; }

    // ── Delta ────────────────────────────────────────────────────────
    public String getFormattedDelta() {
        if (deltaMs == 0) return "—";
        double seconds = deltaMs / 1000.0;
        if (deltaMs > 0) return String.format("+%.3f", seconds);
        return String.format("%.3f", seconds);
    }

    // ── CSV / JSON ───────────────────────────────────────────────────
    @Override
    public String toCSV() {
        return trackName + "," + timeInMilSec + "," +
               dateRecorded.toString() + "," +
               carUsed.getMake() + "-" + carUsed.getModel();
    }

    @Override
    public String toJSON() {
        Vehicle car = getCarUsed();
        return "{\n" +
               "  \"track\": \""      + trackName      + "\",\n" +
               "  \"lapTimeMs\": "    + timeInMilSec   + ",\n" +
               "  \"weather\": \""    + weather.name() + "\",\n" +
               "  \"s1Ms\": "         + s1Ms            + ",\n" +
               "  \"s2Ms\": "         + s2Ms            + ",\n" +
               "  \"s3Ms\": "         + s3Ms            + ",\n" +
               "  \"car\": {\n" +
               "    \"type\": \""   + car.getCarType() + "\",\n" +
               "    \"make\": \""   + car.getMake()    + "\",\n" +
               "    \"model\": \""  + car.getModel()   + "\",\n" +
               "    \"setup\": {\n" +
               "      "             + car.getSetupJSON() + "\n" +
               "    }\n" +
               "  }\n" +
               "}";
    }

    // ── Helpers ──────────────────────────────────────────────────────
    private static String formatMs(long ms) {
        long minutes = (ms / 1000) / 60;
        long seconds = (ms / 1000) % 60;
        long millis  = ms % 1000;
        return String.format("%d:%02d.%03d", minutes, seconds, millis);
    }

    // ── Getters / Setters ────────────────────────────────────────────
    public String    getTrackName()           { return trackName; }
    public long      getTimeInMilliseconds()  { return timeInMilSec; }
    public Vehicle   getCarUsed()             { return carUsed; }
    public LocalDate getDateRecorded()        { return dateRecorded; }

    public long      getDeltaMs()             { return deltaMs; }
    public void      setDeltaMs(long deltaMs) { this.deltaMs = deltaMs; }

    public long      getS1Ms()                { return s1Ms; }
    public void      setS1Ms(long s1Ms)       { this.s1Ms = s1Ms; }

    public long      getS2Ms()                { return s2Ms; }
    public void      setS2Ms(long s2Ms)       { this.s2Ms = s2Ms; }

    public long      getS3Ms()                { return s3Ms; }
    public void      setS3Ms(long s3Ms)       { this.s3Ms = s3Ms; }

    public Weather   getWeather()             { return weather; }
    public void      setWeather(Weather w)    { this.weather = w; }
}
