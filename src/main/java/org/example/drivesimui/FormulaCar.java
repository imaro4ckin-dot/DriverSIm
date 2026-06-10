package org.example.drivesimui;

import java.util.Arrays;
import java.util.List;

public class FormulaCar extends Vehicle implements DataExport {

    private double frontWingAngle;    // 0–15 degrees
    private double rearWingAngle;     // 0–15 degrees
    private int ersDeployMode;        // 0–4: None, Low, Medium, High, Overtake
    private boolean drsEnabled;       // DRS active for this lap setup
    private int differentialEntry;    // % 50–100
    private int differentialExit;     // % 50–100
    private double fuelLoad;          // kg, 0–110

    public FormulaCar(String make, String model, int year) {
        super(make, model, year);
        this.frontWingAngle = 10.0;
        this.rearWingAngle = 10.0;
        this.ersDeployMode = 3;
        this.drsEnabled = true;
        this.differentialEntry = 60;
        this.differentialExit = 70;
        this.fuelLoad = 50.0;
    }

    @Override
    public String getSetupGuide() {
        return "Formula cars rely on aerodynamic grip. Higher wing angles add downforce but reduce top speed. " +
               "Manage ERS deployment mode carefully — Overtake drains the battery fast.";
    }

    @Override
    public String getCarType() { return "Formula"; }

    @Override
    public String getSetupJSON() {
        return "\"frontWing\": " + frontWingAngle + ",\n" +
               "      \"rearWing\": " + rearWingAngle + ",\n" +
               "      \"ersDeployMode\": " + ersDeployMode + ",\n" +
               "      \"drsEnabled\": " + drsEnabled + ",\n" +
               "      \"differentialEntry\": " + differentialEntry + ",\n" +
               "      \"differentialExit\": " + differentialExit + ",\n" +
               "      \"fuelLoad\": " + fuelLoad;
    }

    /** CSV token order: fwAngle,rwAngle,ersMode,drs,diffEntry,diffExit,fuel (7 tokens) */
    @Override
    public String getSaveData() {
        return frontWingAngle + "," + rearWingAngle + "," + ersDeployMode + "," +
               drsEnabled + "," + differentialEntry + "," + differentialExit + "," + fuelLoad;
    }

    @Override
    public void applySetupFromCSV(List<String> tokens) {
        this.frontWingAngle = Double.parseDouble(tokens.get(0));
        this.rearWingAngle = Double.parseDouble(tokens.get(1));
        this.ersDeployMode = Integer.parseInt(tokens.get(2));
        this.drsEnabled = Boolean.parseBoolean(tokens.get(3));
        this.differentialEntry = Integer.parseInt(tokens.get(4));
        this.differentialExit = Integer.parseInt(tokens.get(5));
        this.fuelLoad = Double.parseDouble(tokens.get(6));
    }

    /** Front/Rear wing are essential; ERS, DRS, diff, fuel are advanced. */
    @Override
    public int getEssentialColumnCount() { return 2; }

    @Override
    public List<String> getColumnHeaders() {
        return Arrays.asList(
                "Front Wing", "Rear Wing",
                "ERS Mode", "DRS",
                "Diff Entry %", "Diff Exit %",
                "Fuel (kg)"
        );
    }

    @Override
    public List<String> getColumnValues() {
        String[] ersModeNames = {"None", "Low", "Medium", "High", "Overtake"};
        return Arrays.asList(
                String.format("%.1f", frontWingAngle),
                String.format("%.1f", rearWingAngle),
                ersModeNames[Math.min(ersDeployMode, ersModeNames.length - 1)],
                drsEnabled ? "On" : "Off",
                String.valueOf(differentialEntry),
                String.valueOf(differentialExit),
                String.format("%.1f", fuelLoad)
        );
    }

    @Override
    public String toCSV() {
        return getMake() + "," + getModel() + "," + getYear() + "," + getSaveData();
    }

    @Override
    public String toJSON() {
        return "{\n" +
               "  \"make\": \"" + getMake() + "\",\n" +
               "  \"model\": \"" + getModel() + "\",\n" +
               "  \"setup\": {\n" +
               "    " + getSetupJSON() + "\n" +
               "  }\n" +
               "}";
    }

    public double getFrontWingAngle() { return frontWingAngle; }
    public void setFrontWingAngle(double frontWingAngle) { this.frontWingAngle = frontWingAngle; }

    public double getRearWingAngle() { return rearWingAngle; }
    public void setRearWingAngle(double rearWingAngle) { this.rearWingAngle = rearWingAngle; }

    public int getErsDeployMode() { return ersDeployMode; }
    public void setErsDeployMode(int ersDeployMode) { this.ersDeployMode = ersDeployMode; }

    public boolean isDrsEnabled() { return drsEnabled; }
    public void setDrsEnabled(boolean drsEnabled) { this.drsEnabled = drsEnabled; }

    public int getDifferentialEntry() { return differentialEntry; }
    public void setDifferentialEntry(int differentialEntry) { this.differentialEntry = differentialEntry; }

    public int getDifferentialExit() { return differentialExit; }
    public void setDifferentialExit(int differentialExit) { this.differentialExit = differentialExit; }

    public double getFuelLoad() { return fuelLoad; }
    public void setFuelLoad(double fuelLoad) { this.fuelLoad = fuelLoad; }
}
