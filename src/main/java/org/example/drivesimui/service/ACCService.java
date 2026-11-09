package org.example.drivesimui.service;

import org.example.drivesimui.model.RaceSession;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * ACC data service — file-import only (no public API).
 *
 * Reads ACC results JSON from ~/Documents/Assetto Corsa Competizione/Results/.
 * Each file contains a {@code sessionResult} object with leaderboard lines.
 */
public class ACCService implements PlatformService {

    @Override public String  getPlatformName() { return "ACC"; }
    @Override public boolean isAuthenticated()  { return true; } // no auth needed

    @Override
    public List<RaceSession> fetchRecentRaces() {
        throw new UnsupportedOperationException("ACC has no public API — use importSessionJson() instead.");
    }

    // ── File import ────────────────────────────────────────────────────────

    /**
     * Import a single ACC results JSON file.
     * The JSON root has: serverName, sessionType, sessionResult.leaderBoardLines[].
     */
    public List<RaceSession> importSessionJson(File f) throws Exception {
        String raw = Files.readString(f.toPath());
        return parseAccJson(raw, f.getName());
    }

    /** Scan a directory for all *.json files and import them. */
    public List<RaceSession> importFolder(File dir) throws Exception {
        List<RaceSession> all = new ArrayList<>();
        File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".json"));
        if (files == null) return all;
        for (File f : files) {
            try { all.addAll(importSessionJson(f)); }
            catch (Exception e) {
                System.err.println("[ACCService] Skipping " + f.getName() + ": " + e.getMessage());
            }
        }
        return all;
    }

    /** Import a MoTeC CSV export (best-effort: single session per file). */
    public List<RaceSession> importMotecCsv(File f) throws Exception {
        return new CsvRaceResultImporter().importFile(f, "ACC");
    }

    // ── JSON parsing ───────────────────────────────────────────────────────

    private List<RaceSession> parseAccJson(String raw, String fileName) throws IOException {
        List<RaceSession> result = new ArrayList<>();
        try {
            JSONObject root = new JSONObject(raw);

            String sessionType = root.optString("sessionType", "RACE");
            // ACC encodes: R=Race, Q=Qualify, P=Practice, FP=Free Practice
            String type = switch (sessionType) {
                case "R"  -> "Race";
                case "Q"  -> "Qualifying";
                case "FP", "P" -> "Practice";
                default -> sessionType;
            };

            // Extract track from file name (ACC uses track_timestamp.json naming)
            String trackName = extractTrackFromFileName(fileName);

            JSONObject sessionResult = root.optJSONObject("sessionResult");
            if (sessionResult == null) throw new IOException("No sessionResult in " + fileName);

            // Best lap over all drivers
            long bestLapOverall = Long.MAX_VALUE;
            JSONArray lines = sessionResult.optJSONArray("leaderBoardLines");
            if (lines == null) lines = new JSONArray();

            for (int i = 0; i < lines.length(); i++) {
                JSONObject entry  = lines.getJSONObject(i);
                JSONObject timing = entry.optJSONObject("timing");
                if (timing == null) continue;
                long best = timing.optLong("bestLap", Long.MAX_VALUE);
                if (best > 0 && best < bestLapOverall) bestLapOverall = best;
            }

            // Build one RaceSession per file (entire session)
            RaceSession s = new RaceSession();
            s.setPlatform("ACC");
            s.setTrackName(trackName);
            s.setCarName(extractCarFromJson(sessionResult));
            s.setSessionType(type);
            s.setFinishPosition(1);
            s.setTotalDrivers(lines.length());
            s.setBestLapMs(bestLapOverall == Long.MAX_VALUE ? 0 : bestLapOverall);
            s.setRawJson(raw.length() > 4000 ? raw.substring(0, 4000) : raw);
            s.setExternalSessionId(fileName);

            // Try to parse date from first line's timestamp or file name
            LocalDate date = extractDateFromFileName(fileName);
            if (date != null) s.setSessionDate(date);

            // Sector bests
            if (lines.length() > 0) {
                JSONObject first = lines.getJSONObject(0);
                JSONObject timing = first.optJSONObject("timing");
                if (timing != null) {
                    JSONArray splits = timing.optJSONArray("bestSplits");
                    if (splits != null && splits.length() >= 3) {
                        s.setS1BestMs(splits.getLong(0));
                        s.setS2BestMs(splits.getLong(1));
                        s.setS3BestMs(splits.getLong(2));
                    }
                }
            }

            result.add(s);
        } catch (Exception e) {
            throw new IOException("Failed to parse ACC JSON: " + e.getMessage(), e);
        }
        return result;
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private String extractTrackFromFileName(String fileName) {
        // ACC format: TrackName_YYYYMMDD_HHMMSS.json
        String name = fileName.replace(".json", "");
        // Remove trailing _YYYYMMDD_HHMMSS pattern
        String cleaned = name.replaceAll("_\\d{8}_\\d{6}$", "");
        return cleaned.isEmpty() ? "Unknown Track" : cleaned.replace("_", " ");
    }

    private LocalDate extractDateFromFileName(String fileName) {
        try {
            java.util.regex.Matcher m =
                    java.util.regex.Pattern.compile("(\\d{4})(\\d{2})(\\d{2})").matcher(fileName);
            if (m.find()) {
                return LocalDate.of(Integer.parseInt(m.group(1)),
                                    Integer.parseInt(m.group(2)),
                                    Integer.parseInt(m.group(3)));
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String extractCarFromJson(JSONObject sessionResult) {
        try {
            JSONArray lines = sessionResult.optJSONArray("leaderBoardLines");
            if (lines != null && lines.length() > 0) {
                JSONObject first = lines.getJSONObject(0);
                JSONObject car = first.optJSONObject("car");
                if (car != null) return car.optString("carModel", null);
            }
        } catch (Exception ignored) {}
        return null;
    }
}
