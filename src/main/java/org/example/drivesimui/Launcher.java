package org.example.drivesimui;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Launcher extends Application {

    private final GarageManger manager = new GarageManger();

    // Keys are "TrackName|CarType" (e.g. "Monza|GT3")
    private final Map<String, ObservableList<LapTime>>            lapDataMap    = new HashMap<>();
    private final Map<String, XYChart.Series<String, Number>>     seriesMap     = new HashMap<>();
    private final Map<String, Integer>                             lapCountMap   = new HashMap<>();
    private final Map<String, List<TableColumn<LapTime, String>>> advancedColMap = new HashMap<>();

    private final Map<String, TabPane>  trackSubTabPanes = new HashMap<>();
    private final Map<String, Tab>      carTypeTabs      = new HashMap<>();

    // KPI labels updated on every new lap
    private Label kpiBestValue    = new Label("—");
    private Label kpiLastValue    = new Label("—");
    private Label kpiTotalValue   = new Label("0");
    private Label kpiSessionValue = new Label("—");

    // Dashboard controller
    private final DashboardController    dashboard   = new DashboardController();
    private final LapComparisonController comparison = new LapComparisonController();
    private final FuelStrategyController  fuelStrat  = new FuelStrategyController();

    // Sector summary labels per key (updated on every lap)
    private final Map<String, Label> sectorS1Labels   = new HashMap<>();
    private final Map<String, Label> sectorS2Labels   = new HashMap<>();
    private final Map<String, Label> sectorS3Labels   = new HashMap<>();
    private final Map<String, Label> sectorTheoLabels = new HashMap<>();

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("DriverSim — Garage Manager");
        primaryStage.setMinWidth(1100);
        primaryStage.setMinHeight(750);

        // ── 1. KPI HEADER BAR ───────────────────────────────────────
        HBox kpiBar = buildKpiBar();

        // ── 2. MAIN TRACK TAB PANE ──────────────────────────────────
        TabPane trackTabPane = new TabPane();
        trackTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        trackTabPane.getStyleClass().add("main-tab-pane");
        VBox.setVgrow(trackTabPane, Priority.ALWAYS);

        // ── 3. INPUT FIELDS ─────────────────────────────────────────
        Label formTitle = new Label("NEW TELEMETRY ENTRY");
        formTitle.getStyleClass().add("section-title");

        TextField trackInput = new TextField();
        trackInput.setPromptText("Track  (e.g. Monza)");
        trackInput.setPrefWidth(160);

        TextField timeInput = new TextField();
        timeInput.setPromptText("Time  e.g. 1.24.500  or  84500");
        timeInput.setPrefWidth(170);

        // ── Init dashboard (needs timeInput reference for stopwatch) ──
        dashboard.init(lapDataMap, () -> {
            long ms = dashboard.getElapsedMs();
            if (ms > 0) timeInput.setText(String.valueOf(ms));
        });
        comparison.init(lapDataMap);
        fuelStrat.init(lapDataMap);

        ObservableList<String> carOptions = FXCollections.observableArrayList(
                "Porsche 911 GT3 R", "Ferrari F1-75");
        ComboBox<String> carTypeDropdown = new ComboBox<>(carOptions);
        carTypeDropdown.setValue("Porsche 911 GT3 R");
        carTypeDropdown.setPrefWidth(180);

        TextField newCarInput = new TextField();
        newCarInput.setPromptText("Add new car...");
        newCarInput.setPrefWidth(140);

        Button addNewCarBtn = new Button("+");
        addNewCarBtn.getStyleClass().add("btn-add");
        addNewCarBtn.setOnAction(e -> {
            String name = newCarInput.getText().trim();
            if (!name.isEmpty()) {
                carOptions.add(name);
                carTypeDropdown.setValue(name);
                newCarInput.clear();
            }
        });

        // ── 4. GT3 SLIDERS ─────────────────────────────────────────
        Slider absSlider       = buildSlider(0, 11, 6, 1);
        Slider tcSlider        = buildSlider(0, 11, 6, 1);
        Slider brakeBiasSlider = buildSlider(50.0, 65.0, 56.5, 2.5);
        Slider frontSuspSlider = buildSlider(1, 10, 5, 1);
        Slider rearSuspSlider  = buildSlider(1, 10, 5, 1);
        Slider tpFrontSlider   = buildSlider(25.0, 32.0, 27.5, 1.0);
        Slider tpRearSlider    = buildSlider(25.0, 32.0, 27.5, 1.0);
        Slider gt3FuelSlider   = buildSlider(0, 120, 60, 20);

        VBox gt3AdvancedBox = new VBox(6,
                sliderRow("Brake Bias %",        brakeBiasSlider),
                sliderRow("Front Suspension",    frontSuspSlider),
                sliderRow("Rear Suspension",     rearSuspSlider),
                sliderRow("Tyre Pressure Front", tpFrontSlider),
                sliderRow("Tyre Pressure Rear",  tpRearSlider),
                sliderRow("Fuel Load (L)",        gt3FuelSlider));
        gt3AdvancedBox.getStyleClass().add("advanced-panel");
        gt3AdvancedBox.setVisible(false);
        gt3AdvancedBox.setManaged(false);

        Button gt3AdvancedToggle = new Button("⊕  Advanced Setup");
        gt3AdvancedToggle.getStyleClass().add("btn-toggle");
        gt3AdvancedToggle.setOnAction(e -> {
            boolean show = !gt3AdvancedBox.isVisible();
            gt3AdvancedBox.setVisible(show);
            gt3AdvancedBox.setManaged(show);
            gt3AdvancedToggle.setText(show ? "⊖  Advanced Setup" : "⊕  Advanced Setup");
        });

        VBox gt3SetupBox = new VBox(8,
                sliderRow("ABS Level",         absSlider),
                sliderRow("Traction Control",  tcSlider),
                gt3AdvancedToggle, gt3AdvancedBox);

        // ── 5. FORMULA SLIDERS ─────────────────────────────────────
        Slider fwSlider = buildSlider(0, 15, 10, 5);
        Slider rwSlider = buildSlider(0, 15, 10, 5);

        ObservableList<String> ersModeOptions = FXCollections.observableArrayList(
                "0 - None", "1 - Low", "2 - Medium", "3 - High", "4 - Overtake");
        ComboBox<String> ersModeDropdown = new ComboBox<>(ersModeOptions);
        ersModeDropdown.setValue("3 - High");
        ersModeDropdown.setPrefWidth(140);

        CheckBox drsCheckBox = new CheckBox("DRS Enabled");

        Slider diffEntrySlider   = buildSlider(50, 100, 60, 10);
        Slider diffExitSlider    = buildSlider(50, 100, 70, 10);
        Slider formulaFuelSlider = buildSlider(0, 110, 50, 20);

        VBox formulaAdvancedBox = new VBox(6,
                labeledRow("ERS Deploy Mode",      ersModeDropdown),
                drsCheckBox,
                sliderRow("Differential Entry %", diffEntrySlider),
                sliderRow("Differential Exit %",  diffExitSlider),
                sliderRow("Fuel Load (kg)",         formulaFuelSlider));
        formulaAdvancedBox.getStyleClass().add("advanced-panel");
        formulaAdvancedBox.setVisible(false);
        formulaAdvancedBox.setManaged(false);

        Button formulaAdvancedToggle = new Button("⊕  Advanced Setup");
        formulaAdvancedToggle.getStyleClass().add("btn-toggle");
        formulaAdvancedToggle.setOnAction(e -> {
            boolean show = !formulaAdvancedBox.isVisible();
            formulaAdvancedBox.setVisible(show);
            formulaAdvancedBox.setManaged(show);
            formulaAdvancedToggle.setText(show ? "⊖  Advanced Setup" : "⊕  Advanced Setup");
        });

        VBox formulaSetupBox = new VBox(8,
                sliderRow("Front Wing Angle", fwSlider),
                sliderRow("Rear Wing Angle",  rwSlider),
                formulaAdvancedToggle, formulaAdvancedBox);

        // ── 6. DYNAMIC SETUP CONTAINER ─────────────────────────────
        VBox dynamicSetupContainer = new VBox();
        dynamicSetupContainer.getStyleClass().add("setup-panel");
        dynamicSetupContainer.getChildren().add(gt3SetupBox);

        carTypeDropdown.valueProperty().addListener((obs, oldVal, newVal) -> {
            dynamicSetupContainer.getChildren().clear();
            if (newVal != null && newVal.contains("F1")) {
                dynamicSetupContainer.getChildren().add(formulaSetupBox);
            } else {
                dynamicSetupContainer.getChildren().add(gt3SetupBox);
            }
        });

        // ── 7. LOG LAP BUTTON ──────────────────────────────────────
        // Sector time inputs
        TextField s1Input = new TextField();
        s1Input.setPromptText("S1  e.g. 0.28.100");
        s1Input.setPrefWidth(140);

        TextField s2Input = new TextField();
        s2Input.setPromptText("S2  e.g. 0.31.200");
        s2Input.setPrefWidth(140);

        TextField s3Input = new TextField();
        s3Input.setPromptText("S3  e.g. 0.25.000");
        s3Input.setPrefWidth(140);

        // Weather dropdown
        ObservableList<String> weatherOptions = FXCollections.observableArrayList(
                "DRY", "DAMP", "WET", "COLD", "HOT");
        ComboBox<String> weatherDropdown = new ComboBox<>(weatherOptions);
        weatherDropdown.setValue("DRY");
        weatherDropdown.setPrefWidth(90);
        weatherDropdown.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || empty) { setText(null); setGraphic(null); return; }
                Label pill = new Label(item);
                pill.getStyleClass().addAll("weather-pill", "weather-" + item);
                setGraphic(pill);
                setText(null);
            }
        });
        weatherDropdown.setButtonCell(new ListCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || empty) { setText(null); setGraphic(null); return; }
                Label pill = new Label(item);
                pill.getStyleClass().addAll("weather-pill", "weather-" + item);
                setGraphic(pill);
                setText(null);
            }
        });

        Button addLapBtn = new Button("▶  LOG LAP");
        addLapBtn.getStyleClass().add("btn-primary");
        addLapBtn.setOnAction(event -> {
            try {
                String track = trackInput.getText().trim();
                if (track.isEmpty()) { showAlert("Please enter a track name."); return; }
                long timeMs = parseTimeInput(timeInput.getText().trim());
                Vehicle car = buildCarFromUI(carTypeDropdown.getValue(),
                        absSlider, tcSlider, brakeBiasSlider,
                        frontSuspSlider, rearSuspSlider,
                        tpFrontSlider, tpRearSlider, gt3FuelSlider,
                        fwSlider, rwSlider, ersModeDropdown,
                        drsCheckBox, diffEntrySlider, diffExitSlider, formulaFuelSlider);

                LapTime lap = new LapTime(track, timeMs, car);

                // Parse sector times (optional — blank = not recorded)
                if (!s1Input.getText().isBlank())
                    try { lap.setS1Ms(parseTimeInput(s1Input.getText().trim())); } catch (IllegalArgumentException ignored) {}
                if (!s2Input.getText().isBlank())
                    try { lap.setS2Ms(parseTimeInput(s2Input.getText().trim())); } catch (IllegalArgumentException ignored) {}
                if (!s3Input.getText().isBlank())
                    try { lap.setS3Ms(parseTimeInput(s3Input.getText().trim())); } catch (IllegalArgumentException ignored) {}

                lap.setWeather(LapTime.Weather.valueOf(weatherDropdown.getValue()));

                processNewLap(lap, trackTabPane);
                trackInput.clear();
                timeInput.clear();
                s1Input.clear();
                s2Input.clear();
                s3Input.clear();
            } catch (IllegalArgumentException e) {
                showAlert(e.getMessage());
            }
        });

        // ── 8. FORM ROWS ────────────────────────────────────────────
        HBox inputRow = new HBox(10, trackInput, timeInput, carTypeDropdown, newCarInput, addNewCarBtn, addLapBtn);
        inputRow.setAlignment(Pos.CENTER_LEFT);

        Label sectorLabel = new Label("SECTORS  (optional)");
        sectorLabel.getStyleClass().add("section-badge");
        Label weatherLabel = new Label("WEATHER");
        weatherLabel.getStyleClass().add("section-badge");
        HBox sectorRow = new HBox(10, sectorLabel, s1Input, s2Input, s3Input, weatherLabel, weatherDropdown);
        sectorRow.setAlignment(Pos.CENTER_LEFT);

        VBox formSection = new VBox(10, formTitle, inputRow, sectorRow, dynamicSetupContainer);
        formSection.getStyleClass().add("form-panel");
        formSection.setPadding(new Insets(14, 18, 14, 18));

        // ── 9. AI ENGINEER SECTION ──────────────────────────────────
        Label aiTitle = new Label("⚡  AI RACE ENGINEER");
        aiTitle.getStyleClass().add("ai-title");

        TextArea aiOutputBox = new TextArea();
        aiOutputBox.setPromptText("Select a track tab and click 'Analyze Session' to get setup advice from your AI race engineer...");
        aiOutputBox.setWrapText(true);
        aiOutputBox.setPrefHeight(90);
        aiOutputBox.setEditable(false);

        Button askEngineerBtn = new Button("◉  Analyze Session");
        askEngineerBtn.getStyleClass().add("btn-warning");
        askEngineerBtn.setOnAction(event -> {
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
        });

        HBox aiButtonRow = new HBox(askEngineerBtn);
        aiButtonRow.setAlignment(Pos.CENTER_RIGHT);
        VBox aiSection = new VBox(10, aiTitle, aiOutputBox, aiButtonRow);
        aiSection.getStyleClass().add("ai-panel");
        aiSection.setPadding(new Insets(14, 18, 14, 18));

        // ── 10. ROOT LAYOUT ─────────────────────────────────────────
        VBox bottomSection = new VBox(10, formSection, aiSection);
        bottomSection.setPadding(new Insets(10, 12, 10, 12));

        VBox garageView = new VBox(0, trackTabPane, bottomSection);
        VBox.setVgrow(trackTabPane, Priority.ALWAYS);

        // ── Top-level tab pane: Dashboard | Garage | Comparison | Fuel ─
        Tab dashTab       = new Tab("  DASHBOARD  ",    dashboard.build());
        Tab garageTab     = new Tab("  GARAGE  ",       garageView);
        Tab compTab       = new Tab("  LAP ANALYSIS  ", comparison.build());
        Tab fuelTab       = new Tab("  FUEL STRATEGY  ",fuelStrat.build());
        dashTab.setClosable(false);
        garageTab.setClosable(false);
        compTab.setClosable(false);
        fuelTab.setClosable(false);

        TabPane mainTabPane = new TabPane(dashTab, garageTab, compTab, fuelTab);
        mainTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        VBox.setVgrow(mainTabPane, Priority.ALWAYS);

        VBox rootLayout = new VBox(0, kpiBar, mainTabPane);
        rootLayout.getStyleClass().add("main-container");
        VBox.setVgrow(mainTabPane, Priority.ALWAYS);

        // ── 11. LOAD SAVED DATA ─────────────────────────────────────
        for (LapTime lap : garageIO.loadLaps()) {
            processNewLap(lap, trackTabPane);
        }

        // ── 12. SAVE ON CLOSE ───────────────────────────────────────
        primaryStage.setOnCloseRequest(event -> {
            List<LapTime> all = new ArrayList<>();
            for (ObservableList<LapTime> list : lapDataMap.values()) all.addAll(list);
            garageIO.saveLaps(all);
        });

        Scene scene = new Scene(rootLayout, 1200, 860);
        scene.getStylesheets().add(
                getClass().getResource("dark-theme.css").toExternalForm());
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    // ── KPI BAR ─────────────────────────────────────────────────────
    private HBox buildKpiBar() {
        // App name / logo area
        Label titleLabel = new Label("DRIVER");
        titleLabel.getStyleClass().add("app-title");
        Label titleAccent = new Label("SIM");
        titleAccent.getStyleClass().add("kpi-value-red");
        titleAccent.setStyle("-fx-font-size: 15px; -fx-font-weight: bold;");
        HBox appName = new HBox(0, titleLabel, titleAccent);
        appName.setAlignment(Pos.CENTER_LEFT);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox kpiBar = new HBox(12,
                appName, spacer,
                buildKpiCard("BEST LAP",   kpiBestValue,  true),
                buildKpiCard("LAST LAP",   kpiLastValue,  false),
                buildKpiCard("TOTAL LAPS", kpiTotalValue, false),
                buildKpiCard("SESSION",    kpiSessionValue, false));
        kpiBar.getStyleClass().add("kpi-bar");
        kpiBar.setAlignment(Pos.CENTER_LEFT);
        return kpiBar;
    }

    private VBox buildKpiCard(String labelText, Label valueLabel, boolean accent) {
        Label lbl = new Label(labelText);
        lbl.getStyleClass().add("kpi-label");
        valueLabel.getStyleClass().add(accent ? "kpi-value-red" : "kpi-value");
        VBox card = new VBox(2, lbl, valueLabel);
        card.getStyleClass().add("kpi-card");
        card.setAlignment(Pos.CENTER);
        return card;
    }

    private void updateKpis(String key) {
        ObservableList<LapTime> laps = lapDataMap.get(key);
        if (laps == null || laps.isEmpty()) return;

        long best = laps.stream().mapToLong(LapTime::getTimeInMilliseconds).min().orElse(0);
        LapTime last = laps.get(laps.size() - 1);

        int total = lapDataMap.values().stream().mapToInt(ObservableList::size).sum();

        kpiBestValue.setText(formatMs(best));
        kpiLastValue.setText(last.getFormatedTime());
        kpiTotalValue.setText(String.valueOf(total));
        kpiSessionValue.setText(key.replace("|", " · "));
    }

    private String formatMs(long ms) {
        long minutes = (ms / 1000) / 60;
        long seconds = (ms / 1000) % 60;
        long millis  = ms % 1000;
        return String.format("%d:%02d.%03d", minutes, seconds, millis);
    }

    // ── PROCESS NEW LAP ─────────────────────────────────────────────
    private void processNewLap(LapTime newLap, TabPane trackTabPane) {
        String track   = newLap.getTrackName();
        String carType = newLap.getCarUsed().getCarType();
        String key     = track + "|" + carType;

        manager.addLap(newLap);

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

            // ── TABLE ──────────────────────────────────────────────
            TableView<LapTime> table = new TableView<>();
            table.setItems(trackCarData);
            table.setPrefHeight(260);
            table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

            TableColumn<LapTime, String> lapNumCol = new TableColumn<>("#");
            lapNumCol.setCellValueFactory(cd -> {
                int idx = trackCarData.indexOf(cd.getValue()) + 1;
                return new SimpleStringProperty(String.valueOf(idx));
            });
            lapNumCol.setPrefWidth(36);
            lapNumCol.setStyle("-fx-alignment: CENTER;");

            TableColumn<LapTime, String> timeCol = new TableColumn<>("LAP TIME");
            timeCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getFormatedTime()));
            timeCol.setPrefWidth(95);

            TableColumn<LapTime, String> deltaCol = new TableColumn<>("SPLIT");
            deltaCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getFormattedDelta()));
            deltaCol.setPrefWidth(75);
            deltaCol.setCellFactory(col -> new TableCell<>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    getStyleClass().removeAll("delta-faster", "delta-slower", "delta-neutral");
                    if (item == null || empty) { setText(null); return; }
                    setText(item);
                    if (item.startsWith("+"))                      getStyleClass().add("delta-slower");
                    else if (item.startsWith("-") && item.length() > 1) getStyleClass().add("delta-faster");
                    else                                            getStyleClass().add("delta-neutral");
                }
            });

            TableColumn<LapTime, String> carCol = new TableColumn<>("CAR");
            carCol.setCellValueFactory(cd -> {
                Vehicle v = cd.getValue().getCarUsed();
                return new SimpleStringProperty(v.getMake() + " " + v.getModel());
            });
            carCol.setPrefWidth(160);

            table.getColumns().addAll(lapNumCol, timeCol, deltaCol, carCol);

            // ── Sector columns ──────────────────────────────────────
            TableColumn<LapTime, String> s1Col = new TableColumn<>("S1");
            s1Col.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getFormattedS1()));
            s1Col.setPrefWidth(80);
            s1Col.setCellFactory(col -> new TableCell<>() {
                @Override protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    getStyleClass().removeAll("sector-cell", "sector-cell-best");
                    if (item == null || empty) { setText(null); return; }
                    setText(item);
                    // Highlight if this is the best S1 in the session
                    LapTime lap = getTableView().getItems().get(getIndex());
                    if (lap.getS1Ms() > 0 && isBestSector(trackCarData, lap.getS1Ms(), 1))
                        getStyleClass().add("sector-cell-best");
                    else
                        getStyleClass().add("sector-cell");
                }
            });

            TableColumn<LapTime, String> s2Col = new TableColumn<>("S2");
            s2Col.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getFormattedS2()));
            s2Col.setPrefWidth(80);
            s2Col.setCellFactory(col -> new TableCell<>() {
                @Override protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    getStyleClass().removeAll("sector-cell", "sector-cell-best");
                    if (item == null || empty) { setText(null); return; }
                    setText(item);
                    LapTime lap = getTableView().getItems().get(getIndex());
                    if (lap.getS2Ms() > 0 && isBestSector(trackCarData, lap.getS2Ms(), 2))
                        getStyleClass().add("sector-cell-best");
                    else
                        getStyleClass().add("sector-cell");
                }
            });

            TableColumn<LapTime, String> s3Col = new TableColumn<>("S3");
            s3Col.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getFormattedS3()));
            s3Col.setPrefWidth(80);
            s3Col.setCellFactory(col -> new TableCell<>() {
                @Override protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    getStyleClass().removeAll("sector-cell", "sector-cell-best");
                    if (item == null || empty) { setText(null); return; }
                    setText(item);
                    LapTime lap = getTableView().getItems().get(getIndex());
                    if (lap.getS3Ms() > 0 && isBestSector(trackCarData, lap.getS3Ms(), 3))
                        getStyleClass().add("sector-cell-best");
                    else
                        getStyleClass().add("sector-cell");
                }
            });

            // ── Weather column ──────────────────────────────────────
            TableColumn<LapTime, String> weatherCol = new TableColumn<>("COND");
            weatherCol.setCellValueFactory(cd ->
                    new SimpleStringProperty(cd.getValue().getWeather().name()));
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

            table.getColumns().addAll(s1Col, s2Col, s3Col, weatherCol);

            List<String> headers = newLap.getCarUsed().getColumnHeaders();
            int essential        = newLap.getCarUsed().getEssentialColumnCount();
            List<TableColumn<LapTime, String>> advancedCols = new ArrayList<>();

            for (int i = 0; i < headers.size(); i++) {
                final int idx = i;
                TableColumn<LapTime, String> col = new TableColumn<>(headers.get(idx).toUpperCase());
                col.setCellValueFactory(cd -> {
                    if (!col.isVisible()) return new SimpleStringProperty("");
                    List<String> vals = cd.getValue().getCarUsed().getColumnValues();
                    return new SimpleStringProperty(idx < vals.size() ? vals.get(idx) : "—");
                });
                col.setPrefWidth(75);
                if (i >= essential) {
                    col.setVisible(false);
                    advancedCols.add(col);
                }
                table.getColumns().add(col);
            }
            advancedColMap.put(key, advancedCols);

            ScrollPane tableScroll = new ScrollPane(table);
            tableScroll.setFitToHeight(true);
            tableScroll.setFitToWidth(false);
            tableScroll.setPrefWidth(520);
            tableScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
            tableScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

            // ── EXPORT TOOLBAR ─────────────────────────────────────
            Button showAdvancedBtn = new Button("Show Advanced");
            showAdvancedBtn.setOnAction(e -> {
                boolean show = !advancedCols.isEmpty() && !advancedCols.get(0).isVisible();
                for (TableColumn<LapTime, String> col : advancedCols) col.setVisible(show);
                showAdvancedBtn.setText(show ? "Hide Advanced" : "Show Advanced");
            });

            ComboBox<String> formatBox = new ComboBox<>(
                    FXCollections.observableArrayList("CSV", "PDF"));
            formatBox.setValue("CSV");
            formatBox.setPrefWidth(72);

            List<String> exportSelectedCols = new ArrayList<>(headers);

            Button columnsBtn = new Button("Columns");
            columnsBtn.setOnAction(e -> showColumnEditor(headers, exportSelectedCols, table, advancedCols));

            Button exportBtn = new Button("⬇  Export");
            exportBtn.getStyleClass().add("btn-success");
            exportBtn.setOnAction(e -> {
                ObservableList<LapTime> laps = lapDataMap.get(key);
                if (laps == null || laps.isEmpty()) {
                    showAlert("No laps to export for this session.");
                    return;
                }
                LapExporter.Format fmt = formatBox.getValue().equals("PDF")
                        ? LapExporter.Format.PDF : LapExporter.Format.CSV;
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

            HBox exportBar = new HBox(8, showAdvancedBtn, columnsBtn, formatBox, exportBtn);
            exportBar.getStyleClass().add("export-bar");
            exportBar.setAlignment(Pos.CENTER_LEFT);

            // ── CHART ──────────────────────────────────────────────
            CategoryAxis xAxis = new CategoryAxis();
            xAxis.setLabel("Lap");
            NumberAxis yAxis = new NumberAxis();
            yAxis.setLabel("Time (s)");
            yAxis.setAutoRanging(true);

            LineChart<String, Number> chart = new LineChart<>(xAxis, yAxis);
            chart.setTitle(track + "  ·  " + carType);
            chart.setPrefWidth(440);
            chart.setPrefHeight(290);
            chart.setAnimated(true);
            chart.setCreateSymbols(true);
            chart.setLegendVisible(false);

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

        // Delta against previous lap
        ObservableList<LapTime> list = lapDataMap.get(key);
        if (!list.isEmpty()) {
            newLap.setDeltaMs(newLap.getTimeInMilliseconds() - list.get(list.size() - 1).getTimeInMilliseconds());
        }
        list.add(newLap);

        int lapNum = lapCountMap.get(key) + 1;
        lapCountMap.put(key, lapNum);
        seriesMap.get(key).getData().add(
                new XYChart.Data<>("L" + lapNum, newLap.getTimeInMilliseconds() / 1000.0));

        updateKpis(key);
        updateSectorSummary(key);
        dashboard.onNewLap(newLap, key);
        comparison.onNewLap(newLap);
        fuelStrat.onNewLap(newLap);
    }

    // ── SECTOR SUMMARY BAR ──────────────────────────────────────────
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

        VBox s1Card = new VBox(2, s1Lbl, s1Val);
        VBox s2Card = new VBox(2, s2Lbl, s2Val);
        VBox s3Card = new VBox(2, s3Lbl, s3Val);
        VBox thCard = new VBox(2, thLbl, thVal);
        for (VBox c : List.of(s1Card, s2Card, s3Card, thCard))
            c.getStyleClass().add("sector-summary-card");

        HBox bar = new HBox(8, s1Card, s2Card, s3Card, thCard);
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    /** Returns true if sectorMs is the best recorded value for the given sector (1/2/3) in laps. */
    private boolean isBestSector(ObservableList<LapTime> laps, long sectorMs, int sector) {
        long best = laps.stream()
                .mapToLong(l -> switch (sector) {
                    case 1 -> l.getS1Ms();
                    case 2 -> l.getS2Ms();
                    default -> l.getS3Ms();
                })
                .filter(v -> v > 0)
                .min().orElse(Long.MAX_VALUE);
        return sectorMs == best;
    }

    private void updateSectorSummary(String key) {
        ObservableList<LapTime> laps = lapDataMap.get(key);
        if (laps == null) return;

        long bestS1 = laps.stream().mapToLong(LapTime::getS1Ms).filter(v -> v > 0).min().orElse(0);
        long bestS2 = laps.stream().mapToLong(LapTime::getS2Ms).filter(v -> v > 0).min().orElse(0);
        long bestS3 = laps.stream().mapToLong(LapTime::getS3Ms).filter(v -> v > 0).min().orElse(0);

        if (sectorS1Labels.containsKey(key))
            sectorS1Labels.get(key).setText(bestS1 > 0 ? formatMs(bestS1) : "—");
        if (sectorS2Labels.containsKey(key))
            sectorS2Labels.get(key).setText(bestS2 > 0 ? formatMs(bestS2) : "—");
        if (sectorS3Labels.containsKey(key))
            sectorS3Labels.get(key).setText(bestS3 > 0 ? formatMs(bestS3) : "—");

        if (sectorTheoLabels.containsKey(key)) {
            if (bestS1 > 0 && bestS2 > 0 && bestS3 > 0) {
                long theo = bestS1 + bestS2 + bestS3;
                sectorTheoLabels.get(key).setText(formatMs(theo));
                // Color red if theoretical best is faster than actual best lap
                long actualBest = laps.stream().mapToLong(LapTime::getTimeInMilliseconds).min().orElse(Long.MAX_VALUE);
                sectorTheoLabels.get(key).getStyleClass().removeAll("theo-best-value", "kpi-value-red");
                sectorTheoLabels.get(key).getStyleClass().add(
                        theo < actualBest ? "kpi-value-red" : "theo-best-value");
            } else {
                sectorTheoLabels.get(key).setText("—");
            }
        }
    }

    // ── HELPERS ─────────────────────────────────────────────────────

    private HBox sliderRow(String labelText, Slider slider) {
        Label lbl = new Label(labelText);
        lbl.setPrefWidth(140);
        HBox row = new HBox(10, lbl, slider);
        row.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(slider, Priority.ALWAYS);
        return row;
    }

    private HBox labeledRow(String labelText, Control control) {
        Label lbl = new Label(labelText);
        lbl.setPrefWidth(140);
        HBox row = new HBox(10, lbl, control);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private Vehicle buildCarFromUI(
            String selectedCar,
            Slider absSlider, Slider tcSlider, Slider brakeBiasSlider,
            Slider frontSuspSlider, Slider rearSuspSlider,
            Slider tpFrontSlider, Slider tpRearSlider, Slider gt3FuelSlider,
            Slider fwSlider, Slider rwSlider, ComboBox<String> ersModeDropdown,
            CheckBox drsCheckBox, Slider diffEntrySlider, Slider diffExitSlider,
            Slider formulaFuelSlider) {

        if (selectedCar.contains("F1")) {
            FormulaCar fCar = new FormulaCar("Ferrari", selectedCar, 2022);
            fCar.setFrontWingAngle(fwSlider.getValue());
            fCar.setRearWingAngle(rwSlider.getValue());
            fCar.setErsDeployMode(parseErsMode(ersModeDropdown.getValue()));
            fCar.setDrsEnabled(drsCheckBox.isSelected());
            fCar.setDifferentialEntry((int) Math.round(diffEntrySlider.getValue()));
            fCar.setDifferentialExit((int) Math.round(diffExitSlider.getValue()));
            fCar.setFuelLoad(formulaFuelSlider.getValue());
            return fCar;
        }

        String make = selectedCar.equals("Porsche 911 GT3 R") ? "Porsche" : "Custom";
        GT3Car gCar = new GT3Car(make, selectedCar, 2023);
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

    /**
     * Parses a lap time string into milliseconds.
     * Accepted formats:
     *   m.ss.mmm  →  1.24.500  (dot-separated, 3-digit ms)
     *   m.ss.mm   →  1.24.50   (dot-separated, 2-digit ms treated as centiseconds × 10)
     *   m:ss.mmm  →  1:24.500  (colon-minute, dot-decimal)
     *   m:ss.mm   →  1:24.50
     *   raw ms    →  84500
     */
    private long parseTimeInput(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Please enter a lap time.");
        }

        // Format: m.ss.mmm or m.ss.mm  (two dots)
        if (raw.chars().filter(c -> c == '.').count() == 2) {
            String[] parts = raw.split("\\.", -1);
            try {
                long minutes = Long.parseLong(parts[0]);
                long seconds = Long.parseLong(parts[1]);
                String msStr = parts[2];
                long millis  = msStr.length() == 2
                        ? Long.parseLong(msStr) * 10   // centiseconds → ms
                        : Long.parseLong(msStr);
                if (seconds >= 60) throw new IllegalArgumentException("Seconds must be 0–59.");
                if (millis  >= 1000) throw new IllegalArgumentException("Milliseconds must be 0–999.");
                return (minutes * 60_000L) + (seconds * 1000L) + millis;
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "Invalid format. Use  m.ss.mmm  e.g.  1.24.500");
            }
        }

        // Format: m:ss.mmm or m:ss.mm  (colon then dot)
        if (raw.contains(":") && raw.contains(".")) {
            String[] colonParts = raw.split(":", 2);
            String[] dotParts   = colonParts[1].split("\\.", 2);
            try {
                long minutes = Long.parseLong(colonParts[0]);
                long seconds = Long.parseLong(dotParts[0]);
                String msStr = dotParts[1];
                long millis  = msStr.length() == 2
                        ? Long.parseLong(msStr) * 10
                        : Long.parseLong(msStr);
                if (seconds >= 60) throw new IllegalArgumentException("Seconds must be 0–59.");
                if (millis  >= 1000) throw new IllegalArgumentException("Milliseconds must be 0–999.");
                return (minutes * 60_000L) + (seconds * 1000L) + millis;
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "Invalid format. Use  m:ss.mmm  e.g.  1:24.500");
            }
        }

        // Fallback: raw milliseconds integer
        try {
            long ms = Long.parseLong(raw);
            if (ms <= 0) throw new IllegalArgumentException("Lap time must be positive.");
            return ms;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Invalid time.\nAccepted formats:\n  1.24.500  or  1:24.500  or  84500 (ms)");
        }
    }

    private int parseErsMode(String label) {
        if (label == null || label.isEmpty()) return 3;
        try { return Integer.parseInt(label.substring(0, 1)); }
        catch (NumberFormatException e) { return 3; }
    }

    private Slider buildSlider(double min, double max, double def, double tick) {
        Slider s = new Slider(min, max, def);
        s.setShowTickMarks(true);
        s.setShowTickLabels(true);
        s.setMajorTickUnit(tick);
        HBox.setHgrow(s, Priority.ALWAYS);
        return s;
    }

    private void showColumnEditor(List<String> allSetupHeaders,
                                  List<String> exportSelectedCols,
                                  TableView<LapTime> table,
                                  List<TableColumn<LapTime, String>> advancedCols) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Export Columns");
        dialog.setHeaderText("Select which setup columns to include in exports.\nChanges are also reflected in the table.");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.OK);

        VBox box = new VBox(8);
        box.setPadding(new Insets(12));

        for (String header : allSetupHeaders) {
            CheckBox cb = new CheckBox(header);
            cb.setSelected(exportSelectedCols.contains(header));
            cb.selectedProperty().addListener((obs, wasSelected, isSelected) -> {
                if (isSelected) {
                    if (!exportSelectedCols.contains(header)) exportSelectedCols.add(header);
                } else {
                    exportSelectedCols.remove(header);
                }
                for (TableColumn<LapTime, ?> col : table.getColumns()) {
                    if (col.getText().equalsIgnoreCase(header)) col.setVisible(isSelected);
                }
            });
            box.getChildren().add(cb);
        }

        ScrollPane scroll = new ScrollPane(box);
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(260);
        dialog.getDialogPane().setContent(scroll);
        dialog.showAndWait();
    }

    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("DriverSim");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
