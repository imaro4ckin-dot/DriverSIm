package org.example.drivesimui.controller;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.example.drivesimui.LapTime;
import org.example.drivesimui.Vehicle;

import java.net.URL;
import java.util.*;

/**
 * Controller for lap-comparison.fxml.
 */
public class LapComparisonController implements Initializable {

    @FXML private ComboBox<String> lapASelector;
    @FXML private ComboBox<String> lapBSelector;
    @FXML private Label            lapATime;
    @FXML private Label            lapBTime;
    @FXML private Label            lapACar;
    @FXML private Label            lapBCar;
    @FXML private Label            lapATrack;
    @FXML private Label            lapBTrack;
    @FXML private TableView<DiffRow> diffTable;
    @FXML private Label            verdictLabel;

    private final ObservableList<DiffRow> diffRows = FXCollections.observableArrayList();
    private final List<LapTime>           allLaps  = new ArrayList<>();
    private Map<String, ObservableList<LapTime>> lapDataMap;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        buildDiffTable();
        diffTable.setItems(diffRows);
    }

    public void setLapDataMap(Map<String, ObservableList<LapTime>> map) {
        this.lapDataMap = map;
    }

    public void onNewLap(LapTime lap) {
        allLaps.add(lap);
        String label = buildLapLabel(allLaps.size(), lap);
        lapASelector.getItems().add(label);
        lapBSelector.getItems().add(label);
    }

    @FXML
    private void onCompare(ActionEvent e) {
        runComparison();
    }

    // ── Diff table ─────────────────────────────────────────────────────────

    private void buildDiffTable() {
        diffTable.getStyleClass().add("diff-table");
        diffTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        diffTable.setPlaceholder(new Label("Select two laps and click COMPARE"));

        TableColumn<DiffRow, String> paramCol = new TableColumn<>("PARAMETER");
        paramCol.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(cd.getValue().param()));
        paramCol.setPrefWidth(160);
        paramCol.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().remove("diff-param");
                if (item == null || empty) { setText(null); return; }
                setText(item); getStyleClass().add("diff-param");
            }
        });

        TableColumn<DiffRow, String> aCol = new TableColumn<>("LAP A");
        aCol.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(cd.getValue().valueA()));
        aCol.setPrefWidth(120);
        aCol.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("diff-better","diff-worse","diff-same");
                if (item == null || empty) { setText(null); return; }
                setText(item);
                DiffRow row = getTableView().getItems().get(getIndex());
                switch (row.advantageA()) {
                    case BETTER -> getStyleClass().add("diff-better");
                    case WORSE  -> getStyleClass().add("diff-worse");
                    default     -> getStyleClass().add("diff-same");
                }
            }
        });

        TableColumn<DiffRow, String> bCol = new TableColumn<>("LAP B");
        bCol.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(cd.getValue().valueB()));
        bCol.setPrefWidth(120);
        bCol.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("diff-better","diff-worse","diff-same");
                if (item == null || empty) { setText(null); return; }
                setText(item);
                DiffRow row = getTableView().getItems().get(getIndex());
                switch (row.advantageA()) {
                    case BETTER -> getStyleClass().add("diff-worse");
                    case WORSE  -> getStyleClass().add("diff-better");
                    default     -> getStyleClass().add("diff-same");
                }
            }
        });

        TableColumn<DiffRow, String> noteCol = new TableColumn<>("NOTE");
        noteCol.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(cd.getValue().note()));
        noteCol.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().add("diff-same");
                if (item == null || empty) { setText(null); return; }
                setText(item);
            }
        });

        diffTable.getColumns().addAll(paramCol, aCol, bCol, noteCol);
    }

    // ── Comparison logic ───────────────────────────────────────────────────

    private void runComparison() {
        int idxA = lapASelector.getSelectionModel().getSelectedIndex();
        int idxB = lapBSelector.getSelectionModel().getSelectedIndex();
        if (idxA < 0 || idxB < 0 || idxA == idxB) {
            verdictLabel.setText("Please select two different laps."); return;
        }

        LapTime lapA = allLaps.get(idxA);
        LapTime lapB = allLaps.get(idxB);

        lapATime.setText(lapA.getFormatedTime());  lapBTime.setText(lapB.getFormatedTime());
        lapACar.setText(lapA.getCarUsed().getMake() + " " + lapA.getCarUsed().getModel());
        lapBCar.setText(lapB.getCarUsed().getMake() + " " + lapB.getCarUsed().getModel());
        lapATrack.setText(lapA.getTrackName());    lapBTrack.setText(lapB.getTrackName());

        boolean aIsFaster = lapA.getTimeInMilliseconds() <= lapB.getTimeInMilliseconds();
        diffRows.clear();

        diffRows.add(new DiffRow("Lap Time", lapA.getFormatedTime(), lapB.getFormatedTime(),
                aIsFaster ? Advantage.BETTER : Advantage.WORSE,
                timeDiffNote(lapA.getTimeInMilliseconds(), lapB.getTimeInMilliseconds())));

        List<String> headers = lapA.getCarUsed().getColumnHeaders();
        List<String> valsA   = lapA.getCarUsed().getColumnValues();
        List<String> valsB   = lapB.getCarUsed().getColumnValues();
        int colCount = Math.min(headers.size(), Math.min(valsA.size(), valsB.size()));

        List<String> changedFavoringFaster = new ArrayList<>();
        for (int i = 0; i < colCount; i++) {
            String valA = valsA.get(i), valB = valsB.get(i), header = headers.get(i);
            if (valA.equals(valB)) {
                diffRows.add(new DiffRow(header, valA, valB, Advantage.SAME, "identical")); continue;
            }
            Advantage adv = aIsFaster ? Advantage.BETTER : Advantage.WORSE;
            changedFavoringFaster.add(header + " (" + (aIsFaster ? valA : valB) + " vs " + (aIsFaster ? valB : valA) + ")");
            diffRows.add(new DiffRow(header, valA, valB, adv, buildNote(header, valA, valB, aIsFaster)));
        }

        long gapMs = Math.abs(lapA.getTimeInMilliseconds() - lapB.getTimeInMilliseconds());
        StringBuilder verdict = new StringBuilder();
        verdict.append(aIsFaster ? "Lap A" : "Lap B").append(" was faster by ")
               .append(String.format("%.3f", gapMs / 1000.0)).append("s.\n");
        if (changedFavoringFaster.isEmpty())
            verdict.append("Setup was identical — time difference is from driving, not setup.");
        else
            verdict.append("Setup differences on the faster lap: ").append(String.join(", ", changedFavoringFaster)).append(".");
        verdictLabel.setText(verdict.toString());
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private String buildNote(String param, String valA, String valB, boolean aIsFaster) {
        try {
            double dA = Double.parseDouble(valA.replace(",", "."));
            double dB = Double.parseDouble(valB.replace(",", "."));
            return (aIsFaster ? dA : dB) < (aIsFaster ? dB : dA) ? "lower on faster lap" : "higher on faster lap";
        } catch (NumberFormatException e) {
            return (aIsFaster ? valA : valB) + " on faster lap";
        }
    }

    private String timeDiffNote(long msA, long msB) {
        long gap = msA - msB;
        return gap == 0 ? "identical" : String.format("%+.3f s", gap / 1000.0);
    }

    private String buildLapLabel(int num, LapTime lap) {
        return "#" + num + "  " + lap.getFormatedTime() + "  " + lap.getTrackName()
               + "  [" + lap.getCarUsed().getCarType() + "]";
    }

    // ── Inner types ────────────────────────────────────────────────────────
    enum Advantage { BETTER, WORSE, SAME }
    record DiffRow(String param, String valueA, String valueB, Advantage advantageA, String note) {}
}
