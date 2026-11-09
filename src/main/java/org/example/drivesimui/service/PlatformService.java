package org.example.drivesimui.service;

import org.example.drivesimui.model.RaceSession;

import java.util.List;

/**
 * Common contract for platform data sources (iRacing, ACC, LMU).
 */
public interface PlatformService {

    /** Returns a human-readable platform name, e.g. "iRacing". */
    String getPlatformName();

    /** Whether this service currently has an active authenticated session. */
    boolean isAuthenticated();

    /**
     * Fetch the most recent races/sessions from the remote platform.
     * Implementations that do not support live sync should throw UnsupportedOperationException.
     */
    List<RaceSession> fetchRecentRaces() throws Exception;
}
