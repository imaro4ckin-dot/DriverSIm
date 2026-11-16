package org.example.drivesimui.service;

import org.example.drivesimui.model.TelemetryLap;
import org.example.drivesimui.model.TelemetrySample;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Reads full telemetry data from iRacing .ibt binary files.
 *
 * The ibt format:
 *   - Main header (little-endian int32 fields)
 *   - Variable header array describing every telemetry channel
 *   - YAML session-info string
 *   - Data section: fixed-size records at ~60 Hz
 *
 * This reader extracts Speed, Throttle, Brake, Gear, LapDistPct, and
 * (optionally) WorldVelX/WorldVelY for a 2-D track map.
 */
public class IbtTelemetryReader {

    // ── ibt channel names ────────────────────────────────────────────────
    private static final String CH_SPEED      = "Speed";
    private static final String CH_THROTTLE   = "Throttle";
    private static final String CH_BRAKE      = "Brake";
    private static final String CH_GEAR       = "Gear";
    private static final String CH_LAP_DIST   = "LapDistPct";
    private static final String CH_VEL_X      = "WorldVelX";
    private static final String CH_VEL_Y      = "WorldVelY";

    // ── ibt variable types ────────────────────────────────────────────────
    private static final int IBT_FLOAT  = 4;
    private static final int IBT_DOUBLE = 5;
    private static final int IBT_INT    = 2;
    private static final int IBT_BOOL   = 1;

    /** Metadata for one telemetry channel extracted from the variable header. */
    private record VarDef(int type, int offset, int count, String name) {}

    // ────────────────────────────────────────────────────────────────────

