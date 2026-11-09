package org.example.drivesimui.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * A race/qualify/practice session imported from a sim platform (iRacing, ACC, LMU).
 */
public class RaceSession {

    private long      id;
    private String    platform;           // "IRACING", "ACC", "LMU", "LOCAL"
    private String    trackName;
    private String    carName;
    private LocalDate sessionDate;
    private String    sessionType;        // "RACE", "QUALIFY", "PRACTICE"
    private int       finishPosition;
    private int       totalDrivers;
    private long      bestLapMs;
    private long      s1BestMs;
    private long      s2BestMs;
    private long      s3BestMs;
    private String    externalSessionId;
    private String    rawJson;
    private List<LapTime> laps = new ArrayList<>();

    public RaceSession() {}

    public RaceSession(String platform, String trackName, String carName,
                       LocalDate sessionDate, String sessionType) {
        this.platform    = platform;
        this.trackName   = trackName;
        this.carName     = carName;
        this.sessionDate = sessionDate;
        this.sessionType = sessionType;
    }

    public long      getId()                              { return id; }
    public void      setId(long id)                       { this.id = id; }
    public String    getPlatform()                        { return platform; }
    public void      setPlatform(String platform)         { this.platform = platform; }
    public String    getTrackName()                       { return trackName; }
    public void      setTrackName(String trackName)       { this.trackName = trackName; }
    public String    getCarName()                         { return carName; }
    public void      setCarName(String carName)           { this.carName = carName; }
    public LocalDate getSessionDate()                     { return sessionDate; }
    public void      setSessionDate(LocalDate sessionDate){ this.sessionDate = sessionDate; }
    public String    getSessionType()                     { return sessionType; }
    public void      setSessionType(String sessionType)   { this.sessionType = sessionType; }
    public int       getFinishPosition()                  { return finishPosition; }
    public void      setFinishPosition(int pos)           { this.finishPosition = pos; }
    public int       getTotalDrivers()                    { return totalDrivers; }
    public void      setTotalDrivers(int total)           { this.totalDrivers = total; }
    public long      getBestLapMs()                       { return bestLapMs; }
    public void      setBestLapMs(long bestLapMs)         { this.bestLapMs = bestLapMs; }
    public long      getS1BestMs()                        { return s1BestMs; }
    public void      setS1BestMs(long s1BestMs)           { this.s1BestMs = s1BestMs; }
    public long      getS2BestMs()                        { return s2BestMs; }
    public void      setS2BestMs(long s2BestMs)           { this.s2BestMs = s2BestMs; }
    public long      getS3BestMs()                        { return s3BestMs; }
    public void      setS3BestMs(long s3BestMs)           { this.s3BestMs = s3BestMs; }
    public String    getExternalSessionId()               { return externalSessionId; }
    public void      setExternalSessionId(String id)      { this.externalSessionId = id; }
    public String    getRawJson()                         { return rawJson; }
    public void      setRawJson(String rawJson)           { this.rawJson = rawJson; }
    public List<LapTime> getLaps()                        { return laps; }
    public void      setLaps(List<LapTime> laps)          { this.laps = laps; }
}
