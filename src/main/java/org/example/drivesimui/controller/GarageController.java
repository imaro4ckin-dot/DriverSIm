package org.example.drivesimui.controller;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.chart.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import org.example.drivesimui.*;
import org.example.drivesimui.util.TimeFormatter;

import java.io.File;
import java.net.URL;
import java.util.*;

/**
 * Controller for garage.fxml.
 * Contains all lap-logging, tab management, setup slider, and AI logic
 * that previously lived in Launcher.java.
 */
public class GarageController implements Initializable {

    // ── FXML fields ────────────────────────────────────────────────────────
    @FXML private TabPane       trackTabPane;
    @FXML private TextField     trackInput;
    @FXML private TextField     timeInput;
    @FXML private TextField     s1Input;
    @FXML private TextField     s2Input;
    @FXML private TextField     s3Input;
    @FXML private ComboBox<String> carTypeDropdown;
    @FXML private TextField     newCarInput;
    @FXML private ComboBox<String> weatherDropdown;
    @FXML private VBox          dynamicSetupContainer;
    @FXML private TextArea      aiOutputBox;
    @FXML private Button        askEngineerBtn;

    // ── Setup sliders (built programmatically, placed in dynamicSetupContainer) ──
    private Slider absSlider, tcSlider, brakeBiasSlider;
    private Slider frontSuspSlider, rearSuspSlider;
    private Slider tpFrontSlider, tpRearSlider, gt3FuelSlider;
    private Slider fwSlider, rwSlider, diffEntrySlider, diffExitSlider, formulaFuelSlider;
    private ComboBox<String> ersModeDropdown;
    private CheckBox drsCheckBox;

    // ── State maps ─────────────────────────────────────────────────────────
    private final Map<String, ObservableList<LapTime>>           lapDataMap     = new HashMap<>();
    private final Map<String, XYChart.Series<String, Number>>    seriesMap      = new HashMap<>();
    private final Map<String, Integer>                           lapCountMap    = new HashMap<>();
    private final Map<String, List<TableColumn<LapTime, String>>> advancedColMap = new HashMap<>();
    private final Map<String, TabPane>  trackSubTabPanes = new HashMap<>();
    private final Map<String, Tab>      carTypeTabs      = new HashMap<>();
    private final Map<String, Label>    sectorS1Labels   = new HashMap<>();
    private final Map<String, Label>    sectorS2Labels   = new HashMap<>();
    private final Map<String, Label>    sectorS3Labels   = new HashMap<>();
    private final Map<String, Label>    sectorTheoLabels = new HashMap<>();

    // ── Cross-controller ref ────────────────────────────────────────────────
    private MainController mainController;
    private final GarageManger manager = new GarageManger();

