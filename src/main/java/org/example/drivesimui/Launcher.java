package org.example.drivesimui;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
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

    // Track which track tabs and car-type sub-tabs already exist
    private final Map<String, TabPane>  trackSubTabPanes = new HashMap<>();  // key = track name
    private final Map<String, Tab>      carTypeTabs      = new HashMap<>();  // key = "TrackName|CarType"

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Sim Racing Garage Manager - Pro Edition");

        // --- 1. MAIN TRACK TAB PANE ---
        TabPane trackTabPane = new TabPane();
        trackTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        trackTabPane.setPrefHeight(340);

        // --- 2. INPUT FIELDS ---
        Label formTitle = new Label("Add New Telemetry Data:");
        formTitle.setFont(Font.font("System", FontWeight.BOLD, 14));

        TextField trackInput = new TextField();
        trackInput.setPromptText("Track (e.g., Monza)");

        TextField timeInput = new TextField();
        timeInput.setPromptText("Time in ms (e.g., 84500)");

        ObservableList<String> carOptions = FXCollections.observableArrayList(
                "Porsche 911 GT3 R", "Ferrari F1-75");
        ComboBox<String> carTypeDropdown = new ComboBox<>(carOptions);
        carTypeDropdown.setValue("Porsche 911 GT3 R");

        TextField newCarInput = new TextField();
        newCarInput.setPromptText("Add new car...");
        Button addNewCarBtn = new Button("+");
        addNewCarBtn.setOnAction(e -> {
            String name = newCarInput.getText().trim();
            if (!name.isEmpty()) {
                carOptions.add(name);
                carTypeDropdown.setValue(name);
                newCarInput.clear();
            }
        });

        // --- 3. GT3 ESSENTIAL SLIDERS ---
        Slider absSlider       = buildSlider(0, 11, 6, 1);
        Slider tcSlider        = buildSlider(0, 11, 6, 1);

        // --- 4. GT3 ADVANCED SLIDERS ---
        Slider brakeBiasSlider = buildSlider(50.0, 65.0, 56.5, 2.5);
        Slider frontSuspSlider = buildSlider(1, 10, 5, 1);
        Slider rearSuspSlider  = buildSlider(1, 10, 5, 1);
        Slider tpFrontSlider   = buildSlider(25.0, 32.0, 27.5, 1.0);
        Slider tpRearSlider    = buildSlider(25.0, 32.0, 27.5, 1.0);
        Slider gt3FuelSlider   = buildSlider(0, 120, 60, 20);

        VBox gt3AdvancedBox = new VBox(4,
                new Label("Brake Bias %:"),        brakeBiasSlider,
                new Label("Front Suspension:"),    frontSuspSlider,
                new Label("Rear Suspension:"),     rearSuspSlider,
                new Label("Tyre Pressure Front:"), tpFrontSlider,
                new Label("Tyre Pressure Rear:"),  tpRearSlider,
                new Label("Fuel Load (L):"),       gt3FuelSlider);
        gt3AdvancedBox.setVisible(false);
        gt3AdvancedBox.setManaged(false);

        Button gt3AdvancedToggle = new Button("+ Advanced Setup");
        gt3AdvancedToggle.setOnAction(e -> {
            boolean show = !gt3AdvancedBox.isVisible();
            gt3AdvancedBox.setVisible(show);
            gt3AdvancedBox.setManaged(show);
            gt3AdvancedToggle.setText(show ? "- Advanced Setup" : "+ Advanced Setup");
        });

        VBox gt3SetupBox = new VBox(6,
                new Label("ABS Level:"), absSlider,
                new Label("Traction Control:"), tcSlider,
                gt3AdvancedToggle, gt3AdvancedBox);

        // --- 5. FORMULA ESSENTIAL SLIDERS ---
        Slider fwSlider = buildSlider(0, 15, 10, 5);
        Slider rwSlider = buildSlider(0, 15, 10, 5);

        // --- 6. FORMULA ADVANCED CONTROLS ---
        ObservableList<String> ersModeOptions = FXCollections.observableArrayList(
                "0 - None", "1 - Low", "2 - Medium", "3 - High", "4 - Overtake");
        ComboBox<String> ersModeDropdown = new ComboBox<>(ersModeOptions);
        ersModeDropdown.setValue("3 - High");

        CheckBox drsCheckBox = new CheckBox("DRS Enabled");
        drsCheckBox.setSelected(true);

        Slider diffEntrySlider   = buildSlider(50, 100, 60, 10);
        Slider diffExitSlider    = buildSlider(50, 100, 70, 10);
        Slider formulaFuelSlider = buildSlider(0, 110, 50, 20);

        VBox formulaAdvancedBox = new VBox(4,
                new Label("ERS Deploy Mode:"),      ersModeDropdown,
                drsCheckBox,
                new Label("Differential Entry %:"), diffEntrySlider,
                new Label("Differential Exit %:"),  diffExitSlider,
                new Label("Fuel Load (kg):"),        formulaFuelSlider);
        formulaAdvancedBox.setVisible(false);
        formulaAdvancedBox.setManaged(false);

        Button formulaAdvancedToggle = new Button("+ Advanced Setup");
        formulaAdvancedToggle.setOnAction(e -> {
            boolean show = !formulaAdvancedBox.isVisible();
            formulaAdvancedBox.setVisible(show);
            formulaAdvancedBox.setManaged(show);
            formulaAdvancedToggle.setText(show ? "- Advanced Setup" : "+ Advanced Setup");
        });

        VBox formulaSetupBox = new VBox(6,
                new Label("Front Wing Angle:"), fwSlider,
                new Label("Rear Wing Angle:"),  rwSlider,
                formulaAdvancedToggle, formulaAdvancedBox);

        // --- 7. DYNAMIC SETUP CONTAINER ---
        VBox dynamicSetupContainer = new VBox();
        dynamicSetupContainer.getChildren().add(gt3SetupBox);

        carTypeDropdown.valueProperty().addListener((obs, oldVal, newVal) -> {
            dynamicSetupContainer.getChildren().clear();
            if (newVal != null && newVal.contains("F1")) {
                dynamicSetupContainer.getChildren().add(formulaSetupBox);
            } else {
                dynamicSetupContainer.getChildren().add(gt3SetupBox);
            }
        });

        // --- 8. LOG LAP BUTTON ---
        Button addLapBtn = new Button("Log Lap & Plot Data");
        addLapBtn.setStyle("-fx-background-color: #0078D7; -fx-text-fill: white; -fx-font-weight: bold;");
        addLapBtn.setOnAction(event -> {
            try {
                String track = trackInput.getText().trim();
                long timeMs = Long.parseLong(timeInput.getText().trim());
                Vehicle car = buildCarFromUI(carTypeDropdown.getValue(),
                        absSlider, tcSlider, brakeBiasSlider,
                        frontSuspSlider, rearSuspSlider,
                        tpFrontSlider, tpRearSlider, gt3FuelSlider,
                        fwSlider, rwSlider, ersModeDropdown,
                        drsCheckBox, diffEntrySlider, diffExitSlider, formulaFuelSlider);

                processNewLap(new LapTime(track, timeMs, car), trackTabPane);
                trackInput.clear();
                timeInput.clear();
            } catch (NumberFormatException e) {
                System.out.println("Error: Please enter a valid number for the time.");
            }
        });

        // --- 9. AI ENGINEER DASHBOARD ---
        Label aiTitle = new Label("AI Race Engineer Feedback:");
        aiTitle.setFont(Font.font("System", FontWeight.BOLD, 14));

        TextArea aiOutputBox = new TextArea();
        aiOutputBox.setPromptText("Select a track tab and click 'Analyze Session' to get setup advice...");
        aiOutputBox.setWrapText(true);
        aiOutputBox.setPrefHeight(90);
        aiOutputBox.setEditable(false);

        Button askEngineerBtn = new Button("Analyze Session");
        askEngineerBtn.setStyle("-fx-background-color: #ff9900; -fx-text-fill: black; -fx-font-weight: bold;");
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

            aiOutputBox.setText("Engineer reviewing " + activeTrack + " [" + carType + "]... Please wait...");
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
                Platform.runLater(() -> { aiOutputBox.setText(advice); askEngineerBtn.setDisable(false); });
            }).start();
        });

        // --- 10. LAYOUT ---
        HBox inputFields = new HBox(10,
                trackInput, timeInput, carTypeDropdown, newCarInput, addNewCarBtn, addLapBtn);
        VBox formSection = new VBox(8, formTitle, inputFields, dynamicSetupContainer);
        VBox aiSection   = new VBox(8, aiTitle, aiOutputBox, askEngineerBtn);

        VBox rootLayout = new VBox(12);
        rootLayout.setPadding(new Insets(15));
        rootLayout.getChildren().addAll(trackTabPane, formSection, aiSection);

        // --- 11. LOAD SAVED DATA ---
        for (LapTime lap : garageIO.loadLaps()) {
            processNewLap(lap, trackTabPane);
        }

        // --- 12. SAVE ON CLOSE ---
        primaryStage.setOnCloseRequest(event -> {
            List<LapTime> all = new ArrayList<>();
            for (ObservableList<LapTime> list : lapDataMap.values()) all.addAll(list);
            garageIO.saveLaps(all);
        });

        Scene scene = new Scene(rootLayout, 1100, 820);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    // ---------------------------------------------------------------------------
    // Routes a lap to the correct track tab → car-type sub-tab → table + chart.
    // Creates the tab hierarchy on first encounter.
    // ---------------------------------------------------------------------------
    private void processNewLap(LapTime newLap, TabPane trackTabPane) {
        String track   = newLap.getTrackName();
        String carType = newLap.getCarUsed().getCarType();
        String key     = track + "|" + carType;

        manager.addLap(newLap);

        // Create the track-level tab if this is the first lap at this track
        if (!trackSubTabPanes.containsKey(track)) {
            TabPane carTypeTabPane = new TabPane();
            carTypeTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
            trackSubTabPanes.put(track, carTypeTabPane);

            Tab trackTab = new Tab(track, carTypeTabPane);
            trackTabPane.getTabs().add(trackTab);
            trackTabPane.getSelectionModel().select(trackTab);
        }

        // Create the car-type sub-tab if this is the first lap with this car type at this track
        if (!carTypeTabs.containsKey(key)) {
            ObservableList<LapTime> trackCarData = FXCollections.observableArrayList();
            lapDataMap.put(key, trackCarData);

            // --- Table ---
            TableView<LapTime> table = new TableView<>();
            table.setItems(trackCarData);
            table.setPrefHeight(250);

            TableColumn<LapTime, String> timeCol = new TableColumn<>("Lap Time");
            timeCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getFormatedTime()));
            timeCol.setPrefWidth(90);

            TableColumn<LapTime, String> deltaCol = new TableColumn<>("Split");
            deltaCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getFormattedDelta()));
            deltaCol.setPrefWidth(70);
            deltaCol.setCellFactory(col -> new TableCell<>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (item == null || empty) { setText(null); setStyle(""); return; }
                    setText(item);
                    if      (item.startsWith("+"))                       setStyle("-fx-text-fill: #ff3333; -fx-font-weight: bold;");
                    else if (item.startsWith("-") && item.length() > 1)  setStyle("-fx-text-fill: #00cc00; -fx-font-weight: bold;");
                    else                                                  setStyle("-fx-text-fill: gray;");
                }
            });

            TableColumn<LapTime, String> carCol = new TableColumn<>("Car");
            carCol.setCellValueFactory(cd -> {
                Vehicle v = cd.getValue().getCarUsed();
                return new SimpleStringProperty(v.getMake() + " " + v.getModel());
            });
            carCol.setPrefWidth(150);

            table.getColumns().addAll(timeCol, deltaCol, carCol);

            // Car-type-specific setup columns (polymorphic, no casting)
            List<String> headers  = newLap.getCarUsed().getColumnHeaders();
            int essential         = newLap.getCarUsed().getEssentialColumnCount();
            List<TableColumn<LapTime, String>> advancedCols = new ArrayList<>();

            for (int i = 0; i < headers.size(); i++) {
                final int idx = i;
                TableColumn<LapTime, String> col = new TableColumn<>(headers.get(idx));
                col.setCellValueFactory(cd -> {
                    if (!col.isVisible()) return new SimpleStringProperty("");
                    List<String> vals = cd.getValue().getCarUsed().getColumnValues();
                    return new SimpleStringProperty(idx < vals.size() ? vals.get(idx) : "-");
                });
                col.setPrefWidth(75);
                if (i >= essential) {
                    col.setVisible(false);
                    advancedCols.add(col);
                }
                table.getColumns().add(col);
            }
            advancedColMap.put(key, advancedCols);

            // Scrollable table wrapper
            ScrollPane tableScroll = new ScrollPane(table);
            tableScroll.setFitToHeight(true);
            tableScroll.setFitToWidth(false);
            tableScroll.setPrefWidth(500);
            tableScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
            tableScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

            // Toggle advanced columns in table
            Button showAdvancedBtn = new Button("Show Advanced Setup");
            showAdvancedBtn.setOnAction(e -> {
                boolean show = !advancedCols.isEmpty() && !advancedCols.get(0).isVisible();
                for (TableColumn<LapTime, String> col : advancedCols) col.setVisible(show);
                showAdvancedBtn.setText(show ? "Hide Advanced Setup" : "Show Advanced Setup");
            });

            // --- Export format selector ---
            ComboBox<String> formatBox = new ComboBox<>(
                    FXCollections.observableArrayList("CSV", "PDF"));
            formatBox.setValue("CSV");

            // --- Columns... dialog button ---
            // Keeps a mutable list of which setup column names the user wants exported.
            // Starts with all columns selected.
            List<String> exportSelectedCols = new ArrayList<>(headers);

            Button columnsBtn = new Button("Columns...");
            columnsBtn.setOnAction(e -> showColumnEditor(headers, exportSelectedCols, table, advancedCols));

            // --- Export button ---
            Button exportBtn = new Button("Export");
            exportBtn.setStyle("-fx-background-color: #28a745; -fx-text-fill: white; -fx-font-weight: bold;");
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

            HBox exportBar = new HBox(6, showAdvancedBtn, columnsBtn, formatBox, exportBtn);
            exportBar.setPadding(new Insets(0, 0, 4, 0));

            // --- Chart ---
            CategoryAxis xAxis = new CategoryAxis();
            xAxis.setLabel("Lap");
            NumberAxis yAxis = new NumberAxis();
            yAxis.setLabel("Time (s)");
            yAxis.setAutoRanging(true);

            LineChart<String, Number> chart = new LineChart<>(xAxis, yAxis);
            chart.setTitle(track + " — " + carType);
            chart.setPrefWidth(430);
            chart.setPrefHeight(280);

            XYChart.Series<String, Number> series = new XYChart.Series<>();
            series.setName("Lap Trend");
            chart.getData().add(series);
            seriesMap.put(key, series);
            lapCountMap.put(key, 0);

            VBox tableSection = new VBox(4, exportBar, tableScroll);
            HBox content = new HBox(12, tableSection, chart);
            content.setPadding(new Insets(8));

            Tab carTab = new Tab(carType, content);
            trackSubTabPanes.get(track).getTabs().add(carTab);
            trackSubTabPanes.get(track).getSelectionModel().select(carTab);
            carTypeTabs.put(key, carTab);
        }

        // Delta against previous lap of same track + car type
        ObservableList<LapTime> list = lapDataMap.get(key);
        if (!list.isEmpty()) {
            newLap.setDeltaMs(newLap.getTimeInMilliseconds() - list.get(list.size() - 1).getTimeInMilliseconds());
        }
        list.add(newLap);

        int lapNum = lapCountMap.get(key) + 1;
        lapCountMap.put(key, lapNum);
        seriesMap.get(key).getData().add(
                new XYChart.Data<>("L" + lapNum, newLap.getTimeInMilliseconds() / 1000.0));
    }

    // ---------------------------------------------------------------------------
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
        return s;
    }

    // ---------------------------------------------------------------------------
    // Column editor dialog — lets the user pick which setup columns appear in the
    // export AND mirrors that choice to the table's visible columns.
    // ---------------------------------------------------------------------------
    private void showColumnEditor(List<String> allSetupHeaders,
                                  List<String> exportSelectedCols,
                                  TableView<LapTime> table,
                                  List<TableColumn<LapTime, String>> advancedCols) {

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Choose Columns");
        dialog.setHeaderText("Select which setup columns to include in exports.\nChanges are also reflected in the table.");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.OK);

        VBox box = new VBox(6);
        box.setPadding(new Insets(10));

        // One CheckBox per setup column header
        List<CheckBox> checkBoxes = new ArrayList<>();
        for (String header : allSetupHeaders) {
            CheckBox cb = new CheckBox(header);
            cb.setSelected(exportSelectedCols.contains(header));

            cb.selectedProperty().addListener((obs, wasSelected, isSelected) -> {
                if (isSelected) {
                    if (!exportSelectedCols.contains(header)) exportSelectedCols.add(header);
                } else {
                    exportSelectedCols.remove(header);
                }
                // Mirror to table visibility
                for (TableColumn<LapTime, ?> col : table.getColumns()) {
                    if (col.getText().equals(header)) {
                        col.setVisible(isSelected);
                    }
                }
            });

            checkBoxes.add(cb);
            box.getChildren().add(cb);
        }

        ScrollPane scroll = new ScrollPane(box);
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(250);
        dialog.getDialogPane().setContent(scroll);
        dialog.showAndWait();
    }

    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Export");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
