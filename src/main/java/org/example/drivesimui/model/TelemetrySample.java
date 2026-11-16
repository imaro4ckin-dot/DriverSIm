package org.example.drivesimui.model;

/**
 * A single telemetry sample captured at one tick during a lap.
 *
 * posX / posY are world-space coordinates in metres (estimated by integrating
 * WorldVelX / WorldVelY from ibt). Set to Double.NaN when unavailable (CSV
 * imports without position columns, or ibt files without WorldVelX/Y channels).
 */
public record TelemetrySample(
        long   timeMs,       // elapsed ms from lap start
        double distancePct,  // 0.0–1.0 fraction of lap completed
        double speedKmh,     // km/h
        double throttle,     // 0.0–1.0
        double brake,        // 0.0–1.0
        int    gear,         // -1=reverse, 0=neutral, 1+
        double posX,         // world X (metres), NaN if unavailable
        double posY          // world Y (metres), NaN if unavailable
) {}
