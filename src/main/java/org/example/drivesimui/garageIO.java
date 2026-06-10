package org.example.drivesimui;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class garageIO {
    private static final String FILE_NAME = "telemetry_save.csv";

    /**
     * CSV row format: track,timeMs,carType,make,model,year,<setup tokens...>
     * Setup tokens are produced by Vehicle.getSaveData() — count varies per car type.
     */
    public static void saveLaps(List<LapTime> laps) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(FILE_NAME))) {
            for (LapTime lap : laps) {
                Vehicle car = lap.getCarUsed();
                writer.println(
                        lap.getTrackName() + "," +
                        lap.getTimeInMilliseconds() + "," +
                        car.getCarType() + "," +
                        car.getMake() + "," +
                        car.getModel() + "," +
                        car.getYear() + "," +
                        car.getSaveData()
                );
            }
            System.out.println("Data saved successfully to " + FILE_NAME);
        } catch (IOException e) {
            System.err.println("Critical error saving data: " + e.getMessage());
        }
    }

    public static List<LapTime> loadLaps() {
        List<LapTime> loadedLaps = new ArrayList<>();
        File saveFile = new File(FILE_NAME);

        if (!saveFile.exists()) {
            return loadedLaps;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(saveFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");

                // Minimum: track, timeMs, carType, make, model, year = 6 fixed fields
                if (parts.length < 6) {
                    continue;
                }

                String track = parts[0];
                long timeMs = Long.parseLong(parts[1]);
                String carType = parts[2];
                String make = parts[3];
                String model = parts[4];
                int year = Integer.parseInt(parts[5]);

                // All remaining tokens belong to the car's setup data
                List<String> setupTokens = Arrays.asList(parts).subList(6, parts.length);

                Vehicle car;
                if (carType.equals("Formula")) {
                    car = new FormulaCar(make, model, year);
                } else {
                    car = new GT3Car(make, model, year);
                }

                if (!setupTokens.isEmpty()) {
                    car.applySetupFromCSV(setupTokens);
                }

                loadedLaps.add(new LapTime(track, timeMs, car));
            }
            System.out.println("Data loaded successfully!");
        } catch (Exception e) {
            System.err.println("Critical error loading data: " + e.getMessage());
        }
        return loadedLaps;
    }
}