    /**
     * Parses all laps from an iRacing .ibt file.
     *
     * @param ibtFile the .ibt file to read
     * @return one {@link TelemetryLap} per detected lap (lap boundary = LapDistPct
     *         crosses from >0.90 back to <0.05)
     */
    public List<TelemetryLap> readLaps(File ibtFile) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(ibtFile, "r")) {
            // ── Step 1: validate ──────────────────────────────────────────
            raf.seek(0);
            int version = readInt(raf);
            if (version < 1 || version > 5) {
                throw new IOException("Not a valid iRacing .ibt file (version=" + version + ")");
            }

            // ── Step 2: main header fields ────────────────────────────────
            raf.seek(8);
            int tickRate        = readInt(raf);   // offset  8
            raf.seek(16);
            int sessionInfoLen  = readInt(raf);   // offset 16
            int sessionInfoOff  = readInt(raf);   // offset 20
            int numVars         = readInt(raf);   // offset 24
            int varHeaderOff    = readInt(raf);   // offset 28
            raf.seek(36);
            int bufLen          = readInt(raf);   // offset 36 — bytes per record
            raf.seek(48);
            long bufOffset      = Integer.toUnsignedLong(readInt(raf)); // offset 48 — data start

            if (tickRate <= 0) tickRate = 60;
            double tickMs = 1000.0 / tickRate;

            // ── Step 3: session YAML (track/car names) ────────────────────
            raf.seek(sessionInfoOff);
            byte[] yamlBytes = new byte[Math.min(sessionInfoLen, 65536)];
            raf.readFully(yamlBytes);
            String yaml     = new String(yamlBytes, StandardCharsets.UTF_8);
            String trackName = extractYaml(yaml, "TrackDisplayName",
                                extractYaml(yaml, "TrackName", "Unknown"));
            String carName   = extractYaml(yaml, "CarScreenName",
                                extractYaml(yaml, "CarPath", "Unknown"));

            // ── Step 4: read variable header array ───────────────────────
            Map<String, VarDef> varMap = new HashMap<>();
            for (int i = 0; i < numVars; i++) {
                long base = (long) varHeaderOff + i * 144L;
                raf.seek(base);
                int type    = readInt(raf);   // +0
                int varOff  = readInt(raf);   // +4
                int count   = readInt(raf);   // +8
                // +12 countAsTime, +16-47 padding — skip
                raf.seek(base + 48);
                String name = readFixedString(raf, 32);
                // +80 desc (32), +112 unit (32) — skip
                if (!name.isEmpty()) {
                    varMap.put(name, new VarDef(type, varOff, count, name));
                }
            }

            // ── Step 5: locate required channels ─────────────────────────
            VarDef speedVar  = requireChannel(varMap, CH_SPEED);
            VarDef thrVar    = requireChannel(varMap, CH_THROTTLE);
            VarDef brkVar    = requireChannel(varMap, CH_BRAKE);
            VarDef gearVar   = varMap.get(CH_GEAR);
            VarDef distVar   = varMap.get(CH_LAP_DIST);
            VarDef velXVar   = varMap.get(CH_VEL_X);
            VarDef velYVar   = varMap.get(CH_VEL_Y);
            boolean hasPos   = (velXVar != null && velYVar != null);

            // ── Step 6: record count ──────────────────────────────────────
            long fileSize    = raf.length();
            long dataLen     = fileSize - bufOffset;
            if (bufLen <= 0 || dataLen <= 0) throw new IOException("No data records in .ibt file.");
            long recordCount = dataLen / bufLen;

            // Down-sample stride so we store at most 6000 samples per lap
            int stride = (int) Math.max(1, Math.ceil(recordCount / 6000.0));

            // ── Step 7: read records, split into laps ─────────────────────
            List<List<TelemetrySample>> allLaps = new ArrayList<>();
            List<TelemetrySample>       current = new ArrayList<>();
            double prevDist = -1.0;
            double timeMs   = 0.0;
            double intX = 0.0, intY = 0.0;

            byte[]     recBuf = new byte[bufLen];

            for (long i = 0; i < recordCount; i++) {
                raf.seek(bufOffset + i * bufLen);
                raf.readFully(recBuf);
                ByteBuffer bb = ByteBuffer.wrap(recBuf).order(ByteOrder.LITTLE_ENDIAN);

                double lapDist   = distVar != null ? getFloat(bb, distVar) : (timeMs / 90000.0);
                double speedMs_  = getFloat(bb, speedVar);
                double throttle  = getFloat(bb, thrVar);
                double brake     = getFloat(bb, brkVar);
                int    gear      = gearVar != null ? getInt(bb, gearVar) : 0;
                double speedKmh  = speedMs_ * 3.6;

                double posX = Double.NaN, posY = Double.NaN;
                if (hasPos) {
                    double vx = getFloat(bb, velXVar);
                    double vy = getFloat(bb, velYVar);
                    intX += vx * (tickMs / 1000.0);
                    intY += vy * (tickMs / 1000.0);
                    posX = intX;
                    posY = intY;
                }

                // Lap boundary: dist resets from high to low
                if (prevDist > 0.90 && lapDist < 0.05 && !current.isEmpty()) {
                    allLaps.add(current);
                    current = new ArrayList<>();
                    timeMs = 0.0;
                    intX = 0.0; intY = 0.0;
                }

                if (i % stride == 0) {
                    current.add(new TelemetrySample(
                        (long) timeMs, lapDist, speedKmh,
                        throttle, brake, gear, posX, posY
                    ));
                }

                prevDist  = lapDist;
                timeMs   += tickMs;
            }
            if (!current.isEmpty()) allLaps.add(current);

            // ── Step 8: build TelemetryLap objects ────────────────────────
            List<TelemetryLap> result = new ArrayList<>();
            for (int i = 0; i < allLaps.size(); i++) {
                result.add(new TelemetryLap(
                    ibtFile.getName(), "IBT",
                    i + 1, trackName, carName,
                    Collections.unmodifiableList(allLaps.get(i))
                ));
            }
            return result;
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private VarDef requireChannel(Map<String, VarDef> map, String name) throws IOException {
        VarDef v = map.get(name);
        if (v == null) throw new IOException("Required telemetry channel not found in .ibt: " + name);
        return v;
    }

    private double getFloat(ByteBuffer bb, VarDef v) {
        if (v.offset() + 4 > bb.capacity()) return 0.0;
        if (v.type() == IBT_DOUBLE) return bb.getDouble(v.offset());
        return bb.getFloat(v.offset());
    }

    private int getInt(ByteBuffer bb, VarDef v) {
        if (v.offset() + 4 > bb.capacity()) return 0;
        if (v.type() == IBT_BOOL) return bb.get(v.offset()) & 0xFF;
        return bb.getInt(v.offset());
    }

    private int readInt(RandomAccessFile raf) throws IOException {
        byte[] b = new byte[4];
        raf.readFully(b);
        return (b[0] & 0xFF) | ((b[1] & 0xFF) << 8) | ((b[2] & 0xFF) << 16) | ((b[3] & 0xFF) << 24);
    }

    private String readFixedString(RandomAccessFile raf, int maxLen) throws IOException {
        byte[] buf = new byte[maxLen];
        raf.readFully(buf);
        int end = 0;
        while (end < buf.length && buf[end] != 0) end++;
        return new String(buf, 0, end, StandardCharsets.US_ASCII).trim();
    }

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
