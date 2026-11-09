package org.example.drivesimui.model;

import java.time.LocalDateTime;

/**
 * Driver statistics and rating data fetched from a sim platform.
 */
public class DriverProfile {

    private long          id;
    private String        platform;        // "IRACING", "ACC", "LMU"
    private String        driverName;
    private String        customerId;      // iRacing custId, ACC playerId, etc.
    private int           irating;         // iRacing only
    private double        safetyRating;
    private String        licenseClass;    // "A", "B", "C", "D", "R"
    private int           totalRaces;
    private int           wins;
    private int           podiums;
    private int           poles;
    private double        avgFinishPos;
    private LocalDateTime lastSynced;

    public DriverProfile() {}

    public DriverProfile(String platform, String driverName, String customerId) {
        this.platform   = platform;
        this.driverName = driverName;
        this.customerId = customerId;
    }

    public long          getId()                               { return id; }
    public void          setId(long id)                        { this.id = id; }
    public String        getPlatform()                         { return platform; }
    public void          setPlatform(String platform)          { this.platform = platform; }
    public String        getDriverName()                       { return driverName; }
    public void          setDriverName(String driverName)      { this.driverName = driverName; }
    public String        getCustomerId()                       { return customerId; }
    public void          setCustomerId(String customerId)      { this.customerId = customerId; }
    public int           getIrating()                          { return irating; }
    public void          setIrating(int irating)               { this.irating = irating; }
    public double        getSafetyRating()                     { return safetyRating; }
    public void          setSafetyRating(double safetyRating)  { this.safetyRating = safetyRating; }
    public String        getLicenseClass()                     { return licenseClass; }
    public void          setLicenseClass(String licenseClass)  { this.licenseClass = licenseClass; }
    public int           getTotalRaces()                       { return totalRaces; }
    public void          setTotalRaces(int totalRaces)         { this.totalRaces = totalRaces; }
    public int           getWins()                             { return wins; }
    public void          setWins(int wins)                     { this.wins = wins; }
    public int           getPodiums()                          { return podiums; }
    public void          setPodiums(int podiums)               { this.podiums = podiums; }
    public int           getPoles()                            { return poles; }
    public void          setPoles(int poles)                   { this.poles = poles; }
    public double        getAvgFinishPos()                     { return avgFinishPos; }
    public void          setAvgFinishPos(double avgFinishPos)  { this.avgFinishPos = avgFinishPos; }
    public LocalDateTime getLastSynced()                       { return lastSynced; }
    public void          setLastSynced(LocalDateTime lastSynced){ this.lastSynced = lastSynced; }
}
