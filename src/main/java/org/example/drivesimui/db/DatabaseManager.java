package org.example.drivesimui.db;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Singleton that manages the SQLite connection and ensures the schema exists.
 * Database file: ~/.drivesim/drivesim.db
 */
public class DatabaseManager {

    private static final Path DB_DIR  = Path.of(System.getProperty("user.home"), ".drivesim");
    private static final Path DB_PATH = DB_DIR.resolve("drivesim.db");

    private static DatabaseManager instance;
    private final Connection connection;

    private DatabaseManager() throws SQLException {
        try {
            Files.createDirectories(DB_DIR);
        } catch (IOException e) {
            throw new SQLException("Cannot create database directory: " + e.getMessage(), e);
        }
        connection = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
        connection.setAutoCommit(true);
        applySchema();
    }

    public static synchronized DatabaseManager getInstance() throws SQLException {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }

    public Connection getConnection() {
        return connection;
    }

    private void applySchema() throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS vehicles (
                    id        INTEGER PRIMARY KEY AUTOINCREMENT,
                    car_type  TEXT    NOT NULL,
                    make      TEXT    NOT NULL,
                    model     TEXT    NOT NULL,
                    year      INTEGER NOT NULL,
                    setup_json TEXT,
                    platform  TEXT    NOT NULL DEFAULT 'LOCAL'
                )
            """);

            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS laps (
                    id              INTEGER PRIMARY KEY AUTOINCREMENT,
                    track_name      TEXT    NOT NULL,
                    time_ms         INTEGER NOT NULL,
                    date_recorded   TEXT    NOT NULL,
                    delta_ms        INTEGER NOT NULL DEFAULT 0,
                    s1_ms           INTEGER NOT NULL DEFAULT 0,
                    s2_ms           INTEGER NOT NULL DEFAULT 0,
                    s3_ms           INTEGER NOT NULL DEFAULT 0,
                    weather         TEXT    NOT NULL DEFAULT 'DRY',
                    vehicle_id      INTEGER REFERENCES vehicles(id),
                    session_id      INTEGER REFERENCES sessions(id),
                    platform        TEXT    NOT NULL DEFAULT 'LOCAL',
                    external_lap_id TEXT
                )
            """);

            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_laps_track    ON laps(track_name)");
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_laps_platform ON laps(platform)");

            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS sessions (
                    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
                    platform            TEXT    NOT NULL,
                    track_name          TEXT    NOT NULL,
                    car_name            TEXT,
                    session_date        TEXT,
                    session_type        TEXT,
                    finish_position     INTEGER,
                    total_drivers       INTEGER,
                    best_lap_ms         INTEGER,
                    s1_best_ms          INTEGER NOT NULL DEFAULT 0,
                    s2_best_ms          INTEGER NOT NULL DEFAULT 0,
                    s3_best_ms          INTEGER NOT NULL DEFAULT 0,
                    external_session_id TEXT,
                    raw_json            TEXT
                )
            """);

            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS driver_stats (
                    id             INTEGER PRIMARY KEY AUTOINCREMENT,
                    platform       TEXT    NOT NULL,
                    driver_name    TEXT    NOT NULL,
                    customer_id    TEXT,
                    irating        INTEGER NOT NULL DEFAULT 0,
                    safety_rating  REAL    NOT NULL DEFAULT 0.0,
                    license_class  TEXT    NOT NULL DEFAULT 'R',
                    total_races    INTEGER NOT NULL DEFAULT 0,
                    wins           INTEGER NOT NULL DEFAULT 0,
                    podiums        INTEGER NOT NULL DEFAULT 0,
                    poles          INTEGER NOT NULL DEFAULT 0,
                    avg_finish_pos REAL    NOT NULL DEFAULT 0.0,
                    last_synced    TEXT,
                    UNIQUE(platform, customer_id)
                )
            """);

            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS platform_accounts (
                    id             INTEGER PRIMARY KEY AUTOINCREMENT,
                    platform       TEXT    NOT NULL UNIQUE,
                    display_name   TEXT,
                    email          TEXT,
                    cred_file_path TEXT,
                    last_sync      TEXT,
                    sync_enabled   INTEGER NOT NULL DEFAULT 0
                )
            """);

            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS car_setups (
                    id          INTEGER PRIMARY KEY AUTOINCREMENT,
                    platform    TEXT    NOT NULL,
                    car_name    TEXT,
                    track_name  TEXT,
                    setup_name  TEXT,
                    params_json TEXT,
                    import_date TEXT
                )
            """);
        }
    }
}
