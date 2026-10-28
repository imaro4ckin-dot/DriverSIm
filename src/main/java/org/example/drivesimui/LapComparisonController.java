package org.example.drivesimui;

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

/**
 * Lap Comparison Tool
 *
 * Lets the user pick any 2 logged laps (A and B) and see:
 *  - Both lap times side by side
 *  - A diff table: every setup parameter with color coding
 *      green  = that lap's value correlates with faster time
 *      red    = that lap's value correlates with slower time
 *      gray   = identical values
 *  - A plain-English verdict summary ("Lap A was faster by X.
 *    Key differences: lower ABS, more front wing downforce.")
 */
public class LapComparisonController {

    private Map<String, ObservableList<LapTime>> lapDataMap;

    // Selectors
    private final ComboBox<String> lapASelector = new ComboBox<>();
    private final ComboBox<String> lapBSelector = new ComboBox<>();

    // Header badges
    private final Label lapATime  = new Label("—");
    private final Label lapBTime  = new Label("—");
    private final Label lapACar   = new Label("");
    private final Label lapBCar   = new Label("");
    private final Label lapATrack = new Label("");
    private final Label lapBTrack = new Label("");

    // Diff table
    private final TableView<DiffRow> diffTable = new TableView<>();
    private final ObservableList<DiffRow> diffRows = FXCollections.observableArrayList();

    // Verdict
    private final Label verdictLabel = new Label("Select two laps to compare.");
    private final VBox verdictCard   = new VBox(verdictLabel);

    // All laps in insertion order (for combo boxes)
    private final List<LapTime> allLaps = new ArrayList<>();

    // ─────────────────────────────────────────────────────────────────
    public void init(Map<String, ObservableList<LapTime>> lapDataMap) {
        this.lapDataMap = lapDataMap;
    }

    /** Called by Launcher whenever a new lap is logged — refreshes selectors. */
    public void onNewLap(LapTime lap) {
        allLaps.add(lap);
        String label = buildLapLabel(allLaps.size(), lap);
        lapASelector.getItems().add(label);
        lapBSelector.getItems().add(label);
    }

    // ─────────────────────────────────────────────────────────────────
    public Node build() {
        // ── Selector row ────────────────────────────────────────────
        Label aLbl = new Label("LAP A");
        aLbl.getStyleClass().add("comparison-lap-label");
        lapASelector.setPromptText("Select Lap A…");
        lapASelector.setPrefWidth(320);

        Label bLbl = new Label("LAP B");
        bLbl.getStyleClass().add("comparison-lap-label");
        lapBSelector.setPromptText("Select Lap B…");
        lapBSelector.setPrefWidth(320);

        Button compareBtn = new Button("⇄  COMPARE");
        compareBtn.getStyleClass().add("btn-primary");
        compareBtn.setOnAction(e -> runComparison());

        HBox selectorRow = new HBox(16,
                new VBox(4, aLbl, lapASelector),
                new VBox(4, bLbl, lapBSelector),
                compareBtn);
        selectorRow.setAlignment(Pos.BOTTOM_LEFT);

        // ── Header badges (lap times) ────────────────────────────────
        lapATime.getStyleClass().add("comparison-lap-time");
        lapBTime.getStyleClass().add("comparison-lap-time");
        lapACar.getStyleClass().add("comparison-hint");
        lapBCar.getStyleClass().add("comparison-hint");
        lapATrack.getStyleClass().add("comparison-hint");
        lapBTrack.getStyleClass().add("comparison-hint");

        VBox aBadgeContent = new VBox(2, new Label("LAP A") {{ getStyleClass().add("comparison-lap-label"); }},
                lapATime, lapACar, lapATrack);
        aBadgeContent.setAlignment(Pos.CENTER_LEFT);

        VBox bBadgeContent = new VBox(2, new Label("LAP B") {{ getStyleClass().add("comparison-lap-label"); }},
                lapBTime, lapBCar, lapBTrack);
        bBadgeContent.setAlignment(Pos.CENTER_LEFT);

        VBox aBadge = new VBox(aBadgeContent);
        aBadge.getStyleClass().add("comparison-lap-badge");
        aBadge.setPrefWidth(240);

        VBox bBadge = new VBox(bBadgeContent);
        bBadge.getStyleClass().add("comparison-lap-badge-b");
        bBadge.setPrefWidth(240);

        HBox badgeRow = new HBox(12, aBadge, bBadge);

        VBox headerCard = new VBox(14, selectorRow, badgeRow);
        headerCard.getStyleClass().add("comparison-header-card");

        // ── Diff table ───────────────────────────────────────────────
        buildDiffTable();
        VBox.setVgrow(diffTable, Priority.ALWAYS);

        // ── Verdict card ─────────────────────────────────────────────
        verdictLabel.getStyleClass().add("verdict-text");
        verdictLabel.setWrapText(true);
        verdictCard.getStyleClass().add("verdict-card");
        verdictCard.setPadding(new Insets(12, 18, 12, 18));

        Label hint = new Label("Green = that parameter helped achieve the faster time   |   Red = differed negatively   |   Gray = identical");
        hint.getStyleClass().add("comparison-hint");

        VBox root = new VBox(14, headerCard, hint, diffTable, verdictCard);
        root.getStyleClass().add("comparison-root");
        VBox.setVgrow(diffTable, Priority.ALWAYS);

        ScrollPane sp = new ScrollPane(root);
        sp.setFitToWidth(true);
        sp.setFitToHeight(true);
        sp.setStyle("-fx-background-color: #0D1117; -fx-background: #0D1117;");
        return sp;
    }

