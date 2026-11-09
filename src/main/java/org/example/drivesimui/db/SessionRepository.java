package org.example.drivesimui.db;

import org.example.drivesimui.model.RaceSession;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * CRUD for the {@code sessions} and {@code platform_accounts} tables.
 */
public class SessionRepository {

    // ── Sessions ───────────────────────────────────────────────────────────

    public void insert(RaceSession session) {
        String sql = """
                INSERT OR IGNORE INTO sessions
                  (platform, track_name, car_name, session_date, session_type,
                   finish_position, total_drivers, best_lap_ms,
                   s1_best_ms, s2_best_ms, s3_best_ms,
                   external_session_id, raw_json)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
                """;
        try (Connection con = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, session.getPlatform() != null ? session.getPlatform() : "LOCAL");
            ps.setString(2, session.getTrackName());
            ps.setString(3, session.getCarName());
            ps.setString(4, session.getSessionDate() != null ? session.getSessionDate().toString() : null);
            ps.setString(5, session.getSessionType());
            ps.setInt(6, session.getFinishPosition());
            ps.setInt(7, session.getTotalDrivers());
            ps.setLong(8, session.getBestLapMs());
            ps.setLong(9, session.getS1BestMs());
            ps.setLong(10, session.getS2BestMs());
            ps.setLong(11, session.getS3BestMs());
            ps.setString(12, session.getExternalSessionId());
            ps.setString(13, session.getRawJson());
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[SessionRepository] insert failed: " + e.getMessage());
        }
    }

    public List<RaceSession> loadByPlatform(String platform) {
        List<RaceSession> result = new ArrayList<>();
        String sql = "SELECT * FROM sessions WHERE platform = ? ORDER BY session_date DESC";
        try (Connection con = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, platform);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                result.add(map(rs));
            }
        } catch (SQLException e) {
            System.err.println("[SessionRepository] loadByPlatform failed: " + e.getMessage());
        }
        return result;
    }

    public List<RaceSession> loadAll() {
        List<RaceSession> result = new ArrayList<>();
        String sql = "SELECT * FROM sessions ORDER BY session_date DESC";
        try (Connection con = DatabaseManager.getInstance().getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) result.add(map(rs));
        } catch (SQLException e) {
            System.err.println("[SessionRepository] loadAll failed: " + e.getMessage());
        }
        return result;
    }

    // ── Platform accounts ──────────────────────────────────────────────────

    /**
     * Upsert a platform account record (display name + email, no password).
     */
    public void savePlatformAccount(String platform, String displayName, String email, String credFilePath) {
        String sql = """
                INSERT INTO platform_accounts (platform, display_name, email, cred_file_path, last_sync, sync_enabled)
                VALUES (?,?,?,?,?,1)
                ON CONFLICT(platform) DO UPDATE SET
                  display_name=excluded.display_name,
                  email=excluded.email,
                  cred_file_path=excluded.cred_file_path,
                  last_sync=excluded.last_sync
                """;
        try (Connection con = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, platform);
            ps.setString(2, displayName);
            ps.setString(3, email);
            ps.setString(4, credFilePath);
            ps.setString(5, LocalDate.now().toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[SessionRepository] savePlatformAccount failed: " + e.getMessage());
        }
    }

    /** Returns the saved email/display-name for a platform, or null if not found. */
    public String loadPlatformEmail(String platform) {
        String sql = "SELECT email FROM platform_accounts WHERE platform = ?";
        try (Connection con = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, platform);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString("email");
        } catch (SQLException e) {
            System.err.println("[SessionRepository] loadPlatformEmail failed: " + e.getMessage());
        }
        return null;
    }

    // ── Mapping ────────────────────────────────────────────────────────────

    private RaceSession map(ResultSet rs) throws SQLException {
        RaceSession s = new RaceSession();
        s.setId(rs.getLong("id"));
        s.setPlatform(rs.getString("platform"));
        s.setTrackName(rs.getString("track_name"));
        s.setCarName(rs.getString("car_name"));
        String dateStr = rs.getString("session_date");
        if (dateStr != null && !dateStr.isBlank()) {
            try { s.setSessionDate(LocalDate.parse(dateStr)); } catch (Exception ignored) {}
        }
        s.setSessionType(rs.getString("session_type"));
        s.setFinishPosition(rs.getInt("finish_position"));
        s.setTotalDrivers(rs.getInt("total_drivers"));
        s.setBestLapMs(rs.getLong("best_lap_ms"));
        s.setS1BestMs(rs.getLong("s1_best_ms"));
        s.setS2BestMs(rs.getLong("s2_best_ms"));
        s.setS3BestMs(rs.getLong("s3_best_ms"));
        s.setExternalSessionId(rs.getString("external_session_id"));
        s.setRawJson(rs.getString("raw_json"));
        return s;
    }
}