    // ─────────────────────────────────────────────────────────────────────────
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        buildCarDropdown();
        buildWeatherDropdown();
        buildSetupControls();
        loadSavedLaps();
    }

    public void setMainController(MainController mc) {
        this.mainController = mc;
    }

    /** Repositories are managed by garageIO internally; this method is a no-op hook for MainController. */
    public void setRepositories(org.example.drivesimui.db.LapRepository lapRepo,
                                 org.example.drivesimui.db.SessionRepository sessionRepo) {
        // garageIO delegates to LapRepository already; no additional wiring needed here
    }

    public Map<String, ObservableList<LapTime>> getLapDataMap() {
        return lapDataMap;
    }

    /** Called by MainController when the stopwatch stops — puts elapsed ms into the time field. */
    public void fillTimeFromStopwatch(long ms) {
        if (ms > 0) timeInput.setText(String.valueOf(ms));
    }

    // ── FXML action handlers ───────────────────────────────────────────────

    @FXML
    private void onAddLap(ActionEvent e) {
        try {
            String track = trackInput.getText().trim();
            if (track.isEmpty()) { showAlert("Please enter a track name."); return; }
            long timeMs = TimeFormatter.parseTimeInput(timeInput.getText().trim());
            Vehicle car = buildCarFromUI();

            LapTime lap = new LapTime(track, timeMs, car);
            if (!s1Input.getText().isBlank())
                try { lap.setS1Ms(TimeFormatter.parseTimeInput(s1Input.getText().trim())); } catch (IllegalArgumentException ignored) {}
            if (!s2Input.getText().isBlank())
                try { lap.setS2Ms(TimeFormatter.parseTimeInput(s2Input.getText().trim())); } catch (IllegalArgumentException ignored) {}
            if (!s3Input.getText().isBlank())
                try { lap.setS3Ms(TimeFormatter.parseTimeInput(s3Input.getText().trim())); } catch (IllegalArgumentException ignored) {}

            lap.setWeather(LapTime.Weather.valueOf(weatherDropdown.getValue()));

            processNewLap(lap);
            trackInput.clear();
            timeInput.clear();
            s1Input.clear(); s2Input.clear(); s3Input.clear();
        } catch (IllegalArgumentException ex) {
            showAlert(ex.getMessage());
        }
    }

    @FXML
    private void onAddCar(ActionEvent e) {
        String name = newCarInput.getText().trim();
        if (!name.isEmpty()) {
            carTypeDropdown.getItems().add(name);
            carTypeDropdown.setValue(name);
            newCarInput.clear();
        }
    }

    @FXML
    private void onAnalyzeSession(ActionEvent e) {
        Tab trackTab = trackTabPane.getSelectionModel().getSelectedItem();
        if (trackTab == null) { aiOutputBox.setText("No track selected."); return; }

        String activeTrack = trackTab.getText();
        TabPane subPane = trackSubTabPanes.get(activeTrack);
        Tab carTab = subPane == null ? null : subPane.getSelectionModel().getSelectedItem();
        String carType = carTab == null ? "" : carTab.getText();
        String key = activeTrack + "|" + carType;

        ObservableList<LapTime> laps = lapDataMap.get(key);
        if (laps == null || laps.isEmpty()) {
            aiOutputBox.setText("No laps logged for " + activeTrack + " / " + carType + " yet.");
            return;
        }

        aiOutputBox.setText("[ Radio ] Engineer reviewing " + activeTrack + " [" + carType + "]... standby...");
        askEngineerBtn.setDisable(true);

        StringBuilder prompt = new StringBuilder();
        prompt.append("You are an expert race engineer. Analyze this lap data for ")
              .append(activeTrack).append(" (").append(carType).append(" car). ");
        prompt.append("Give 3 short bullet points of advice to improve lap times. JSON: [\n");
        for (int i = 0; i < laps.size(); i++) {
            prompt.append(laps.get(i).toJSON());
            if (i < laps.size() - 1) prompt.append(",");
        }
        prompt.append("\n]");

        new Thread(() -> {
            String advice = new AIClient().askEngineer(prompt.toString());
            Platform.runLater(() -> {
                aiOutputBox.setText(advice);
                askEngineerBtn.setDisable(false);
            });
        }).start();
    }

    // ── Core lap processing ────────────────────────────────────────────────

    public void processNewLap(LapTime newLap) {
        String track   = newLap.getTrackName();
        String carType = newLap.getCarUsed().getCarType();
        String key     = track + "|" + carType;

        manager.addLap(newLap);
        garageIO.insertLap(newLap);

        if (!trackSubTabPanes.containsKey(track)) {
            TabPane carTypeTabPane = new TabPane();
            carTypeTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
            trackSubTabPanes.put(track, carTypeTabPane);
            Tab trackTab = new Tab(track, carTypeTabPane);
            trackTabPane.getTabs().add(trackTab);
            trackTabPane.getSelectionModel().select(trackTab);
        }

        if (!carTypeTabs.containsKey(key)) {
            ObservableList<LapTime> trackCarData = FXCollections.observableArrayList();
            lapDataMap.put(key, trackCarData);

            TableView<LapTime> table = buildTable(key, trackCarData, newLap);

            ScrollPane tableScroll = new ScrollPane(table);
            tableScroll.setFitToHeight(true);
            tableScroll.setFitToWidth(false);
            tableScroll.setPrefWidth(520);
            tableScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
            tableScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

            // Export toolbar
            List<TableColumn<LapTime, String>> advancedCols = advancedColMap.getOrDefault(key, new ArrayList<>());
            List<String> headers = newLap.getCarUsed().getColumnHeaders();
            List<String> exportSelectedCols = new ArrayList<>(headers);
            HBox exportBar = buildExportBar(key, track, carType, table, advancedCols, headers, exportSelectedCols);

            // Chart
            CategoryAxis xAxis = new CategoryAxis(); xAxis.setLabel("Lap");
            NumberAxis   yAxis = new NumberAxis();   yAxis.setLabel("Time (s)"); yAxis.setAutoRanging(true);
            LineChart<String, Number> chart = new LineChart<>(xAxis, yAxis);
            chart.setTitle(track + "  ·  " + carType);
            chart.setPrefWidth(440); chart.setPrefHeight(290);
            chart.setAnimated(true); chart.setCreateSymbols(true); chart.setLegendVisible(false);

            XYChart.Series<String, Number> series = new XYChart.Series<>();
            series.setName("Lap Trend");
            chart.getData().add(series);
            seriesMap.put(key, series);
            lapCountMap.put(key, 0);

            VBox tableSection = new VBox(6, exportBar, tableScroll, buildSectorSummary(key, trackCarData));
            HBox content = new HBox(14, tableSection, chart);
            content.setPadding(new Insets(10));
            HBox.setHgrow(chart, Priority.ALWAYS);

            Tab carTab = new Tab(carType, content);
            trackSubTabPanes.get(track).getTabs().add(carTab);
            trackSubTabPanes.get(track).getSelectionModel().select(carTab);
            carTypeTabs.put(key, carTab);
        }

        ObservableList<LapTime> list = lapDataMap.get(key);
        if (!list.isEmpty())
            newLap.setDeltaMs(newLap.getTimeInMilliseconds() - list.get(list.size() - 1).getTimeInMilliseconds());
        list.add(newLap);

        int lapNum = lapCountMap.get(key) + 1;
        lapCountMap.put(key, lapNum);
        seriesMap.get(key).getData().add(
                new XYChart.Data<>("L" + lapNum, newLap.getTimeInMilliseconds() / 1000.0));

        updateSectorSummary(key);
        if (mainController != null) mainController.onLapLogged(newLap, key);
    }

    // ── Table builder ─────────────────────────────────────────────────────

    private TableView<LapTime> buildTable(String key, ObservableList<LapTime> trackCarData, LapTime firstLap) {
        TableView<LapTime> table = new TableView<>();
        table.setItems(trackCarData);
        table.setPrefHeight(260);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<LapTime, String> lapNumCol = new TableColumn<>("#");
        lapNumCol.setCellValueFactory(cd -> new SimpleStringProperty(
                String.valueOf(trackCarData.indexOf(cd.getValue()) + 1)));
        lapNumCol.setPrefWidth(36);
        lapNumCol.setStyle("-fx-alignment: CENTER;");

        TableColumn<LapTime, String> timeCol = new TableColumn<>("LAP TIME");
        timeCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getFormatedTime()));
        timeCol.setPrefWidth(95);

        TableColumn<LapTime, String> deltaCol = new TableColumn<>("SPLIT");
        deltaCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getFormattedDelta()));
        deltaCol.setPrefWidth(75);
        deltaCol.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("delta-faster", "delta-slower", "delta-neutral");
                if (item == null || empty) { setText(null); return; }
                setText(item);
                if (item.startsWith("+"))                           getStyleClass().add("delta-slower");
                else if (item.startsWith("-") && item.length() > 1) getStyleClass().add("delta-faster");
                else                                                getStyleClass().add("delta-neutral");
            }
        });

        TableColumn<LapTime, String> carCol = new TableColumn<>("CAR");
        carCol.setCellValueFactory(cd -> new SimpleStringProperty(
                cd.getValue().getCarUsed().getMake() + " " + cd.getValue().getCarUsed().getModel()));
        carCol.setPrefWidth(160);

        // Sector columns
        TableColumn<LapTime, String> s1Col = sectorCol("S1", 1, trackCarData);
        TableColumn<LapTime, String> s2Col = sectorCol("S2", 2, trackCarData);
        TableColumn<LapTime, String> s3Col = sectorCol("S3", 3, trackCarData);

        // Weather column
        TableColumn<LapTime, String> weatherCol = new TableColumn<>("COND");
        weatherCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getWeather().name()));
        weatherCol.setPrefWidth(72);
        weatherCol.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(null);
                if (item == null || empty) { setGraphic(null); return; }
                Label pill = new Label(item);
                pill.getStyleClass().addAll("weather-pill", "weather-" + item);
                setGraphic(pill);
            }
        });

        table.getColumns().addAll(lapNumCol, timeCol, deltaCol, carCol, s1Col, s2Col, s3Col, weatherCol);

        // Setup columns
        List<String> headers = firstLap.getCarUsed().getColumnHeaders();
        int essential = firstLap.getCarUsed().getEssentialColumnCount();
        List<TableColumn<LapTime, String>> advancedCols = new ArrayList<>();
        for (int i = 0; i < headers.size(); i++) {
            final int idx = i;
            TableColumn<LapTime, String> col = new TableColumn<>(headers.get(idx).toUpperCase());
            col.setCellValueFactory(cd -> {
                List<String> vals = cd.getValue().getCarUsed().getColumnValues();
                return new SimpleStringProperty(idx < vals.size() ? vals.get(idx) : "—");
            });
            col.setPrefWidth(75);
            if (i >= essential) { col.setVisible(false); advancedCols.add(col); }
            table.getColumns().add(col);
        }
        advancedColMap.put(key, advancedCols);
        return table;
    }

    private TableColumn<LapTime, String> sectorCol(String header, int sector,
                                                    ObservableList<LapTime> data) {
        TableColumn<LapTime, String> col = new TableColumn<>(header);
        col.setCellValueFactory(cd -> new SimpleStringProperty(switch (sector) {
            case 1 -> cd.getValue().getFormattedS1();
            case 2 -> cd.getValue().getFormattedS2();
            default -> cd.getValue().getFormattedS3();
        }));
        col.setPrefWidth(80);
        col.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("sector-cell", "sector-cell-best");
                if (item == null || empty) { setText(null); return; }
                setText(item);
                LapTime lap = getTableView().getItems().get(getIndex());
                long sMs = switch (sector) {
                    case 1 -> lap.getS1Ms(); case 2 -> lap.getS2Ms(); default -> lap.getS3Ms();
                };
                if (sMs > 0 && isBestSector(data, sMs, sector)) getStyleClass().add("sector-cell-best");
                else getStyleClass().add("sector-cell");
            }
        });
        return col;
    }

    // ── Export toolbar ────────────────────────────────────────────────────

    private HBox buildExportBar(String key, String track, String carType,
                                TableView<LapTime> table,
                                List<TableColumn<LapTime, String>> advancedCols,
                                List<String> headers, List<String> exportSelectedCols) {
        Button showAdvancedBtn = new Button("Show Advanced");
        showAdvancedBtn.setOnAction(e -> {
            boolean show = !advancedCols.isEmpty() && !advancedCols.get(0).isVisible();
            advancedCols.forEach(col -> col.setVisible(show));
            showAdvancedBtn.setText(show ? "Hide Advanced" : "Show Advanced");
        });

        ComboBox<String> formatBox = new ComboBox<>(FXCollections.observableArrayList("CSV", "PDF"));
        formatBox.setValue("CSV"); formatBox.setPrefWidth(72);

        Button columnsBtn = new Button("Columns");
        columnsBtn.setOnAction(e -> showColumnEditor(headers, exportSelectedCols, table, advancedCols));

        Button exportBtn = new Button("⬇  Export");
        exportBtn.getStyleClass().add("btn-success");
        exportBtn.setOnAction(e -> {
            ObservableList<LapTime> laps = lapDataMap.get(key);
            if (laps == null || laps.isEmpty()) { showAlert("No laps to export for this session."); return; }
            LapExporter.Format fmt = "PDF".equals(formatBox.getValue()) ? LapExporter.Format.PDF : LapExporter.Format.CSV;
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Save Export");
            chooser.setInitialFileName(track + "_" + carType + "." + fmt.name().toLowerCase());
            chooser.getExtensionFilters().add(fmt == LapExporter.Format.PDF
                    ? new FileChooser.ExtensionFilter("PDF Files", "*.pdf")
                    : new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
            File file = chooser.showSaveDialog(null);
            if (file == null) return;
            try {
                new LapExporter().export(new ArrayList<>(laps), exportSelectedCols,
                        file.getAbsolutePath(), fmt);
                showAlert("Exported " + laps.size() + " laps to:\n" + file.getAbsolutePath());
            } catch (Exception ex) {
                showAlert("Export failed: " + ex.getMessage());
            }
        });

        HBox bar = new HBox(8, showAdvancedBtn, columnsBtn, formatBox, exportBtn);
        bar.getStyleClass().add("export-bar");
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    // ── Sector summary ────────────────────────────────────────────────────

    private HBox buildSectorSummary(String key, ObservableList<LapTime> laps) {
        Label s1Lbl = new Label("BEST S1"); s1Lbl.getStyleClass().add("sector-label");
        Label s2Lbl = new Label("BEST S2"); s2Lbl.getStyleClass().add("sector-label");
        Label s3Lbl = new Label("BEST S3"); s3Lbl.getStyleClass().add("sector-label");
        Label thLbl = new Label("THEORETICAL BEST"); thLbl.getStyleClass().add("theo-best-label");

        Label s1Val = new Label("—"); s1Val.getStyleClass().add("sector-value");
        Label s2Val = new Label("—"); s2Val.getStyleClass().add("sector-value");
        Label s3Val = new Label("—"); s3Val.getStyleClass().add("sector-value");
        Label thVal = new Label("—"); thVal.getStyleClass().add("theo-best-value");

        sectorS1Labels.put(key, s1Val);
        sectorS2Labels.put(key, s2Val);
        sectorS3Labels.put(key, s3Val);
        sectorTheoLabels.put(key, thVal);

        VBox s1c = new VBox(2, s1Lbl, s1Val); VBox s2c = new VBox(2, s2Lbl, s2Val);
        VBox s3c = new VBox(2, s3Lbl, s3Val); VBox thc = new VBox(2, thLbl, thVal);
        for (VBox c : List.of(s1c, s2c, s3c, thc)) c.getStyleClass().add("sector-summary-card");

        HBox bar = new HBox(8, s1c, s2c, s3c, thc);
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    private void updateSectorSummary(String key) {
        ObservableList<LapTime> laps = lapDataMap.get(key);
        if (laps == null) return;

        long bestS1 = laps.stream().mapToLong(LapTime::getS1Ms).filter(v -> v > 0).min().orElse(0);
        long bestS2 = laps.stream().mapToLong(LapTime::getS2Ms).filter(v -> v > 0).min().orElse(0);
        long bestS3 = laps.stream().mapToLong(LapTime::getS3Ms).filter(v -> v > 0).min().orElse(0);

        if (sectorS1Labels.containsKey(key)) sectorS1Labels.get(key).setText(bestS1 > 0 ? TimeFormatter.formatMs(bestS1) : "—");
        if (sectorS2Labels.containsKey(key)) sectorS2Labels.get(key).setText(bestS2 > 0 ? TimeFormatter.formatMs(bestS2) : "—");
        if (sectorS3Labels.containsKey(key)) sectorS3Labels.get(key).setText(bestS3 > 0 ? TimeFormatter.formatMs(bestS3) : "—");

        if (sectorTheoLabels.containsKey(key)) {
            if (bestS1 > 0 && bestS2 > 0 && bestS3 > 0) {
                long theo = bestS1 + bestS2 + bestS3;
                sectorTheoLabels.get(key).setText(TimeFormatter.formatMs(theo));
                long actualBest = laps.stream().mapToLong(LapTime::getTimeInMilliseconds).min().orElse(Long.MAX_VALUE);
                sectorTheoLabels.get(key).getStyleClass().removeAll("theo-best-value", "kpi-value-red");
                sectorTheoLabels.get(key).getStyleClass().add(theo < actualBest ? "kpi-value-red" : "theo-best-value");
            } else {
                sectorTheoLabels.get(key).setText("—");
            }
        }
    }

    private boolean isBestSector(ObservableList<LapTime> laps, long sectorMs, int sector) {
        long best = laps.stream()
                .mapToLong(l -> switch (sector) {
                    case 1 -> l.getS1Ms(); case 2 -> l.getS2Ms(); default -> l.getS3Ms();
                })
                .filter(v -> v > 0).min().orElse(Long.MAX_VALUE);
        return sectorMs == best;
    }

    // ── Setup controls ────────────────────────────────────────────────────

    private void buildSetupControls() {
        // GT3 sliders
        absSlider       = buildSlider(0, 11, 6, 1);
        tcSlider        = buildSlider(0, 11, 6, 1);
        brakeBiasSlider = buildSlider(50.0, 65.0, 56.5, 2.5);
        frontSuspSlider = buildSlider(1, 10, 5, 1);
        rearSuspSlider  = buildSlider(1, 10, 5, 1);
        tpFrontSlider   = buildSlider(25.0, 32.0, 27.5, 1.0);
        tpRearSlider    = buildSlider(25.0, 32.0, 27.5, 1.0);
        gt3FuelSlider   = buildSlider(0, 120, 60, 20);

        VBox gt3AdvancedBox = new VBox(6,
                sliderRow("Brake Bias %", brakeBiasSlider),
                sliderRow("Front Suspension", frontSuspSlider),
                sliderRow("Rear Suspension",  rearSuspSlider),
                sliderRow("Tyre Pressure Front", tpFrontSlider),
                sliderRow("Tyre Pressure Rear",  tpRearSlider),
                sliderRow("Fuel Load (L)", gt3FuelSlider));
        gt3AdvancedBox.getStyleClass().add("advanced-panel");
        gt3AdvancedBox.setVisible(false); gt3AdvancedBox.setManaged(false);

        Button gt3AdvBtn = new Button("⊕  Advanced Setup");
        gt3AdvBtn.getStyleClass().add("btn-toggle");
        gt3AdvBtn.setOnAction(e -> {
            boolean show = !gt3AdvancedBox.isVisible();
            gt3AdvancedBox.setVisible(show); gt3AdvancedBox.setManaged(show);
            gt3AdvBtn.setText(show ? "⊖  Advanced Setup" : "⊕  Advanced Setup");
        });

        VBox gt3SetupBox = new VBox(8, sliderRow("ABS Level", absSlider),
                sliderRow("Traction Control", tcSlider), gt3AdvBtn, gt3AdvancedBox);

        // Formula sliders
        fwSlider = buildSlider(0, 15, 10, 5);
        rwSlider = buildSlider(0, 15, 10, 5);

        ersModeDropdown = new ComboBox<>(FXCollections.observableArrayList(
                "0 - None","1 - Low","2 - Medium","3 - High","4 - Overtake"));
        ersModeDropdown.setValue("3 - High"); ersModeDropdown.setPrefWidth(140);

        drsCheckBox = new CheckBox("DRS Enabled");

        diffEntrySlider   = buildSlider(50, 100, 60, 10);
        diffExitSlider    = buildSlider(50, 100, 70, 10);
        formulaFuelSlider = buildSlider(0, 110, 50, 20);

        VBox formulaAdvancedBox = new VBox(6,
                labeledRow("ERS Deploy Mode", ersModeDropdown),
                drsCheckBox,
                sliderRow("Differential Entry %", diffEntrySlider),
                sliderRow("Differential Exit %",  diffExitSlider),
                sliderRow("Fuel Load (kg)", formulaFuelSlider));
        formulaAdvancedBox.getStyleClass().add("advanced-panel");
        formulaAdvancedBox.setVisible(false); formulaAdvancedBox.setManaged(false);

        Button formulaAdvBtn = new Button("⊕  Advanced Setup");
        formulaAdvBtn.getStyleClass().add("btn-toggle");
        formulaAdvBtn.setOnAction(e -> {
            boolean show = !formulaAdvancedBox.isVisible();
            formulaAdvancedBox.setVisible(show); formulaAdvancedBox.setManaged(show);
            formulaAdvBtn.setText(show ? "⊖  Advanced Setup" : "⊕  Advanced Setup");
        });

        VBox formulaSetupBox = new VBox(8, sliderRow("Front Wing Angle", fwSlider),
                sliderRow("Rear Wing Angle", rwSlider), formulaAdvBtn, formulaAdvancedBox);

        dynamicSetupContainer.getChildren().add(gt3SetupBox);

        carTypeDropdown.valueProperty().addListener((obs, oldVal, newVal) -> {
            dynamicSetupContainer.getChildren().clear();
            if (newVal != null && newVal.contains("F1")) dynamicSetupContainer.getChildren().add(formulaSetupBox);
            else dynamicSetupContainer.getChildren().add(gt3SetupBox);
        });
    }

    private void buildCarDropdown() {
        carTypeDropdown.setItems(FXCollections.observableArrayList("Porsche 911 GT3 R", "Ferrari F1-75"));
        carTypeDropdown.setValue("Porsche 911 GT3 R");
    }

    private void buildWeatherDropdown() {
        weatherDropdown.setItems(FXCollections.observableArrayList("DRY","DAMP","WET","COLD","HOT"));
        weatherDropdown.setValue("DRY");
        weatherDropdown.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || empty) { setText(null); setGraphic(null); return; }
                Label pill = new Label(item);
                pill.getStyleClass().addAll("weather-pill", "weather-" + item);
                setGraphic(pill); setText(null);
            }
        });
        weatherDropdown.setButtonCell(new ListCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || empty) { setText(null); setGraphic(null); return; }
                Label pill = new Label(item);
                pill.getStyleClass().addAll("weather-pill", "weather-" + item);
                setGraphic(pill); setText(null);
            }
        });
    }

    private void loadSavedLaps() {
        for (LapTime lap : garageIO.loadLaps()) processNewLap(lap);
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private Vehicle buildCarFromUI() {
        String selected = carTypeDropdown.getValue();
        if (selected != null && selected.contains("F1")) {
            FormulaCar fCar = new FormulaCar("Ferrari", selected, 2022);
            fCar.setFrontWingAngle(fwSlider.getValue());
            fCar.setRearWingAngle(rwSlider.getValue());
            fCar.setErsDeployMode(parseErsMode(ersModeDropdown.getValue()));
            fCar.setDrsEnabled(drsCheckBox.isSelected());
            fCar.setDifferentialEntry((int) Math.round(diffEntrySlider.getValue()));
            fCar.setDifferentialExit((int) Math.round(diffExitSlider.getValue()));
            fCar.setFuelLoad(formulaFuelSlider.getValue());
            return fCar;
        }
        String make = "Porsche 911 GT3 R".equals(selected) ? "Porsche" : "Custom";
        GT3Car gCar = new GT3Car(make, selected, 2023);
        gCar.setAbs((int) Math.round(absSlider.getValue()));
        gCar.setTractionControl((int) Math.round(tcSlider.getValue()));
        gCar.setBrakeBias(brakeBiasSlider.getValue());
        gCar.setFrontSuspension((int) Math.round(frontSuspSlider.getValue()));
        gCar.setRearSuspension((int) Math.round(rearSuspSlider.getValue()));
        gCar.setTirePressureFront(tpFrontSlider.getValue());
        gCar.setTirePressureRear(tpRearSlider.getValue());
        gCar.setFuelLoad(gt3FuelSlider.getValue());
        return gCar;
    }

    private int parseErsMode(String label) {
        if (label == null || label.isEmpty()) return 3;
        try { return Integer.parseInt(label.substring(0, 1)); } catch (NumberFormatException e) { return 3; }
    }

    private Slider buildSlider(double min, double max, double def, double tick) {
        Slider s = new Slider(min, max, def);
        s.setShowTickMarks(true); s.setShowTickLabels(true); s.setMajorTickUnit(tick);
        HBox.setHgrow(s, Priority.ALWAYS);
        return s;
    }

    private HBox sliderRow(String labelText, Slider slider) {
        Label lbl = new Label(labelText); lbl.setPrefWidth(140);
        HBox row = new HBox(10, lbl, slider); row.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(slider, Priority.ALWAYS);
        return row;
    }

    private HBox labeledRow(String labelText, Control control) {
        Label lbl = new Label(labelText); lbl.setPrefWidth(140);
        HBox row = new HBox(10, lbl, control); row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private void showColumnEditor(List<String> allSetupHeaders, List<String> exportSelectedCols,
                                  TableView<LapTime> table, List<TableColumn<LapTime, String>> advancedCols) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Export Columns");
        dialog.setHeaderText("Select which setup columns to include in exports.");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.OK);
        VBox box = new VBox(8); box.setPadding(new Insets(12));
        for (String header : allSetupHeaders) {
            CheckBox cb = new CheckBox(header);
            cb.setSelected(exportSelectedCols.contains(header));
            cb.selectedProperty().addListener((obs, was, is) -> {
                if (is) { if (!exportSelectedCols.contains(header)) exportSelectedCols.add(header); }
                else exportSelectedCols.remove(header);
                table.getColumns().stream()
                        .filter(col -> col.getText().equalsIgnoreCase(header))
                        .forEach(col -> col.setVisible(is));
            });
            box.getChildren().add(cb);
        }
        ScrollPane scroll = new ScrollPane(box); scroll.setFitToWidth(true); scroll.setPrefHeight(260);
        dialog.getDialogPane().setContent(scroll);
        dialog.showAndWait();
    }

    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("DriverSim"); alert.setHeaderText(null); alert.setContentText(message);
        alert.showAndWait();
    }
}
