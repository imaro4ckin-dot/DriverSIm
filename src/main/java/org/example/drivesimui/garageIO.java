package org.example.drivesimui;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class garageIO {
    private static final String FILE_NAME = "telemetry_save.csv";

    /**
     * CSV row format:
     *   track, timeMs, carType, make, model, year, <setup tokens...>, s1Ms, s2Ms, s3Ms, weather
     *
     * The 4 new fields are always appended at the end so old saves (without them)
     * still load correctly — the load method checks length before reading them.
     */
    public static void saveLaps(List<LapTime> laps) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(FILE_NAME))) {
            for (LapTime lap : laps) {
                Vehicle car = lap.getCarUsed();
                writer.println(
                        lap.getTrackName()          + "," +
                        lap.getTimeInMilliseconds() + "," +
                        car.getCarType()            + "," +
                        car.getMake()               + "," +
                        car.getModel()              + "," +
                        car.getYear()               + "," +
                        car.getSaveData()           + "," +
                        lap.getS1Ms()               + "," +
                        lap.getS2Ms()               + "," +
                        lap.getS3Ms()               + "," +
                        lap.getWeather().name()
                );
            }
        } catch (IOException e) {
            System.err.println("Critical error saving data: " + e.getMessage());
        }
    }

    public static List<LapTime> loadLaps() {
        List<LapTime> loadedLaps = new ArrayList<>();
        File saveFile = new File(FILE_NAME);
        if (!saveFile.exists()) return loadedLaps;

        try (BufferedReader reader = new BufferedReader(new FileReader(saveFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] parts = line.split(",");

                // Minimum fixed fields: track, timeMs, carType, make, model, year = 6
                if (parts.length < 6) continue;

                String track   = parts[0];
                long   timeMs  = Long.parseLong(parts[1]);
                String carType = parts[2];
                String make    = parts[3];
                String model   = parts[4];
                int    year    = Integer.parseInt(parts[5]);

                // Everything between index 6 and the last 4 tokens is setup data.
                // Old saves have no trailing s1/s2/s3/weather — detect by total length.
                // GT3 has 8 setup tokens, Formula has 7.
                int setupTokenCount = carType.equals("Formula") ? 7 : 8;
                int setupEnd        = 6 + setupTokenCount;   // exclusive

                Vehicle car = carType.equals("Formula")
                        ? new FormulaCar(make, model, year)
                        : new GT3Car(make, model, year);

                if (parts.length > 6) {
                    // Guard: only pass as many tokens as the car expects
                    int available = Math.min(setupTokenCount, parts.length - 6);
                    car.applySetupFromCSV(Arrays.asList(parts).subList(6, 6 + available));
                }

                LapTime lap = new LapTime(track, timeMs, car);

                // Load new fields only if they are present (backwards-compatible)
                if (parts.length >= setupEnd + 4) {
                    try { lap.setS1Ms(Long.parseLong(parts[setupEnd]));     } catch (NumberFormatException ignored) {}
                    try { lap.setS2Ms(Long.parseLong(parts[setupEnd + 1])); } catch (NumberFormatException ignored) {}
                    try { lap.setS3Ms(Long.parseLong(parts[setupEnd + 2])); } catch (NumberFormatException ignored) {}
                    try { lap.setWeather(LapTime.Weather.valueOf(parts[setupEnd + 3])); } catch (IllegalArgumentException ignored) {}
                }

                loadedLaps.add(lap);
            }
        } catch (Exception e) {
            System.err.println("Critical error loading data: " + e.getMessage());
        }
        return loadedLaps;
    }
}