    // ─────────────────────────────────────────────────────────────────
    private void buildDiffTable() {
        diffTable.getStyleClass().add("diff-table");
        diffTable.setItems(diffRows);
        diffTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        diffTable.setPlaceholder(new Label("Select two laps and click COMPARE"));

        TableColumn<DiffRow, String> paramCol = new TableColumn<>("PARAMETER");
        paramCol.setCellValueFactory(cd ->
                new javafx.beans.property.SimpleStringProperty(cd.getValue().param()));
        paramCol.setPrefWidth(160);
        paramCol.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().remove("diff-param");
                if (item == null || empty) { setText(null); return; }
                setText(item);
                getStyleClass().add("diff-param");
            }
        });

        TableColumn<DiffRow, String> aCol = new TableColumn<>("LAP A");
        aCol.setCellValueFactory(cd ->
                new javafx.beans.property.SimpleStringProperty(cd.getValue().valueA()));
        aCol.setPrefWidth(120);
        aCol.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("diff-better", "diff-worse", "diff-same");
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
        bCol.setCellValueFactory(cd ->
                new javafx.beans.property.SimpleStringProperty(cd.getValue().valueB()));
        bCol.setPrefWidth(120);
        bCol.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("diff-better", "diff-worse", "diff-same");
                if (item == null || empty) { setText(null); return; }
                setText(item);
                DiffRow row = getTableView().getItems().get(getIndex());
                // B advantage is inverse of A
                switch (row.advantageA()) {
                    case BETTER -> getStyleClass().add("diff-worse");
                    case WORSE  -> getStyleClass().add("diff-better");
                    default     -> getStyleClass().add("diff-same");
                }
            }
        });

        TableColumn<DiffRow, String> noteCol = new TableColumn<>("NOTE");
        noteCol.setCellValueFactory(cd ->
                new javafx.beans.property.SimpleStringProperty(cd.getValue().note()));
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

    // ─────────────────────────────────────────────────────────────────
    private void runComparison() {
        int idxA = lapASelector.getSelectionModel().getSelectedIndex();
        int idxB = lapBSelector.getSelectionModel().getSelectedIndex();
        if (idxA < 0 || idxB < 0 || idxA == idxB) {
            verdictLabel.setText("Please select two different laps.");
            return;
        }

        LapTime lapA = allLaps.get(idxA);
        LapTime lapB = allLaps.get(idxB);

        // Update header badges
        lapATime.setText(lapA.getFormatedTime());
        lapBTime.setText(lapB.getFormatedTime());
        lapACar.setText(lapA.getCarUsed().getMake() + " " + lapA.getCarUsed().getModel());
        lapBCar.setText(lapB.getCarUsed().getMake() + " " + lapB.getCarUsed().getModel());
        lapATrack.setText(lapA.getTrackName());
        lapBTrack.setText(lapB.getTrackName());

        // Which lap is faster?
        boolean aIsFaster = lapA.getTimeInMilliseconds() <= lapB.getTimeInMilliseconds();
        LapTime faster = aIsFaster ? lapA : lapB;
        LapTime slower = aIsFaster ? lapB : lapA;

        diffRows.clear();

        // ── Lap time row ─────────────────────────────────────────────
        diffRows.add(new DiffRow(
                "Lap Time",
                lapA.getFormatedTime(),
                lapB.getFormatedTime(),
                aIsFaster ? Advantage.BETTER : Advantage.WORSE,
                timeDiffNote(lapA.getTimeInMilliseconds(), lapB.getTimeInMilliseconds())));

        // ── Setup params (polymorphic — works for GT3 and Formula) ───
        List<String> headers = lapA.getCarUsed().getColumnHeaders();
        List<String> valsA   = lapA.getCarUsed().getColumnValues();
        List<String> valsB   = lapB.getCarUsed().getColumnValues();

        // If cars differ in type, use the shorter header list
        int colCount = Math.min(headers.size(),
                Math.min(valsA.size(), valsB.size()));

        List<String> changedFavoringFaster = new ArrayList<>();

        for (int i = 0; i < colCount; i++) {
            String valA = valsA.get(i);
            String valB = valsB.get(i);
            String header = headers.get(i);

            if (valA.equals(valB)) {
                diffRows.add(new DiffRow(header, valA, valB, Advantage.SAME, "identical"));
                continue;
            }

            // For numeric params, the faster lap's value "wins"
            Advantage adv = aIsFaster ? Advantage.BETTER : Advantage.WORSE;
            String note = buildNote(header, valA, valB, aIsFaster);

            // Track which params differ for the verdict
            changedFavoringFaster.add(header + " (" +
                    (aIsFaster ? valA : valB) + " vs " + (aIsFaster ? valB : valA) + ")");

            diffRows.add(new DiffRow(header, valA, valB, adv, note));
        }

        // ── Verdict ──────────────────────────────────────────────────
        long gapMs = Math.abs(lapA.getTimeInMilliseconds() - lapB.getTimeInMilliseconds());
        String fasterLapId = aIsFaster ? "Lap A" : "Lap B";
        StringBuilder verdict = new StringBuilder();
        verdict.append(fasterLapId).append(" was faster by ")
               .append(String.format("%.3f", gapMs / 1000.0)).append("s.\n");

        if (changedFavoringFaster.isEmpty()) {
            verdict.append("Setup was identical — time difference is from driving, not setup.");
        } else {
            verdict.append("Setup differences on the faster lap: ");
            verdict.append(String.join(", ", changedFavoringFaster)).append(".");
        }
        verdictLabel.setText(verdict.toString());
    }

    // ─────────────────────────────────────────────────────────────────
    private String buildNote(String param, String valA, String valB, boolean aIsFaster) {
        // Try to parse as numbers for direction hints
        try {
            double dA = Double.parseDouble(valA.replace(",", "."));
            double dB = Double.parseDouble(valB.replace(",", "."));
            double fasterVal = aIsFaster ? dA : dB;
            double slowerVal = aIsFaster ? dB : dA;
            String dir = fasterVal < slowerVal ? "lower on faster lap" : "higher on faster lap";
            return dir;
        } catch (NumberFormatException e) {
            return (aIsFaster ? valA : valB) + " on faster lap";
        }
    }

    private String timeDiffNote(long msA, long msB) {
        long gap = msA - msB;
        if (gap == 0) return "identical";
        return String.format("%+.3f s", gap / 1000.0);
    }

    private String buildLapLabel(int num, LapTime lap) {
        return "#" + num + "  " + lap.getFormatedTime() +
               "  " + lap.getTrackName() +
               "  [" + lap.getCarUsed().getCarType() + "]";
    }

    // ─────────────────────────────────────────────────────────────────
    enum Advantage { BETTER, WORSE, SAME }

    record DiffRow(String param, String valueA, String valueB,
                   Advantage advantageA, String note) {}
}
