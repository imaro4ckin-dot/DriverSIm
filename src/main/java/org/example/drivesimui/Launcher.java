package org.example.drivesimui; // Make sure this matches your actual package name!

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

public class Launcher extends Application {

    // Backend controller and UI data list
    private GarageManger manager = new GarageManger();
    private ObservableList<LapTime> uiLapData = FXCollections.observableArrayList();

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Sim Racing Garage Manager - Pro Edition");

        // --- 1. THE USP: LIVE TELEMETRY CHART ---
        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("Lap Sequence");

        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Lap Time (Seconds)");
        yAxis.setAutoRanging(true);

        LineChart<String, Number> telemetryChart = new LineChart<>(xAxis, yAxis);
        telemetryChart.setTitle("Session Performance History");
        telemetryChart.setPrefWidth(450);

        XYChart.Series<String, Number> lapSeries = new XYChart.Series<>();
        lapSeries.setName("Lap Time Trend");
        telemetryChart.getData().add(lapSeries);

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

        // Dynamic Dropdown Setup
        ObservableList<String> carOptions = FXCollections.observableArrayList(
                "Porsche 911 GT3 R",
                "Ferrari F1-75"
        );
        ComboBox<String> carTypeDropdown = new ComboBox<>(carOptions);
        carTypeDropdown.setValue("Porsche 911 GT3 R");

        // Mini-form to register a new car
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

        // Main Submit Button
        Button addLapBtn = new Button("Log Lap & Plot Data");
        addLapBtn.setStyle("-fx-background-color: #0078D7; -fx-text-fill: white; -fx-font-weight: bold;");

        addLapBtn.setOnAction(event -> {
            try {
                String track = trackInput.getText();
                long timeMs = Long.parseLong(timeInput.getText());
                String selectedCar = carTypeDropdown.getValue();

                Vehicle car;
                // Object creation logic with fallback for custom cars
                if (selectedCar.equals("Porsche 911 GT3 R")) {
                    car = new GT3Car("Porsche", "911 GT3 R", 2023);
                } else if (selectedCar.equals("Ferrari F1-75")) {
                    car = new FormulaCar("Ferrari", "F1-75", 2022);
                } else {
                    // Fallback: Generates a custom GT3 car using the name the user added
                    car = new GT3Car("Custom", selectedCar, 2024);
                }

                LapTime newLap = new LapTime(track, timeMs, car);

                // Add to backend manager and UI table
                manager.addLap(newLap);
                uiLapData.add(newLap);

                // Update the chart
                double timeInSeconds = timeMs / 1000.0;
                int lapNumber = uiLapData.size();
                lapSeries.getData().add(new XYChart.Data<>("L" + lapNumber, timeInSeconds));

                // Clear input fields for the next entry
                trackInput.clear();
                timeInput.clear();

            } catch (NumberFormatException e) {
                System.out.println("Error: Please enter a valid number for the time.");
            }
        });

        // --- 4. LAYOUT ASSEMBLY ---
        HBox topDataSection = new HBox(20);
        topDataSection.getChildren().addAll(table, telemetryChart);

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