package org.example.drivesimui;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class GarageManger {

    private List<LapTime> lapRecorded;

    public GarageManger(){
        this.lapRecorded = new ArrayList<>();

    }
    public void addLap( LapTime lap){
        lapRecorded.add(lap);
        System.out.println("Lap recorded for " + lap.getTrackName());
    }

    public LapTime getFastestLap(String TrackName){
        if(lapRecorded.isEmpty()){
            return null;
        }
        LapTime fastest = null;


    for (LapTime currentlap : lapRecorded){
        if (currentlap.getTrackName().equalsIgnoreCase(TrackName)) {

            if(fastest == null || currentlap.getTimeInMilliseconds() < fastest.getTimeInMilliseconds()){
                fastest = currentlap;

            }
        }
    }

    return fastest;
    }


    public void exportlapsToCSV(String FileName){
         try(PrintWriter writer = new PrintWriter(new FileWriter(FileName))) {

             writer.println("Track,TimeInMs,Date,carUsed");

             for (LapTime lap :lapRecorded){
                 writer.println(lap.toCSV());
             }
           System.out.println("Successfully exported " + lapRecorded.size() + " laps to " + FileName);

        } catch (IOException e) {
            // Exception handling is mandatory for file operations in Java
            System.err.println("Error writing to file: " + e.getMessage());
        }
    }

    public List<LapTime> getAllLap() {
        return lapRecorded;
    }
}
