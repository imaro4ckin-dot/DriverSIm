package org.example.drivesimui.service;

import org.example.drivesimui.model.TelemetryLap;
import org.example.drivesimui.model.TelemetrySample;

import java.io.*;
import java.util.*;

/**
 * Imports telemetry channels from MoTeC-style CSV exports.
 *
 * Supports:
 *   - Single-row header:  Speed, Throttle, Brake, ...
 *   - Two-row header:     channel names on row 1, units on row 2
 *   - Column name variations: case-insensitive, common aliases
 *
 * Required columns: Speed (or alias), Throttle (or alias), Brake (or alias).
 * Optional: Time, LapDistPct, Gear, PosX, PosY.
 */
public class CsvTelemetryImporter {

    public TelemetryLap importFile(File csvFile) throws Exception {
        List<String> lines = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(csvFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (!line.isBlank() && !line.startsWith("#") && !line.startsWith("//"))
                    lines.add(line);
            }
        }
        if (lines.isEmpty()) throw new Exception("Empty CSV file: " + csvFile.getName());

        // ── Header detection ──────────────────────────────────────────────
        String[] headers = split(lines.get(0));
        int dataStart = 1;

        // If row 2 contains no numeric values, treat it as a units row and skip
        if (lines.size() > 1 && !hasNumeric(lines.get(1))) {
            dataStart = 2;  // skip units row
        }

        // ── Column mapping ────────────────────────────────────────────────
        int timeIdx     = findCol(headers, "Time", "time_s", "Timestamp", "t");
        int speedIdx    = findCol(headers, "Speed", "speed_kmh", "v_Vehicle", "GPS_Speed", "speed");
        int throttleIdx = findCol(headers, "Throttle", "Throttle_Pos", "Thr", "throttle_pct", "throttle");
        int brakeIdx    = findCol(headers, "Brake", "Brake_Pos", "Brk", "brake_pct", "brake");
        int gearIdx     = findCol(headers, "Gear", "gear");
        int distIdx     = findCol(headers, "LapDistPct", "distance_pct", "Dist", "LapDist", "lap_dist_pct");
        int posXIdx     = findCol(headers, "PosX", "GPS_PosX", "pos_x", "X");
        int posYIdx     = findCol(headers, "PosY", "GPS_PosY", "pos_y", "Y");

        if (speedIdx < 0 || throttleIdx < 0 || brakeIdx < 0) {
            throw new Exception("CSV missing required channels (Speed, Throttle, Brake). "
                + "Found: " + Arrays.toString(headers));
        }

        // ── Detect if speed is already km/h or m/s ───────────────────────
        // We'll decide per-value: if speed > 100 assume km/h, else m/s
        // (handled below per-row)

        // ── Parse data rows ───────────────────────────────────────────────
        List<TelemetrySample> samples = new ArrayList<>();
        double assumedTickMs = 1000.0 / 60.0;  // fallback 60 Hz
        double timeMs = 0.0;

        for (int r = dataStart; r < lines.size(); r++) {
            String[] cols = split(lines.get(r));
            if (cols.length < 2) continue;

            // Time
            if (timeIdx >= 0 && timeIdx < cols.length) {
                timeMs = parseDouble(cols, timeIdx, timeMs) * 1000.0;
            } else {
                timeMs = (r - dataStart) * assumedTickMs;
            }

            double rawSpeed  = parseDouble(cols, speedIdx,    0.0);
            double throttle  = parseDouble(cols, throttleIdx, 0.0);
            double brake     = parseDouble(cols, brakeIdx,    0.0);
            int    gear      = parseInt(cols, gearIdx,       0);
            double distPct   = distIdx >= 0 ? parseDouble(cols, distIdx, 0.0) : 0.0;
            double posX      = posXIdx >= 0 ? parseDouble(cols, posXIdx, Double.NaN) : Double.NaN;
            double posY      = posYIdx >= 0 ? parseDouble(cols, posYIdx, Double.NaN) : Double.NaN;

            // Normalise: throttle/brake >1 → percentage
            if (throttle > 1.0) throttle /= 100.0;
            if (brake    > 1.0) brake    /= 100.0;
            throttle = Math.max(0.0, Math.min(1.0, throttle));
            brake    = Math.max(0.0, Math.min(1.0, brake));

            // Speed: if value looks like m/s (< 80 and not obviously km/h) convert
            double speedKmh = rawSpeed < 80.0 ? rawSpeed * 3.6 : rawSpeed;

            samples.add(new TelemetrySample(
                (long) timeMs, distPct, speedKmh,
                throttle, brake, gear, posX, posY
            ));
        }

        if (samples.isEmpty()) throw new Exception("No data rows found in CSV: " + csvFile.getName());

        // ── Backfill distancePct if missing ──────────────────────────────
        if (distIdx < 0) {
            long totalMs = samples.get(samples.size() - 1).timeMs();
            if (totalMs > 0) {
                List<TelemetrySample> fixed = new ArrayList<>(samples.size());
                for (TelemetrySample s : samples) {
                    double pct = (double) s.timeMs() / totalMs;
                    fixed.add(new TelemetrySample(s.timeMs(), pct, s.speedKmh(),
                        s.throttle(), s.brake(), s.gear(), s.posX(), s.posY()));
                }
                samples = fixed;
            }
        }

        return new TelemetryLap(
            csvFile.getName(), "CSV",
            -1, "Unknown", "Unknown",
            Collections.unmodifiableList(samples)
        );
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private String[] split(String line) {
        return line.split(",", -1);
    }

    private int findCol(String[] headers, String... candidates) {
        for (String cand : candidates) {
            for (int i = 0; i < headers.length; i++) {
                if (headers[i].trim().equalsIgnoreCase(cand)) return i;
            }
        }
        return -1;
    }

    private double parseDouble(String[] cols, int idx, double fallback) {
        if (idx < 0 || idx >= cols.length) return fallback;
        try { return Double.parseDouble(cols[idx].trim()); }
        catch (NumberFormatException e) { return fallback; }
    }

    private int parseInt(String[] cols, int idx, int fallback) {
        if (idx < 0 || idx >= cols.length) return fallback;
        try { return Integer.parseInt(cols[idx].trim()); }
        catch (NumberFormatException e) { return fallback; }
    }

    /** Returns true if the line contains at least one parseable number. */
    private boolean hasNumeric(String line) {
        for (String col : split(line)) {
            try { Double.parseDouble(col.trim()); return true; }
            catch (NumberFormatException ignored) {}
        }
        return false;
    }
}
