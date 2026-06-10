package org.example.drivesimui;

import java.util.List;

public abstract class Vehicle {

    private String make;
    private String model;
    private int year;

    public Vehicle(String make, String model, int year) {
        this.make = make;
        this.model = model;
        this.year = year;
    }

    public abstract String getSetupGuide();

    public abstract String getCarType();

    public abstract String getSetupJSON();

    /**
     * Returns all setup field values as a comma-separated string for CSV persistence.
     * Each subclass defines its own format; the same order must be used in applySetupFromCSV().
     */
    public abstract String getSaveData();

    /**
     * Restores setup state from a CSV token list produced by getSaveData().
     * Uses a List so each subclass can consume as many tokens as it needs.
     */
    public abstract void applySetupFromCSV(List<String> tokens);

    /**
     * Returns the table column header names specific to this car type.
     * The order must match getColumnValues().
     */
    public abstract List<String> getColumnHeaders();

    /**
     * Returns the formatted display values for each setup field.
     * The order must match getColumnHeaders().
     */
    public abstract List<String> getColumnValues();

    /**
     * Returns how many columns (from the start of getColumnHeaders()) are shown by default.
     * Columns beyond this index are hidden until the user expands "Advanced Setup".
     */
    public abstract int getEssentialColumnCount();

    public String getMake() { return make; }

    public void setMake(String make) { this.make = make; }

    public String getModel() { return model; }

    public void setModel(String model) { this.model = model; }

    public int getYear() { return year; }

    public void setYear(int year) { this.year = year; }
}
