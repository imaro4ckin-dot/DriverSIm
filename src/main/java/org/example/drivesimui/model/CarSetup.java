package org.example.drivesimui.model;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * Generic key-value setup container for setups imported from sim platforms.
 * Used when the platform's setup schema doesn't map to GT3Car/FormulaCar fields.
 */
public class CarSetup {

    private long              id;
    private String            platform;
    private String            carName;
    private String            trackName;
    private String            setupName;
    private Map<String,String> params = new HashMap<>();
    private LocalDate         importDate;

    public CarSetup() {}

    public CarSetup(String platform, String carName, String trackName, String setupName) {
        this.platform  = platform;
        this.carName   = carName;
        this.trackName = trackName;
        this.setupName = setupName;
        this.importDate = LocalDate.now();
    }

    public long              getId()                           { return id; }
    public void              setId(long id)                    { this.id = id; }
    public String            getPlatform()                     { return platform; }
    public void              setPlatform(String platform)      { this.platform = platform; }
    public String            getCarName()                      { return carName; }
    public void              setCarName(String carName)        { this.carName = carName; }
    public String            getTrackName()                    { return trackName; }
    public void              setTrackName(String trackName)    { this.trackName = trackName; }
    public String            getSetupName()                    { return setupName; }
    public void              setSetupName(String setupName)    { this.setupName = setupName; }
    public Map<String,String> getParams()                      { return params; }
    public void              setParams(Map<String,String> p)   { this.params = p; }
    public void              putParam(String key, String value){ this.params.put(key, value); }
    public LocalDate         getImportDate()                   { return importDate; }
    public void              setImportDate(LocalDate d)        { this.importDate = d; }
}
