package org.example.drivesimui.service;

import org.example.drivesimui.model.RaceSession;

import java.io.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Best-effort importer for iRacing .ibt telemetry files.
 *
 * The .ibt format is a binary file with a header containing variable definitions
 * and session info, followed by data records. This implementation reads just
 * enough of the header to extract car name, track, and best lap time.
 *
 * Full ibt spec: https://gitlab.com/openfdctools/irbt2csv
 */
class IbtImporter {

    List<RaceSession> importFile(File f) throws Exception {
        List<RaceSession> result = new ArrayList<>();
        try (RandomAccessFile raf = new RandomAccessFile(f, "r")) {
            // ibt header magic bytes check (first 4 bytes: version int)
            int version = readInt(raf);
            if (version < 1 || version > 5) {
                // Not a valid ibt file
                throw new IOException("Not a valid iRacing .ibt file (unexpected version: " + version + ")");
            }

            // Skip to session info string offset (at byte 112 in the header)
            raf.seek(112);
            int sessionInfoOffset = readInt(raf);
            int sessionInfoLen    = readInt(raf);

            if (sessionInfoOffset <= 0 || sessionInfoLen <= 0 || sessionInfoOffset > raf.length()) {
                throw new IOException("Invalid session info offset in .ibt header");
            }

            raf.seek(sessionInfoOffset);
            byte[] sessionBytes = new byte[Math.min(sessionInfoLen, 65536)];
            raf.readFully(sessionBytes);
            String sessionInfo = new String(sessionBytes, java.nio.charset.StandardCharsets.UTF_8);

            RaceSession s = new RaceSession();
            s.setPlatform("IRACING");
            s.setSessionType("Practice");
            s.setSessionDate(LocalDate.ofEpochDay(f.lastModified() / 86_400_000));
            s.setExternalSessionId(f.getName());

            // Parse YAML-like session info for key fields
            s.setTrackName(extractYaml(sessionInfo, "TrackDisplayName", extractYaml(sessionInfo, "TrackName", "Unknown")));
            s.setCarName(extractYaml(sessionInfo, "CarScreenName", extractYaml(sessionInfo, "CarPath", null)));

            String sessionType = extractYaml(sessionInfo, "SessionType", null);
            if (sessionType != null) s.setSessionType(sessionType);

            result.add(s);
        } catch (IOException e) {
            // Re-wrap to propagate to caller
            throw new IOException("Failed to import .ibt: " + e.getMessage(), e);
        }
        return result;
    }

    private int readInt(RandomAccessFile raf) throws IOException {
        byte[] b = new byte[4];
        raf.readFully(b);
        // Little-endian
        return (b[0] & 0xFF) | ((b[1] & 0xFF) << 8) | ((b[2] & 0xFF) << 16) | ((b[3] & 0xFF) << 24);
    }

    /**
     * Extract a value from iRacing's YAML-like session info string.
     * Lines look like: "  TrackDisplayName: Silverstone Circuit"
     */
    private String extractYaml(String yaml, String key, String fallback) {
        String search = key + ":";
        int idx = yaml.indexOf(search);
        if (idx < 0) return fallback;
        int start = idx + search.length();
        int end   = yaml.indexOf('\n', start);
        if (end < 0) end = yaml.length();
        String val = yaml.substring(start, end).trim();
        return val.isEmpty() ? fallback : val;
    }
}
