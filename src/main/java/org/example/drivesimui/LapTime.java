package org.example.drivesimui;

import java.time.LocalDate;

public class LapTime implements DataExport {

    private String TrackName;
    private long TimeInMilSec;
    private LocalDate dateRecorded;
    private Vehicle carUsed;


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
        return "{\n" +
               "  \"track\": \"" + TrackName + "\",\n" +
               "  \"timeMs\": " + TimeInMilSec + ",\n" +
               "  \"date\": \"" + dateRecorded.toString() + "\",\n" +
               "  \"car\": \"" + carUsed.getMake() + "\"\n" +
               "}";
    }

public String getTrackName() { return TrackName; }
    public long getTimeInMilliseconds() { return TimeInMilSec; }
    public Vehicle getCarUsed() { return carUsed; }
}
