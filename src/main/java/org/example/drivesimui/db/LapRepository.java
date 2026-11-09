package org.example.drivesimui.db;

import org.example.drivesimui.FormulaCar;
import org.example.drivesimui.GT3Car;
import org.example.drivesimui.LapTime;
import org.example.drivesimui.Vehicle;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * CRUD operations for the laps and vehicles tables.
 */
public class LapRepository {

    private final DatabaseManager db;

    /** Uses the singleton DatabaseManager — prefer this constructor. */
    public LapRepository() {
        try {
            this.db = DatabaseManager.getInstance();
        } catch (java.sql.SQLException e) {
            throw new RuntimeException("Failed to open database: " + e.getMessage(), e);
        }
    }

    public LapRepository(DatabaseManager db) {
        this.db = db;
    }

    // ── Insert ───────────────────────────────────────────────────────────────

    /**
     * Persists a lap (and its vehicle) into the database.
     * Sets {@code lap.id} to the generated row id on success.
     *
     * @return the generated lap id, or -1 on failure
     */
    public long insert(LapTime lap) throws SQLException {
        long vehicleId = insertVehicle(lap.getCarUsed());

        String sql = """
            INSERT INTO laps (track_name, time_ms, date_recorded, delta_ms,
                              s1_ms, s2_ms, s3_ms, weather, vehicle_id, platform)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'LOCAL')
        """;
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, lap.getTrackName());
            ps.setLong(2,   lap.getTimeInMilliseconds());
            ps.setString(3, lap.getDateRecorded().toString());
            ps.setLong(4,   lap.getDeltaMs());
            ps.setLong(5,   lap.getS1Ms());
            ps.setLong(6,   lap.getS2Ms());
            ps.setLong(7,   lap.getS3Ms());
            ps.setString(8, lap.getWeather().name());
            ps.setLong(9,   vehicleId);
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            long id = keys.next() ? keys.getLong(1) : -1;
            lap.setId(id);
            return id;
        }
    }

    private long insertVehicle(Vehicle car) throws SQLException {
        String sql = """
            INSERT INTO vehicles (car_type, make, model, year, setup_json, platform)
            VALUES (?, ?, ?, ?, ?, 'LOCAL')
        """;
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, car.getCarType());
            ps.setString(2, car.getMake());
            ps.setString(3, car.getModel());
            ps.setInt(4,    car.getYear());
            ps.setString(5, car.getSetupJSON());
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            return keys.next() ? keys.getLong(1) : -1;
        }
    }

    // ── Load all ─────────────────────────────────────────────────────────────

    public List<LapTime> loadAll() throws SQLException {
        String sql = """
            SELECT l.id, l.track_name, l.time_ms, l.date_recorded,
                   l.delta_ms, l.s1_ms, l.s2_ms, l.s3_ms, l.weather,
                   v.car_type, v.make, v.model, v.year, v.setup_json
            FROM laps l
            JOIN vehicles v ON l.vehicle_id = v.id
            ORDER BY l.id ASC
        """;
        List<LapTime> result = new ArrayList<>();
        try (Statement st = db.getConnection().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                result.add(mapRow(rs));
            }
        }
        return result;
    }

    // ── Find by track + car type ──────────────────────────────────────────────

    public List<LapTime> findByTrackAndCar(String track, String carType) throws SQLException {
        String sql = """
            SELECT l.id, l.track_name, l.time_ms, l.date_recorded,
                   l.delta_ms, l.s1_ms, l.s2_ms, l.s3_ms, l.weather,
                   v.car_type, v.make, v.model, v.year, v.setup_json
            FROM laps l
            JOIN vehicles v ON l.vehicle_id = v.id
            WHERE l.track_name = ? AND v.car_type = ?
            ORDER BY l.id ASC
        """;
        List<LapTime> result = new ArrayList<>();
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, track);
            ps.setString(2, carType);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(mapRow(rs));
            }
        }
        return result;
    }

    // ── Count ────────────────────────────────────────────────────────────────

    public int count() throws SQLException {
        try (Statement st = db.getConnection().createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM laps")) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    // ── Mapping ──────────────────────────────────────────────────────────────

    private LapTime mapRow(ResultSet rs) throws SQLException {
        String carType  = rs.getString("car_type");
        String make     = rs.getString("make");
        String model    = rs.getString("model");
        int    year     = rs.getInt("year");
        String setupJson = rs.getString("setup_json");

        Vehicle car = buildVehicle(carType, make, model, year, setupJson);

        LapTime lap = new LapTime(rs.getString("track_name"),
                rs.getLong("time_ms"), car);
        lap.setId(rs.getLong("id"));
        lap.setDeltaMs(rs.getLong("delta_ms"));
        lap.setS1Ms(rs.getLong("s1_ms"));
        lap.setS2Ms(rs.getLong("s2_ms"));
        lap.setS3Ms(rs.getLong("s3_ms"));
        try {
            lap.setWeather(LapTime.Weather.valueOf(rs.getString("weather")));
        } catch (IllegalArgumentException ignored) {
            // keep default DRY
        }
        String dateStr = rs.getString("date_recorded");
        if (dateStr != null && !dateStr.isBlank()) {
            try { lap.setDateRecorded(LocalDate.parse(dateStr)); }
            catch (Exception ignored) {}
        }
        return lap;
    }

    /**
     * Reconstructs a Vehicle from persisted columns.
     * The setup_json column holds the result of {@code getSetupJSON()} — it cannot be
     * used to restore slider values directly because it is formatted for AI consumption.
     * We use a comma-serialised approach stored separately via {@code getSaveData()}.
     * For now we reconstruct a default vehicle (setup values go into the lap record
     * from the vehicles.setup_json once we add per-column persistence in a later phase).
     */
    private Vehicle buildVehicle(String carType, String make, String model,
                                  int year, String setupJson) {
        if ("Formula".equals(carType)) {
            FormulaCar fc = new FormulaCar(make, model, year);
            if (setupJson != null) restoreFormulaFromJson(fc, setupJson);
            return fc;
        }
        GT3Car gc = new GT3Car(make, model, year);
        if (setupJson != null) restoreGT3FromJson(gc, setupJson);
        return gc;
    }

    /** Best-effort restore of GT3 numeric setup params from the stored JSON snippet. */
    private void restoreGT3FromJson(GT3Car car, String json) {
        try {
            car.setAbs(extractInt(json, "\"abs\":"));
            car.setTractionControl(extractInt(json, "\"tractionControl\":"));
            car.setBrakeBias(extractDouble(json, "\"brakeBias\":"));
            car.setFrontSuspension(extractInt(json, "\"frontSuspension\":"));
            car.setRearSuspension(extractInt(json, "\"rearSuspension\":"));
            car.setTirePressureFront(extractDouble(json, "\"tirePressureFront\":"));
            car.setTirePressureRear(extractDouble(json, "\"tirePressureRear\":"));
            car.setFuelLoad(extractDouble(json, "\"fuelLoad\":"));
        } catch (Exception ignored) {}
    }

    private void restoreFormulaFromJson(FormulaCar car, String json) {
        try {
            car.setFrontWingAngle(extractDouble(json, "\"frontWing\":"));
            car.setRearWingAngle(extractDouble(json, "\"rearWing\":"));
            car.setErsDeployMode(extractInt(json, "\"ersDeployMode\":"));
            car.setDrsEnabled(json.contains("\"drsEnabled\": true"));
            car.setDifferentialEntry(extractInt(json, "\"differentialEntry\":"));
            car.setDifferentialExit(extractInt(json, "\"differentialExit\":"));
            car.setFuelLoad(extractDouble(json, "\"fuelLoad\":"));
        } catch (Exception ignored) {}
    }

    private int extractInt(String json, String key) {
        int start = json.indexOf(key);
        if (start < 0) throw new IllegalArgumentException("key not found: " + key);
        String rest = json.substring(start + key.length()).trim();
        int end = 0;
        while (end < rest.length() && (Character.isDigit(rest.charAt(end)) || rest.charAt(end) == '-'))
            end++;
        return Integer.parseInt(rest.substring(0, end).trim());
    }

    private double extractDouble(String json, String key) {
        int start = json.indexOf(key);
        if (start < 0) throw new IllegalArgumentException("key not found: " + key);
        String rest = json.substring(start + key.length()).trim();
        int end = 0;
        while (end < rest.length() && (Character.isDigit(rest.charAt(end))
                || rest.charAt(end) == '.' || rest.charAt(end) == '-'))
            end++;
        return Double.parseDouble(rest.substring(0, end).trim());
    }
}
