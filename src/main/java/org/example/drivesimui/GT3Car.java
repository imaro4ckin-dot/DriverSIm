package org.example.drivesimui;

import java.util.Arrays;
import java.util.List;

public class GT3Car extends Vehicle implements DataExport {

    private int abs;                    // 0–11
    private int tractionControl;        // 0–11
    private double brakeBias;           // % front, e.g. 56.5
    private int frontSuspension;        // 1–10 stiffness
    private int rearSuspension;         // 1–10 stiffness
    private double tirePressureFront;   // PSI
    private double tirePressureRear;    // PSI
    private double fuelLoad;            // litres, 0–120

    public GT3Car(String make, String model, int year) {
        super(make, model, year);
        this.abs = 6;
        this.tractionControl = 6;
        this.brakeBias = 56.5;
        this.frontSuspension = 5;
        this.rearSuspension = 5;
        this.tirePressureFront = 27.5;
        this.tirePressureRear = 27.5;
        this.fuelLoad = 60.0;
    }

    @Override
    public String getSetupGuide() {
        return "GT3 cars rely on mechanical grip. Adjust TC and ABS based on track temperature and tyre wear.";
    }

    @Override
    public String getCarType() { return "GT3"; }

    @Override
    public String getSetupJSON() {
        return "\"abs\": " + abs + ",\n" +
               "      \"tractionControl\": " + tractionControl + ",\n" +
               "      \"brakeBias\": " + brakeBias + ",\n" +
               "      \"frontSuspension\": " + frontSuspension + ",\n" +
               "      \"rearSuspension\": " + rearSuspension + ",\n" +
               "      \"tirePressureFront\": " + tirePressureFront + ",\n" +
               "      \"tirePressureRear\": " + tirePressureRear + ",\n" +
               "      \"fuelLoad\": " + fuelLoad;
    }

    /** CSV token order: abs,tc,brakeBias,frontSusp,rearSusp,tpFront,tpRear,fuel (8 tokens) */
    @Override
    public String getSaveData() {
        return abs + "," + tractionControl + "," + brakeBias + "," +
               frontSuspension + "," + rearSuspension + "," +
               tirePressureFront + "," + tirePressureRear + "," + fuelLoad;
    }

    @Override
    public void applySetupFromCSV(List<String> tokens) {
        this.abs = Integer.parseInt(tokens.get(0));
        this.tractionControl = Integer.parseInt(tokens.get(1));
        this.brakeBias = Double.parseDouble(tokens.get(2));
        this.frontSuspension = Integer.parseInt(tokens.get(3));
        this.rearSuspension = Integer.parseInt(tokens.get(4));
        this.tirePressureFront = Double.parseDouble(tokens.get(5));
        this.tirePressureRear = Double.parseDouble(tokens.get(6));
        this.fuelLoad = Double.parseDouble(tokens.get(7));
    }

    /** ABS and TC are essential; the remaining 6 fields are advanced. */
    @Override
    public int getEssentialColumnCount() { return 2; }

    @Override
    public List<String> getColumnHeaders() {
        return Arrays.asList(
                "ABS", "TC", "Brake Bias %",
                "Susp F", "Susp R",
                "TP Front", "TP Rear",
                "Fuel (L)"
        );
    }

    @Override
    public List<String> getColumnValues() {
        return Arrays.asList(
                String.valueOf(abs),
                String.valueOf(tractionControl),
                String.format("%.1f", brakeBias),
                String.valueOf(frontSuspension),
                String.valueOf(rearSuspension),
                String.format("%.1f", tirePressureFront),
                String.format("%.1f", tirePressureRear),
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

    public int getAbs() { return abs; }
    public void setAbs(int abs) { this.abs = abs; }

    public int getTractionControl() { return tractionControl; }
    public void setTractionControl(int tractionControl) { this.tractionControl = tractionControl; }

    public double getBrakeBias() { return brakeBias; }
    public void setBrakeBias(double brakeBias) { this.brakeBias = brakeBias; }

    public int getFrontSuspension() { return frontSuspension; }
    public void setFrontSuspension(int frontSuspension) { this.frontSuspension = frontSuspension; }

    public int getRearSuspension() { return rearSuspension; }
    public void setRearSuspension(int rearSuspension) { this.rearSuspension = rearSuspension; }

    public double getTirePressureFront() { return tirePressureFront; }
    public void setTirePressureFront(double tirePressureFront) { this.tirePressureFront = tirePressureFront; }

    public double getTirePressureRear() { return tirePressureRear; }
    public void setTirePressureRear(double tirePressureRear) { this.tirePressureRear = tirePressureRear; }

    public double getFuelLoad() { return fuelLoad; }
    public void setFuelLoad(double fuelLoad) { this.fuelLoad = fuelLoad; }
}
