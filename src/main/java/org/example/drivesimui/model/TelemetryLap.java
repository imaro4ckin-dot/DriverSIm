package org.example.drivesimui.model;

import java.util.List;

/**
 * One lap's worth of telemetry data, built from a single .ibt or CSV import.
 */
public class TelemetryLap {

    private final String                 sourceFile;  // file name for display
    private final String                 source;      // "IBT" or "CSV"
    private final int                    lapNumber;   // 1-based; -1 if unknown
    private final String                 trackName;
    private final String                 carName;
    private final List<TelemetrySample>  samples;     // ordered by timeMs ascending

    public TelemetryLap(String sourceFile, String source,
                        int lapNumber, String trackName, String carName,
                        List<TelemetrySample> samples) {
        this.sourceFile = sourceFile;
        this.source     = source;
        this.lapNumber  = lapNumber;
        this.trackName  = trackName;
        this.carName    = carName;
        this.samples    = samples;
    }

    public String                getSourceFile() { return sourceFile; }
    public String                getSource()     { return source; }
    public int                   getLapNumber()  { return lapNumber; }
    public String                getTrackName()  { return trackName; }
    public String                getCarName()    { return carName; }
    public List<TelemetrySample> getSamples()    { return samples; }

    /** Total lap duration in milliseconds (last sample's timeMs). */
    public long durationMs() {
        return samples.isEmpty() ? 0L : samples.get(samples.size() - 1).timeMs();
    }

    /** Displayed in the lap-selector ComboBox. */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (lapNumber > 0) sb.append("Lap ").append(lapNumber).append("  ");
        if (trackName != null && !trackName.isEmpty() && !trackName.equals("Unknown"))
            sb.append(trackName).append("  ");
        sb.append("[").append(source).append("]  ");
        sb.append(sourceFile);
        return sb.toString();
    }
}
