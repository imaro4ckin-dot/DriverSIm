package org.example.drivesimui;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

/**
 * Fuel Strategy Calculator
 *
 * Inputs (user-configurable):
 *   - Race laps
 *   - Tank capacity (L or kg — pre-filled from car type)
 *   - Fuel consumption per lap (auto-estimated from logged laps if available)
 *   - Safety car laps (adds fuel buffer)
 *   - Number of pit stops planned
 *
 * Outputs:
 *   - Total fuel required
 *   - Fuel per stint
 *   - Stint plan table: Stint | Start Lap | End Lap | Pit Lap | Fuel Load | Fuel at Pit
 *   - Fuel level bar (color: green → yellow → red)
 *   - Warning if fuel required exceeds tank capacity
 */
public class FuelStrategyController {

    private Map<String, ObservableList<LapTime>> lapDataMap;

    // ── Inputs ───────────────────────────────────────────────────────
    private final Spinner<Integer> raceLapsSpinner      = new Spinner<>(1, 200, 30);
    private final Spinner<Double>  tankCapSpinner       = new Spinner<>(1.0, 200.0, 110.0, 0.5);
    private final Spinner<Double>  fuelPerLapSpinner    = new Spinner<>(0.1, 10.0, 2.5, 0.1);
    private final Spinner<Integer> safetyCarLapsSpinner = new Spinner<>(0, 20, 2);
    private final Spinner<Integer> pitStopsSpinner      = new Spinner<>(0, 5, 1);

    // ── Result cards ────────────────────────────────────────────────
    private final Label totalFuelValue   = new Label("—");
    private final Label fuelPerStintValue= new Label("—");
    private final Label stintsValue      = new Label("—");
    private final Label marginValue      = new Label("—");
    private final Label warningLabel     = new Label("");

    // ── Fuel bar ────────────────────────────────────────────────────
    private final StackPane fuelBar = new StackPane();
    private final Region    fuelFill = new Region();

    // ── Stint table ─────────────────────────────────────────────────
    private final TableView<StintRow> stintTable = new TableView<>();
    private final ObservableList<StintRow> stintRows = FXCollections.observableArrayList();

    // ─────────────────────────────────────────────────────────────────
    public void init(Map<String, ObservableList<LapTime>> lapDataMap) {
        this.lapDataMap = lapDataMap;
    }

    /**
     * Called when a new lap is logged so we can auto-estimate fuel per lap
     * from the car's fuel slider value across consecutive laps.
     */
    public void onNewLap(LapTime lap) {
        // Auto-estimate fuel consumption: use fuelLoad diff between consecutive laps
        // when available (heuristic — real telemetry would provide this directly)
        String key = lap.getTrackName() + "|" + lap.getCarUsed().getCarType();
        ObservableList<LapTime> laps = lapDataMap.get(key);
        if (laps != null && laps.size() >= 2) {
            LapTime prev = laps.get(laps.size() - 2);
            LapTime curr = laps.get(laps.size() - 1);
            // Both values from getColumnValues — fuel is always the last column
            List<String> prevVals = prev.getCarUsed().getColumnValues();
            List<String> currVals = curr.getCarUsed().getColumnValues();
            if (!prevVals.isEmpty() && !currVals.isEmpty()) {
                try {
                    double prevFuel = Double.parseDouble(prevVals.get(prevVals.size() - 1));
                    double currFuel = Double.parseDouble(currVals.get(currVals.size() - 1));
                    double diff = prevFuel - currFuel;
                    if (diff > 0.05 && diff < 8.0) {
                        // Reasonable consumption range — update spinner silently
                        fuelPerLapSpinner.getValueFactory().setValue(
                                Math.round(diff * 10.0) / 10.0);
                    }
                } catch (NumberFormatException ignored) {}
            }

            // Also update tank capacity from the car
            double tankCap = lap.getCarUsed().getCarType().equals("Formula") ? 110.0 : 120.0;
            tankCapSpinner.getValueFactory().setValue(tankCap);
        }
    }

