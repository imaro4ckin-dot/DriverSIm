package org.example.drivesimui;

public class GT3Car extends Vehicle implements DataExport {

    private int ABS;
    private int TractionControl;


    public GT3Car(String make, String Model, int year){
        super(make,Model, year);

        this.TractionControl = 6 ;
        this.ABS = 6 ;
    }

    public String getSetupGuide(){
        return "GT3 cars rely on mechanical grip. Adjust TC and ABS based on track temperature and tire wear.";
    }


    public String toCSV() {
        return getMake() + "," + getModel() + "," + getYear() + "," +
               TractionControl + "," + ABS;
    }

    @Override
    public String toJSON() {
        return "{\n" +
               "  \"make\": \"" + getMake() + "\",\n" +
               "  \"model\": \"" + getModel() + "\",\n" +
               "  \"tc\": " + TractionControl + ",\n" +
               "  \"abs\": " + ABS + "\n" +
               "}";
    }

    public int getABS(){
        return ABS;

    }

    public void setABS(int ABS) {
        this.ABS = ABS;
    }

    public int getTractionControl() {
        return TractionControl;
    }

    public void setTractionControl(int tractionControl) {
        TractionControl = tractionControl;
    }
}
