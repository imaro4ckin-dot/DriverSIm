package org.example.drivesimui.controller;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.example.drivesimui.LapTime;

import java.net.URL;
import java.util.*;
import java.util.function.Function;

/**
 * Controller for fuel-strategy.fxml.
 */
public class FuelStrategyController implements Initializable {

    @FXML private Spinner<Integer> raceLapsSpinner;
    @FXML private Spinner<Double>  tankCapSpinner;
    @FXML private Spinner<Double>  fuelPerLapSpinner;
    @FXML private Spinner<Integer> safetyCarLapsSpinner;
    @FXML private Spinner<Integer> pitStopsSpinner;
    @FXML private Label            totalFuelValue;
    @FXML private Label            fuelPerStintValue;
    @FXML private Label            stintsValue;
    @FXML private Label            marginValue;
    @FXML private Label            warningLabel;
    @FXML private StackPane        fuelBar;
    @FXML private Region           fuelFill;
    @FXML private TableView<StintRow> stintTable;

    private final ObservableList<StintRow> stintRows = FXCollections.observableArrayList();
    private Map<String, ObservableList<LapTime>> lapDataMap;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        raceLapsSpinner.setValueFactory(      new SpinnerValueFactory.IntegerSpinnerValueFactory(1,   200, 30));
        tankCapSpinner.setValueFactory(        new SpinnerValueFactory.DoubleSpinnerValueFactory(1.0, 200.0, 110.0, 0.5));
        fuelPerLapSpinner.setValueFactory(     new SpinnerValueFactory.DoubleSpinnerValueFactory(0.1, 10.0,   2.5, 0.1));
        safetyCarLapsSpinner.setValueFactory(  new SpinnerValueFactory.IntegerSpinnerValueFactory(0,  20,    2));
        pitStopsSpinner.setValueFactory(       new SpinnerValueFactory.IntegerSpinnerValueFactory(0,  5,     1));
        for (Spinner<?> s : List.of(raceLapsSpinner, tankCapSpinner, fuelPerLapSpinner,
                                    safetyCarLapsSpinner, pitStopsSpinner)) {
            s.setEditable(true);
            s.setPrefWidth(120);
        }
        buildStintTable();
    }

    public void setLapDataMap(Map<String, ObservableList<LapTime>> map) {
        this.lapDataMap = map;
    }

    public void onNewLap(LapTime lap) {
        if (lapDataMap == null) return;
        String key = lap.getTrackName() + "|" + lap.getCarUsed().getCarType();
        ObservableList<LapTime> laps = lapDataMap.get(key);
        if (laps != null && laps.size() >= 2) {
            LapTime prev = laps.get(laps.size() - 2);
            LapTime curr = laps.get(laps.size() - 1);
            List<String> prevVals = prev.getCarUsed().getColumnValues();
            List<String> currVals = curr.getCarUsed().getColumnValues();
            if (!prevVals.isEmpty() && !currVals.isEmpty()) {
                try {
                    double prevFuel = Double.parseDouble(prevVals.get(prevVals.size() - 1));
                    double currFuel = Double.parseDouble(currVals.get(currVals.size() - 1));
                    double diff = prevFuel - currFuel;
                    if (diff > 0.05 && diff < 8.0)
                        fuelPerLapSpinner.getValueFactory().setValue(Math.round(diff * 10.0) / 10.0);
                } catch (NumberFormatException ignored) {}
            }
            double tankCap = "Formula".equals(lap.getCarUsed().getCarType()) ? 110.0 : 120.0;
            tankCapSpinner.getValueFactory().setValue(tankCap);
        }
    }

    @FXML
    private void onCalculate(ActionEvent e) {
        calculate();
    }

    // ── Calculation ────────────────────────────────────────────────────────

    private void calculate() {
        int    raceLaps   = raceLapsSpinner.getValue();
        double tankCap    = tankCapSpinner.getValue();
        double fuelPerLap = fuelPerLapSpinner.getValue();
        int    scLaps     = safetyCarLapsSpinner.getValue();
        int    pitStops   = pitStopsSpinner.getValue();
        int    stints     = pitStops + 1;

        double totalFuelNeeded = (raceLaps * fuelPerLap) + (scLaps * fuelPerLap * 0.6);
        double totalWithBuffer = totalFuelNeeded + fuelPerLap;
        double fuelPerStint    = totalWithBuffer / stints;
        double margin          = tankCap - totalWithBuffer;

        totalFuelValue.setText(String.format("%.1f", totalWithBuffer));
        fuelPerStintValue.setText(String.format("%.1f", fuelPerStint));
        stintsValue.setText(String.valueOf(stints));
        marginValue.setText(String.format("%+.1f", margin));

        marginValue.getStyleClass().removeAll("fuel-result-value","fuel-result-value-ok","fuel-result-value-warn");
        if      (margin >= 3.0) marginValue.getStyleClass().add("fuel-result-value-ok");
        else if (margin >= 0)   marginValue.getStyleClass().add("fuel-result-value");
        else                    marginValue.getStyleClass().add("fuel-result-value-warn");

        totalFuelValue.getStyleClass().removeAll("fuel-result-value","fuel-result-value-ok","fuel-result-value-warn");
        if (totalWithBuffer > tankCap * stints) totalFuelValue.getStyleClass().add("fuel-result-value-warn");
        else                                    totalFuelValue.getStyleClass().add("fuel-result-value-ok");

        if (margin < 0)
            warningLabel.setText(String.format("⚠  FUEL DEFICIT: %.1f L/kg short per stint.", Math.abs(margin)));
        else if (fuelPerStint > tankCap)
            warningLabel.setText(String.format("⚠  Stint fuel (%.1f) exceeds tank (%.1f). Add %d more pit stop(s).",
                    fuelPerStint, tankCap, (int) Math.ceil(fuelPerStint / tankCap) - 1));
        else
            warningLabel.setText("");

        double ratio = Math.min(fuelPerStint / tankCap, 1.0);
        fuelBar.widthProperty().addListener((obs, old, width) -> fuelFill.setMaxWidth(width.doubleValue() * ratio));
        fuelFill.setMaxWidth(fuelBar.getWidth() * ratio);
        fuelFill.getStyleClass().removeAll("fuel-bar-fill","fuel-bar-fill-warn","fuel-bar-fill-crit");
        if      (ratio < 0.75) fuelFill.getStyleClass().add("fuel-bar-fill");
        else if (ratio < 0.95) fuelFill.getStyleClass().add("fuel-bar-fill-warn");
        else                   fuelFill.getStyleClass().add("fuel-bar-fill-crit");

        stintRows.clear();
        int lapsPerStint = raceLaps / stints;
        int remainder    = raceLaps % stints;
        int lapCursor = 1;
        for (int s = 1; s <= stints; s++) {
            int stintLaps  = lapsPerStint + (s <= remainder ? 1 : 0);
            int startLap   = lapCursor;
            int endLap     = lapCursor + stintLaps - 1;
            double fuelLoad = fuelPerStint;
            double fuelAtPit = fuelLoad - (stintLaps * fuelPerLap);
            boolean isLast  = (s == stints);
            stintRows.add(new StintRow(s, startLap, endLap,
                    isLast ? "—" : "Lap " + endLap,
                    String.format("%.1f", fuelLoad),
                    isLast ? "Finish" : String.format("%.1f", Math.max(fuelAtPit, 0)),
                    isLast ? "FINISH" : "PIT"));
            lapCursor = endLap + 1;
        }
    }

    // ── Stint table ────────────────────────────────────────────────────────

    private void buildStintTable() {
        stintTable.getStyleClass().add("stint-table");
        stintTable.setItems(stintRows);
        stintTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        stintTable.setPlaceholder(new Label("Click CALCULATE STRATEGY to generate stint plan"));
        stintTable.getColumns().addAll(
                stintCol("STINT",        100, r -> String.valueOf(r.stint())),
                stintCol("START LAP",    90,  r -> String.valueOf(r.startLap())),
                stintCol("END LAP",      90,  r -> String.valueOf(r.endLap())),
                stintCol("PIT ON",       100, StintRow::pitLap),
                stintCol("FUEL LOAD",    110, StintRow::fuelLoad),
                stintCol("FUEL AT PIT",  110, StintRow::fuelAtPit),
                stintCol("ACTION",       90,  StintRow::action));
    }

    private TableColumn<StintRow, String> stintCol(String title, double width,
                                                    Function<StintRow, String> getter) {
        TableColumn<StintRow, String> col = new TableColumn<>(title);
        col.setCellValueFactory(cd -> new SimpleStringProperty(getter.apply(cd.getValue())));
        col.setPrefWidth(width);
        return col;
    }

    record StintRow(int stint, int startLap, int endLap,
                    String pitLap, String fuelLoad, String fuelAtPit, String action) {}
}
