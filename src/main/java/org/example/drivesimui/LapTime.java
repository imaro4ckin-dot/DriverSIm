package org.example.drivesimui;

import java.time.LocalDate;

public class LapTime implements DataExport {

    private String TrackName;
    private long TimeInMilSec;
    private LocalDate dateRecorded;
    private Vehicle carUsed;
    private long deltaMs = 0;


    public LapTime(String TrackName, long TimeInMilSec, Vehicle carUsed){
        this.TrackName = TrackName;
        this.TimeInMilSec = TimeInMilSec;
        this.carUsed =carUsed;
        this.dateRecorded = LocalDate.now();
    }

    public String getFormatedTime(){
        long minutes = (TimeInMilSec / 1000) / 60;
        long seconds = (TimeInMilSec/ 1000) % 60;
        long milliseconds = TimeInMilSec% 1000;


        return String.format("%d:%02d.%03d", minutes, seconds, milliseconds);

    }

    @Override
    public String toCSV() {
        return TrackName + "," + TimeInMilSec + "," +
               dateRecorded.toString() + "," + carUsed.getMake() + "-" + carUsed.getModel();
    }

    @Override
    public String toJSON() {
        Vehicle car = getCarUsed();

        return "{\n" +
               "  \"track\": \"" + TrackName + "\",\n" +
               "  \"lapTimeMs\": " +  + TimeInMilSec +  ",\n" +
               "  \"car\": {\n" +
               "    \"type\": \"" + car.getCarType() + "\",\n" +
               "    \"make\": \"" + car.getMake() + "\",\n" +
               "    \"model\": \"" + car.getModel() + "\",\n" +
               "    \"setup\": {\n" +
               "      " + car.getSetupJSON() + "\n" + //
               "    }\n" +
               "  }\n" +
               "}";
    }

    public String getFormattedDelta(){
        if (deltaMs == 0 ) return "-";
        double seconds = deltaMs / 1000.0;

       if (deltaMs > 0) {
            return String.format("+%.3f", seconds); // Slower lap gets a plus sign
        }

        return String.format("%.3f", seconds); // Faster lap automatically has a minus sign from the math
    }


public String getTrackName() { return TrackName; }
    public long getTimeInMilliseconds() { return TimeInMilSec; }
    public Vehicle getCarUsed() { return carUsed; }

    public void setDeltaMs(long deltaMs){
        this.deltaMs =deltaMs;
    }

    public long getDeltaMs(){
        return deltaMs;
    }
}
