package org.example.drivesimui.util;

/**
 * Shared time formatting and parsing utilities used across all controllers.
 */
public final class TimeFormatter {

    private TimeFormatter() {}

    /**
     * Formats a millisecond value as {@code m:ss.mmm}.
     */
    public static String formatMs(long ms) {
        long minutes = (ms / 1000) / 60;
        long seconds = (ms / 1000) % 60;
        long millis  = ms % 1000;
        return String.format("%d:%02d.%03d", minutes, seconds, millis);
    }

    /**
     * Parses a lap time string into milliseconds.
     * Accepted formats:
     *   m.ss.mmm  →  1.24.500  (dot-separated, 3-digit ms)
     *   m.ss.mm   →  1.24.50   (dot-separated, 2-digit ms treated as centiseconds × 10)
     *   m:ss.mmm  →  1:24.500  (colon-minute, dot-decimal)
     *   m:ss.mm   →  1:24.50
     *   raw ms    →  84500
     */
    public static long parseTimeInput(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Please enter a lap time.");
        }

        // Format: m.ss.mmm or m.ss.mm  (two dots)
        if (raw.chars().filter(c -> c == '.').count() == 2) {
            String[] parts = raw.split("\\.", -1);
            try {
                long minutes = Long.parseLong(parts[0]);
                long seconds = Long.parseLong(parts[1]);
                String msStr = parts[2];
                long millis  = msStr.length() == 2
                        ? Long.parseLong(msStr) * 10
                        : Long.parseLong(msStr);
                if (seconds >= 60)  throw new IllegalArgumentException("Seconds must be 0–59.");
                if (millis  >= 1000) throw new IllegalArgumentException("Milliseconds must be 0–999.");
                return (minutes * 60_000L) + (seconds * 1000L) + millis;
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid format. Use  m.ss.mmm  e.g.  1.24.500");
            }
        }

        // Format: m:ss.mmm or m:ss.mm  (colon then dot)
        if (raw.contains(":") && raw.contains(".")) {
            String[] colonParts = raw.split(":", 2);
            String[] dotParts   = colonParts[1].split("\\.", 2);
            try {
                long minutes = Long.parseLong(colonParts[0]);
                long seconds = Long.parseLong(dotParts[0]);
                String msStr = dotParts[1];
                long millis  = msStr.length() == 2
                        ? Long.parseLong(msStr) * 10
                        : Long.parseLong(msStr);
                if (seconds >= 60)  throw new IllegalArgumentException("Seconds must be 0–59.");
                if (millis  >= 1000) throw new IllegalArgumentException("Milliseconds must be 0–999.");
                return (minutes * 60_000L) + (seconds * 1000L) + millis;
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid format. Use  m:ss.mmm  e.g.  1:24.500");
            }
        }

        // Fallback: raw milliseconds integer
        try {
            long ms = Long.parseLong(raw);
            if (ms <= 0) throw new IllegalArgumentException("Lap time must be positive.");
            return ms;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Invalid time.\nAccepted formats:\n  1.24.500  or  1:24.500  or  84500 (ms)");
        }
    }
}
