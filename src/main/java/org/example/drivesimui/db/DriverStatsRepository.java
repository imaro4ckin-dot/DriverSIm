package org.example.drivesimui.db;

import org.example.drivesimui.model.DriverProfile;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * CRUD for the {@code driver_stats} and {@code irating_history} tables.
 */
public class DriverStatsRepository {

    private final DatabaseManager db;

    public DriverStatsRepository() {
        try {
            this.db = DatabaseManager.getInstance();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to open database: " + e.getMessage(), e);
        }
    }

    // ── Driver stats ───────────────────────────────────────────────────────

    /**
     * Inserts or replaces a driver profile record.
     * The UNIQUE constraint on (platform, customer_id) drives the upsert.
     */
    public void upsert(DriverProfile p) {
        if (p == null) return;
        String sql = """
                INSERT OR REPLACE INTO driver_stats
                  (platform, driver_name, customer_id, irating, safety_rating,
                   license_class, total_races, wins, podiums, poles,
                   avg_finish_pos, last_synced)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                """;
        try (Connection con = db.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1,  p.getPlatform());
            ps.setString(2,  p.getDriverName());
            ps.setString(3,  p.getCustomerId());
            ps.setInt(4,     p.getIrating());
            ps.setDouble(5,  p.getSafetyRating());
            ps.setString(6,  p.getLicenseClass());
            ps.setInt(7,     p.getTotalRaces());
            ps.setInt(8,     p.getWins());
            ps.setInt(9,     p.getPodiums());
            ps.setInt(10,    p.getPoles());
            ps.setDouble(11, p.getAvgFinishPos());
            ps.setString(12, LocalDate.now().toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[DriverStatsRepository] upsert failed: " + e.getMessage());
        }
    }

    /** Returns all profiles for a given platform, ordered by id descending (newest first). */
    public List<DriverProfile> loadByPlatform(String platform) {
        List<DriverProfile> result = new ArrayList<>();
        String sql = "SELECT * FROM driver_stats WHERE platform = ? ORDER BY id DESC";
        try (Connection con = db.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, platform);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) result.add(mapRow(rs));
        } catch (SQLException e) {
            System.err.println("[DriverStatsRepository] loadByPlatform failed: " + e.getMessage());
        }
        return result;
    }

    /**
     * Returns the most recently upserted profile for a platform, or {@code null} if none.
     */
    public DriverProfile findLatestByPlatform(String platform) {
        String sql = "SELECT * FROM driver_stats WHERE platform = ? ORDER BY id DESC LIMIT 1";
        try (Connection con = db.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, platform);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return mapRow(rs);
        } catch (SQLException e) {
            System.err.println("[DriverStatsRepository] findLatestByPlatform failed: " + e.getMessage());
        }
        return null;
    }

    // ── iRating history ────────────────────────────────────────────────────

    /**
     * Inserts a single iRating snapshot.
     *
     * @param date ISO date string, e.g. "2024-11-09"
     */
    public void insertIRatingSnapshot(String platform, int irating, String date) {
        String sql = "INSERT INTO irating_history (platform, irating, recorded) VALUES (?,?,?)";
        try (Connection con = db.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, platform);
            ps.setInt(2,    irating);
            ps.setString(3, date);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[DriverStatsRepository] insertIRatingSnapshot failed: " + e.getMessage());
        }
    }

    /**
     * Returns iRating history as a list of 2-element arrays: {@code [epochDay, irating]}.
     * Ordered oldest-first so callers can feed directly into a line chart.
     */
    public List<long[]> loadIRatingHistory(String platform) {
        List<long[]> result = new ArrayList<>();
        String sql = "SELECT recorded, irating FROM irating_history WHERE platform = ? ORDER BY recorded ASC";
        try (Connection con = db.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, platform);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                long epochDay = 0;
                try {
                    epochDay = LocalDate.parse(rs.getString("recorded")).toEpochDay();
                } catch (Exception ignored) {}
                result.add(new long[]{epochDay, rs.getLong("irating")});
            }
        } catch (SQLException e) {
            System.err.println("[DriverStatsRepository] loadIRatingHistory failed: " + e.getMessage());
        }
        return result;
    }

    // ── Mapping ────────────────────────────────────────────────────────────

    private DriverProfile mapRow(ResultSet rs) throws SQLException {
        DriverProfile p = new DriverProfile();
        p.setId(rs.getLong("id"));
        p.setPlatform(rs.getString("platform"));
        p.setDriverName(rs.getString("driver_name"));
        p.setCustomerId(rs.getString("customer_id"));
        p.setIrating(rs.getInt("irating"));
        p.setSafetyRating(rs.getDouble("safety_rating"));
        p.setLicenseClass(rs.getString("license_class"));
        p.setTotalRaces(rs.getInt("total_races"));
        p.setWins(rs.getInt("wins"));
        p.setPodiums(rs.getInt("podiums"));
        p.setPoles(rs.getInt("poles"));
        p.setAvgFinishPos(rs.getDouble("avg_finish_pos"));
        String synced = rs.getString("last_synced");
        if (synced != null) {
            try { p.setLastSynced(LocalDateTime.parse(synced + "T00:00:00")); }
            catch (Exception ignored) {}
        }
        return p;
    }
}
