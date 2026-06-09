package org.example.drivesimui;

public class FormulaCar extends Vehicle implements DataExport{

    private double frontWingAngle;
    private double rearWingAngle;
    private int ersDeployed;


    public FormulaCar(String make, String model, int year){
        super(make, model, year);

        this.frontWingAngle = 10.0;
        this.rearWingAngle = 10.0;
        this.ersDeployed = 3;

    }
    public String getSetupGuide(){
        return "Formula cars rely on aerodynamic grip. Higher wing angles increase downforce but reduce top speed.";
    }

    public String toCSV(){
        return getMake() + "," + getModel() + "," + getYear() + "," +
               frontWingAngle + "," + rearWingAngle + "," + ersDeployed;
    }

    public String toJSON() {
        return "{\n" +
               "  \"make\": \"" + getMake() + "\",\n" +
               "  \"model\": \"" + getModel() + "\",\n" +
               "  \"frontWing\": " + frontWingAngle + ",\n" +
               "  \"rearWing\": " + rearWingAngle + "\n" +
               "}";
    }

    public double getFrontWingAngle() {
        return frontWingAngle;
    }

    public void setFrontWingAngle(double frontWingAngle) {
        this.frontWingAngle = frontWingAngle;
    }

    public double getRearWingAngle() {
        return rearWingAngle;
    }

    public void setRearWingAngle(double rearWingAngle){
        this.rearWingAngle = rearWingAngle;
    }

}
