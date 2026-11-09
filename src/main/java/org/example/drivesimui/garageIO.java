package org.example.drivesimui;

import org.example.drivesimui.db.DatabaseManager;
import org.example.drivesimui.db.LapRepository;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Persistence adapter.
 * Delegates to {@link LapRepository} (SQLite).  On first run it automatically
 * migrates any existing {@code telemetry_save.csv} into the database and renames
 * the file to {@code telemetry_save.csv.migrated} so the migration runs only once.
 */
public class garageIO {

    private static final String LEGACY_CSV   = "telemetry_save.csv";
    private static final String MIGRATED_CSV = "telemetry_save.csv.migrated";

    // ── Public API (unchanged signature so Launcher.java needs no edits) ──────

    public static void saveLaps(List<LapTime> laps) {
        // No-op: each lap is persisted immediately via insert() in processNewLap().
        // This method is kept to satisfy Launcher's onCloseRequest handler.
    }

    public static List<LapTime> loadLaps() {
        try {
            DatabaseManager db = DatabaseManager.getInstance();
            LapRepository   repo = new LapRepository(db);

            // One-time CSV migration
            if (repo.count() == 0) {
                migrateFromCsv(repo);
            }

            return repo.loadAll();
        } catch (SQLException e) {
            System.err.println("Error loading laps from database: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Inserts a single lap immediately.
     * Called from {@code Launcher.processNewLap()} after each lap is logged.
     */
    public static void insertLap(LapTime lap) {
        try {
            DatabaseManager db = DatabaseManager.getInstance();
            new LapRepository(db).insert(lap);
        } catch (SQLException e) {
            System.err.println("Error persisting lap: " + e.getMessage());
        }
    }

    // ── CSV migration ─────────────────────────────────────────────────────────

    private static void migrateFromCsv(LapRepository repo) {
        File csvFile = new File(LEGACY_CSV);
        if (!csvFile.exists()) return;

        System.out.println("[garageIO] Migrating legacy CSV to SQLite …");
        List<LapTime> laps = parseCsv(csvFile);
        int count = 0;
        for (LapTime lap : laps) {
            try {
                repo.insert(lap);
                count++;
            } catch (SQLException e) {
                System.err.println("[garageIO] Skipped lap during migration: " + e.getMessage());
            }
        }
        System.out.println("[garageIO] Migrated " + count + " laps. Renaming CSV.");
        csvFile.renameTo(new File(MIGRATED_CSV));
    }

    /**
     * Original CSV parser (copied verbatim from the old garageIO implementation
     * so migration reads the legacy format correctly).
     */
    private static List<LapTime> parseCsv(File file) {
        List<LapTime> loaded = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] parts = line.split(",");
                if (parts.length < 6) continue;

                String track   = parts[0];
                long   timeMs  = Long.parseLong(parts[1]);
                String carType = parts[2];
                String make    = parts[3];
                String model   = parts[4];
                int    year    = Integer.parseInt(parts[5]);

                int setupTokenCount = carType.equals("Formula") ? 7 : 8;
                int setupEnd        = 6 + setupTokenCount;

                Vehicle car = carType.equals("Formula")
                        ? new FormulaCar(make, model, year)
                        : new GT3Car(make, model, year);

                if (parts.length > 6) {
                    int available = Math.min(setupTokenCount, parts.length - 6);
                    car.applySetupFromCSV(Arrays.asList(parts).subList(6, 6 + available));
                }

                LapTime lap = new LapTime(track, timeMs, car);

                if (parts.length >= setupEnd + 4) {
                    try { lap.setS1Ms(Long.parseLong(parts[setupEnd]));     } catch (NumberFormatException ignored) {}
                    try { lap.setS2Ms(Long.parseLong(parts[setupEnd + 1])); } catch (NumberFormatException ignored) {}
                    try { lap.setS3Ms(Long.parseLong(parts[setupEnd + 2])); } catch (NumberFormatException ignored) {}
                    try { lap.setWeather(LapTime.Weather.valueOf(parts[setupEnd + 3])); } catch (IllegalArgumentException ignored) {}
                }
                loaded.add(lap);
            }
        } catch (Exception e) {
            System.err.println("[garageIO] CSV migration error: " + e.getMessage());
        }
        return loaded;
    }
}
