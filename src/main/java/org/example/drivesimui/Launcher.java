package org.example.drivesimui; // Make sure this matches your package name!

import javafx.application.Application;
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
import javafx.stage.Stage;

import java.util.HashMap;
import java.util.Map;

public class Launcher extends Application {

    private GarageManger manager = new GarageManger();
    private ObservableList<LapTime> uiLapData = FXCollections.observableArrayList();

    // Dictionaries to keep track of our data series and lap counts
    private Map<String, XYChart.Series<String, Number>> trackSeriesMap = new HashMap<>();
    private Map<String, Integer> trackLapCounts = new HashMap<>();

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Sim Racing Garage Manager - Pro Edition");

        // --- 1. THE USP: TABBED TELEMETRY CHARTS ---
        // Instead of a single LineChart, we create a TabPane to hold multiple charts
        TabPane chartTabPane = new TabPane();
        chartTabPane.setPrefWidth(450);
        chartTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE); // Prevent users from accidentally closing a chart

        // --- 2. THE DATA TABLE ---
        TableView<LapTime> table = new TableView<>();
        table.setItems(uiLapData);
        table.setPrefWidth(400);

        TableColumn<LapTime, String> trackCol = new TableColumn<>("Track");
        trackCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getTrackName()));
        trackCol.setPrefWidth(120);

        TableColumn<LapTime, String> timeCol = new TableColumn<>("Lap Time");
        timeCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getFormatedTime()));
        timeCol.setPrefWidth(100);

        TableColumn<LapTime, String> carCol = new TableColumn<>("Car Used");
        carCol.setCellValueFactory(cellData -> {
            Vehicle car = cellData.getValue().getCarUsed();
            return new SimpleStringProperty(car.getMake() + " " + car.getModel());
        });
        carCol.setPrefWidth(160);

        table.getColumns().addAll(trackCol, timeCol, carCol);

        // --- 3. THE INPUT FORM ---
        Label formTitle = new Label("Add New Telemetry Data:");
        formTitle.setFont(Font.font("System", FontWeight.BOLD, 14));

        TextField trackInput = new TextField();
        trackInput.setPromptText("Track (e.g., Monza)");

        TextField timeInput = new TextField();
        timeInput.setPromptText("Time in ms (e.g., 84500)");

        ObservableList<String> carOptions = FXCollections.observableArrayList(
                "Porsche 911 GT3 R",
                "Ferrari F1-75"
        );
        ComboBox<String> carTypeDropdown = new ComboBox<>(carOptions);
        carTypeDropdown.setValue("Porsche 911 GT3 R");

        TextField newCarInput = new TextField();
        newCarInput.setPromptText("Add new car...");

        Button addNewCarBtn = new Button("+");
        addNewCarBtn.setOnAction(e -> {
            String newCarName = newCarInput.getText();
            if (!newCarName.trim().isEmpty()) {
                carOptions.add(newCarName);
                carTypeDropdown.setValue(newCarName);
                newCarInput.clear();
            }
        });

        Button addLapBtn = new Button("Log Lap & Plot Data");
        addLapBtn.setStyle("-fx-background-color: #0078D7; -fx-text-fill: white; -fx-font-weight: bold;");

        addLapBtn.setOnAction(event -> {
            try {
                String track = trackInput.getText().trim();
                long timeMs = Long.parseLong(timeInput.getText());
                String selectedCar = carTypeDropdown.getValue();

                Vehicle car;
                if (selectedCar.equals("Porsche 911 GT3 R")) {
                    car = new GT3Car("Porsche", "911 GT3 R", 2023);
                } else if (selectedCar.equals("Ferrari F1-75")) {
                    car = new FormulaCar("Ferrari", "F1-75", 2022);
                } else {
                    car = new GT3Car("Custom", selectedCar, 2024);
                }

                LapTime newLap = new LapTime(track, timeMs, car);

                manager.addLap(newLap);
                uiLapData.add(newLap);

                // --- TABBED CHART LOGIC ---
                double timeInSeconds = timeMs / 1000.0;

                // 1. Check if we already have a graph series for this track
                XYChart.Series<String, Number> trackSeries = trackSeriesMap.get(track);

                // 2. If we don't, we need to build a brand new Tab and Chart for it!
                if (trackSeries == null) {
                    // Create new axes for this specific chart
                    CategoryAxis xAxis = new CategoryAxis();
                    xAxis.setLabel("Lap Sequence");
                    NumberAxis yAxis = new NumberAxis();
                    yAxis.setLabel("Lap Time (Seconds)");
                    yAxis.setAutoRanging(true);

                    // Create the completely separate chart
                    LineChart<String, Number> newTrackChart = new LineChart<>(xAxis, yAxis);
                    newTrackChart.setTitle(track + " Performance History");

                    // Create the data line
                    trackSeries = new XYChart.Series<>();
                    trackSeries.setName(track + " Trend");
                    newTrackChart.getData().add(trackSeries);

                    // Create a new Tab, put the chart inside it, and add it to our TabPane
                    Tab newTab = new Tab(track);
                    newTab.setContent(newTrackChart);
                    chartTabPane.getTabs().add(newTab);

                    // Save the data line to our dictionary so we can find it next time
                    trackSeriesMap.put(track, trackSeries);
                    trackLapCounts.put(track, 0);

                    // Automatically switch the user's view to the newly created tab
                    chartTabPane.getSelectionModel().select(newTab);
                }

                // 3. Figure out what lap number this is for THIS specific track
                int currentLapCount = trackLapCounts.get(track) + 1;
                trackLapCounts.put(track, currentLapCount);

                // 4. Add the data point to the correct tab's graph line
                trackSeries.getData().add(new XYChart.Data<>("Lap " + currentLapCount, timeInSeconds));

                // Clear input fields for the next entry
                trackInput.clear();
                timeInput.clear();

            } catch (NumberFormatException e) {
                System.out.println("Error: Please enter a valid number for the time.");
            }
        });

        // --- 4. LAYOUT ASSEMBLY ---
        HBox topDataSection = new HBox(20);
        // We now place the chartTabPane next to the table instead of a single chart
        topDataSection.getChildren().addAll(table, chartTabPane);

        HBox inputFields = new HBox(10);
        inputFields.getChildren().addAll(trackInput, timeInput, carTypeDropdown, newCarInput, addNewCarBtn, addLapBtn);

        VBox formSection = new VBox(10);
        formSection.getChildren().addAll(formTitle, inputFields);

        VBox rootLayout = new VBox(25);
        rootLayout.setPadding(new Insets(20));
        rootLayout.getChildren().addAll(topDataSection, formSection);

        Scene scene = new Scene(rootLayout, 900, 500);
        primaryStage.setScene(scene);
        primaryStage.show();
    }
}