    // ─────────────────────────────────────────────────────────────────
    public Node build() {
        // ── Input grid ───────────────────────────────────────────────
        configureSpinner(raceLapsSpinner);
        configureSpinner(tankCapSpinner);
        configureSpinner(fuelPerLapSpinner);
        configureSpinner(safetyCarLapsSpinner);
        configureSpinner(pitStopsSpinner);

        GridPane inputGrid = new GridPane();
        inputGrid.setHgap(16);
        inputGrid.setVgap(10);
        inputGrid.getColumnConstraints().addAll(
                colConstraint(200), colConstraint(140),
                colConstraint(200), colConstraint(140));

        addInputRow(inputGrid, 0, 0, "Race Laps",            raceLapsSpinner);
        addInputRow(inputGrid, 0, 2, "Tank Capacity (L/kg)", tankCapSpinner);
        addInputRow(inputGrid, 1, 0, "Fuel per Lap (L/kg)",  fuelPerLapSpinner);
        addInputRow(inputGrid, 1, 2, "Safety Car Laps",      safetyCarLapsSpinner);
        addInputRow(inputGrid, 2, 0, "Planned Pit Stops",    pitStopsSpinner);

        Button calcBtn = new Button("⚙  CALCULATE STRATEGY");
        calcBtn.getStyleClass().add("btn-calculate");
        calcBtn.setOnAction(e -> calculate());

        VBox inputCard = new VBox(14, inputGrid, calcBtn);
        inputCard.getStyleClass().add("fuel-input-card");

        // ── Result cards ─────────────────────────────────────────────
        totalFuelValue.getStyleClass().add("fuel-result-value");
        fuelPerStintValue.getStyleClass().add("fuel-result-value");
        stintsValue.getStyleClass().add("fuel-result-value");
        marginValue.getStyleClass().add("fuel-result-value");

        HBox resultRow = new HBox(12,
                buildResultCard("TOTAL FUEL",    totalFuelValue),
                buildResultCard("FUEL / STINT",  fuelPerStintValue),
                buildResultCard("STINTS",        stintsValue),
                buildResultCard("SAFETY MARGIN", marginValue));

        // ── Fuel bar ─────────────────────────────────────────────────
        fuelFill.getStyleClass().add("fuel-bar-fill");
        fuelFill.setMaxWidth(0);
        Region barBg = new Region();
        barBg.getStyleClass().add("fuel-bar-bg");
        barBg.setPrefHeight(10);
        StackPane.setAlignment(fuelFill, Pos.CENTER_LEFT);
        fuelBar.getChildren().addAll(barBg, fuelFill);
        fuelBar.setPrefHeight(10);

        Label barLabel = new Label("FUEL USAGE vs TANK CAPACITY");
        barLabel.getStyleClass().add("fuel-result-label");

        warningLabel.getStyleClass().add("fuel-warning-label");
        warningLabel.setWrapText(true);

        VBox barSection = new VBox(6, barLabel, fuelBar, warningLabel);

        // ── Stint table ──────────────────────────────────────────────
        buildStintTable();

        Label stintTitle = new Label("STINT PLAN");
        stintTitle.getStyleClass().add("fuel-result-label");

        VBox root = new VBox(14, inputCard, resultRow, barSection, stintTitle, stintTable);
        root.getStyleClass().add("fuel-root");
        VBox.setVgrow(stintTable, Priority.ALWAYS);

        ScrollPane sp = new ScrollPane(root);
        sp.setFitToWidth(true);
        sp.setFitToHeight(true);
        sp.setStyle("-fx-background-color: #0D1117; -fx-background: #0D1117;");
        return sp;
    }

