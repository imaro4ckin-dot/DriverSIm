package org.example.drivesimui.service;

import org.example.drivesimui.model.RaceSession;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Generic CSV race result importer.
 *
 * Supports:
 * - iRacing race-result CSV exports (column headers in first non-comment row)
 * - MoTeC CSV channel exports (ACC / LMU)
 * - Generic "Track, LapTime, Car" CSVs
 */
class CsvRaceResultImporter {

    List<RaceSession> importFile(File f, String platform) throws Exception {
        List<RaceSession> result = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            // Find header row — skip comment/empty lines
            String headerLine = null;
            String line;
            List<String> dataLines = new ArrayList<>();
            while ((line = br.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#") || line.startsWith("//")) continue;
                if (headerLine == null) { headerLine = line; }
                else dataLines.add(line);
            }
            if (headerLine == null) throw new Exception("Empty CSV file: " + f.getName());

            String[] headers = split(headerLine);
            int trackIdx   = findCol(headers, "track", "TrackName", "track_name", "Track");
            int lapIdx     = findCol(headers, "lap_time", "LapTime", "bestLap", "BestLapTime", "time_ms");
            int carIdx     = findCol(headers, "car", "CarName", "car_name", "Vehicle", "car_screen_name");
            int typeIdx    = findCol(headers, "session_type", "SessionType", "type", "Type");
            int posIdx     = findCol(headers, "finish_pos", "FinishPos", "Position", "position");
            int dateIdx    = findCol(headers, "date", "Date", "session_date");

            // If we can't find at minimum a lap time column, create a single aggregate session
            if (lapIdx < 0 && trackIdx < 0) {
                // Fall back: treat entire file as one unknown session
                RaceSession s = new RaceSession();
                s.setPlatform(platform);
                s.setTrackName(f.getName().replace(".csv", "").replace("_", " "));
                s.setSessionType("Race");
                s.setSessionDate(LocalDate.ofEpochDay(f.lastModified() / 86_400_000));
                s.setExternalSessionId(f.getName());
                result.add(s);
                return result;
            }

            // Group by track+date into sessions, take best lap
            java.util.Map<String, RaceSession> sessionMap = new java.util.LinkedHashMap<>();
            for (String dataLine : dataLines) {
                if (dataLine.isBlank()) continue;
                String[] cols = split(dataLine);

                String track   = col(cols, trackIdx,  "Unknown Track");
                String car     = col(cols, carIdx,    null);
                String type    = col(cols, typeIdx,   "Race");
                int    pos     = parseIntSafe(col(cols, posIdx, "0"));
                String dateStr = col(cols, dateIdx,   null);

                long lapMs = 0;
                if (lapIdx >= 0 && lapIdx < cols.length) {
                    lapMs = parseTimeMs(cols[lapIdx].trim());
                }

                String key = track + "|" + dateStr;
                RaceSession s = sessionMap.computeIfAbsent(key, k -> {
                    RaceSession ns = new RaceSession();
                    ns.setPlatform(platform);
                    ns.setTrackName(track);
                    ns.setCarName(car);
                    ns.setSessionType(type);
                    ns.setFinishPosition(pos);
                    ns.setSessionDate(parseDateSafe(dateStr, f));
                    ns.setExternalSessionId(f.getName());
                    return ns;
                });

                if (lapMs > 0 && (s.getBestLapMs() == 0 || lapMs < s.getBestLapMs())) {
                    s.setBestLapMs(lapMs);
                }
            }

            result.addAll(sessionMap.values());
        }
        return result;
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private String[] split(String line) {
        return line.split(",", -1);
    }

    private int findCol(String[] headers, String... candidates) {
        for (String candidate : candidates) {
            for (int i = 0; i < headers.length; i++) {
                if (headers[i].trim().equalsIgnoreCase(candidate)) return i;
            }
        }
        return -1;
    }

    private String col(String[] cols, int idx, String fallback) {
        if (idx < 0 || idx >= cols.length) return fallback;
        String v = cols[idx].trim().replace("\"", "");
        return v.isEmpty() ? fallback : v;
    }

    /**
     * Parse a lap time that may be:
     * - Raw milliseconds: "92345"
     * - m:ss.mmm: "1:32.345"
     * - m.ss.mmm: "1.32.345"
     * - Seconds float: "92.345"
     */
    private long parseTimeMs(String raw) {
        if (raw == null || raw.isBlank()) return 0;
        try {
            // m:ss.mmm
            if (raw.contains(":")) {
                String[] parts = raw.split(":");
                double mins = Double.parseDouble(parts[0]);
                double secs = Double.parseDouble(parts[1]);
                return (long) ((mins * 60 + secs) * 1000);
            }
            // Large raw ms value (iRacing uses 1/10000 s)
            double v = Double.parseDouble(raw);
            if (v > 600_000) return (long) (v / 10); // 1/10000s to ms
            if (v > 600)     return (long) v;          // already ms
            return (long) (v * 1000);                  // seconds float
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private int parseIntSafe(String s) {
        if (s == null) return 0;
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return 0; }
    }

    private LocalDate parseDateSafe(String dateStr, File f) {
        if (dateStr != null && !dateStr.isBlank()) {
            try { return LocalDate.parse(dateStr.substring(0, Math.min(10, dateStr.length()))); }
            catch (Exception ignored) {}
        }
        return LocalDate.ofEpochDay(f.lastModified() / 86_400_000);
    }
}