    // ─────────────────────────────────────────────────────────────────
    private void calculate() {
        int    raceLaps      = raceLapsSpinner.getValue();
        double tankCap       = tankCapSpinner.getValue();
        double fuelPerLap    = fuelPerLapSpinner.getValue();
        int    scLaps        = safetyCarLapsSpinner.getValue();
        int    pitStops      = pitStopsSpinner.getValue();
        int    stints        = pitStops + 1;

        // SC laps consume fuel but at ~60% rate (simplified)
        double totalFuelNeeded = (raceLaps * fuelPerLap) + (scLaps * fuelPerLap * 0.6);
        // Add 1-lap buffer for safety
        double totalWithBuffer = totalFuelNeeded + fuelPerLap;

        double fuelPerStint = totalWithBuffer / stints;
        double margin = tankCap - totalWithBuffer;

        // Update result cards
        totalFuelValue.setText(String.format("%.1f", totalWithBuffer));
        fuelPerStintValue.setText(String.format("%.1f", fuelPerStint));
        stintsValue.setText(String.valueOf(stints));
        marginValue.setText(String.format("%+.1f", margin));

        // Color code margin
        marginValue.getStyleClass().removeAll(
                "fuel-result-value", "fuel-result-value-ok", "fuel-result-value-warn");
        if (margin >= 3.0)       marginValue.getStyleClass().add("fuel-result-value-ok");
        else if (margin >= 0)    marginValue.getStyleClass().add("fuel-result-value");
        else                     marginValue.getStyleClass().add("fuel-result-value-warn");

        // Color code total
        totalFuelValue.getStyleClass().removeAll(
                "fuel-result-value", "fuel-result-value-ok", "fuel-result-value-warn");
        if (totalWithBuffer > tankCap * stints)
             totalFuelValue.getStyleClass().add("fuel-result-value-warn");
        else totalFuelValue.getStyleClass().add("fuel-result-value-ok");

        // Warning label
        if (margin < 0) {
            warningLabel.setText(String.format(
                    "⚠  FUEL DEFICIT: %.1f L/kg short per stint. Increase pit stops or reduce race pace.",
                    Math.abs(margin)));
        } else if (fuelPerStint > tankCap) {
            warningLabel.setText(String.format(
                    "⚠  Stint fuel (%.1f) exceeds tank (%.1f). Add at least %d more pit stop(s).",
                    fuelPerStint, tankCap,
                    (int) Math.ceil(fuelPerStint / tankCap) - 1));
        } else {
            warningLabel.setText("");
        }

        // Fuel bar — proportion of tank used per stint
        double ratio = Math.min(fuelPerStint / tankCap, 1.0);
        fuelBar.widthProperty().addListener((obs, old, width) ->
                fuelFill.setMaxWidth(width.doubleValue() * ratio));
        fuelFill.setMaxWidth(fuelBar.getWidth() * ratio);

        fuelFill.getStyleClass().removeAll(
                "fuel-bar-fill", "fuel-bar-fill-warn", "fuel-bar-fill-crit");
        if      (ratio < 0.75) fuelFill.getStyleClass().add("fuel-bar-fill");
        else if (ratio < 0.95) fuelFill.getStyleClass().add("fuel-bar-fill-warn");
        else                   fuelFill.getStyleClass().add("fuel-bar-fill-crit");

        // Build stint rows
        stintRows.clear();
        int lapsPerStint = raceLaps / stints;
        int remainder    = raceLaps % stints;

        int lapCursor = 1;
        for (int s = 1; s <= stints; s++) {
            int stintLaps   = lapsPerStint + (s <= remainder ? 1 : 0);
            int startLap    = lapCursor;
            int endLap      = lapCursor + stintLaps - 1;
            double fuelLoad = fuelPerStint;
            double fuelAtPit = fuelLoad - (stintLaps * fuelPerLap);
            boolean isLast  = (s == stints);

            stintRows.add(new StintRow(
                    s,
                    startLap,
                    endLap,
                    isLast ? "—" : "Lap " + endLap,
                    String.format("%.1f", fuelLoad),
                    isLast ? "Finish" : String.format("%.1f", Math.max(fuelAtPit, 0)),
                    isLast ? "FINISH" : "PIT"));

            lapCursor = endLap + 1;
        }
    }

    // ─────────────────────────────────────────────────────────────────
    private void buildStintTable() {
        stintTable.getStyleClass().add("stint-table");
        stintTable.setItems(stintRows);
        stintTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        stintTable.setPlaceholder(new Label("Click CALCULATE STRATEGY to generate stint plan"));
        stintTable.setPrefHeight(220);

        stintTable.getColumns().addAll(
                stintCol("STINT",       100, r -> String.valueOf(r.stint())),
                stintCol("START LAP",   90,  r -> String.valueOf(r.startLap())),
                stintCol("END LAP",     90,  r -> String.valueOf(r.endLap())),
                stintCol("PIT ON",      100, r -> r.pitLap()),
                stintCol("FUEL LOAD",   110, r -> r.fuelLoad()),
                stintCol("FUEL AT PIT", 110, r -> r.fuelAtPit()),
                stintCol("ACTION",      90,  r -> r.action()));
    }

    private TableColumn<StintRow, String> stintCol(
            String title, double width, java.util.function.Function<StintRow, String> getter) {
        TableColumn<StintRow, String> col = new TableColumn<>(title);
        col.setCellValueFactory(cd -> new SimpleStringProperty(getter.apply(cd.getValue())));
        col.setPrefWidth(width);
        return col;
    }

    private VBox buildResultCard(String title, Label valueLabel) {
        Label lbl = new Label(title);
        lbl.getStyleClass().add("fuel-result-label");
        VBox card = new VBox(4, lbl, valueLabel);
        card.getStyleClass().add("fuel-result-card");
        card.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(card, Priority.ALWAYS);
        return card;
    }

    private void addInputRow(GridPane grid, int row, int col,
                             String label, Control control) {
        Label lbl = new Label(label);
        lbl.getStyleClass().add("fuel-field-label");
        grid.add(lbl,     col,     row);
        grid.add(control, col + 1, row);
    }

    private ColumnConstraints colConstraint(double width) {
        ColumnConstraints cc = new ColumnConstraints();
        cc.setPrefWidth(width);
        return cc;
    }

    private <T> void configureSpinner(Spinner<T> spinner) {
        spinner.setEditable(true);
        spinner.setPrefWidth(120);
    }

    // ─────────────────────────────────────────────────────────────────
    record StintRow(int stint, int startLap, int endLap,
                    String pitLap, String fuelLoad,
                    String fuelAtPit, String action) {}
}
