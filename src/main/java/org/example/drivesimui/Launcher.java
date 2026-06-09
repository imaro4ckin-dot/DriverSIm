package org.example.drivesimui; // Make sure this matches your actual package name!

import javafx.application.Application;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class Launcher extends Application {

    private GarageManger manager = new GarageManger();
    private ObservableList<LapTime> uiLapData = FXCollections.observableArrayList();

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Sim Racing Garage Manager");

        // 1. Create the Table
        TableView<LapTime> table = new TableView<>();
        table.setItems(uiLapData);

        // 2. Create the Columns (and make them wider so text doesn't cut off!)
        TableColumn<LapTime, String> trackCol = new TableColumn<>("Track");
        trackCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getTrackName()));
        trackCol.setPrefWidth(150); // Fixed width!

        TableColumn<LapTime, String> timeCol = new TableColumn<>("Lap Time");
        timeCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getFormatedTime()));
        timeCol.setPrefWidth(100);

        TableColumn<LapTime, String> carCol = new TableColumn<>("Car Used");
        carCol.setCellValueFactory(cellData -> {
            Vehicle car = cellData.getValue().getCarUsed();
            return new SimpleStringProperty(car.getMake() + " " + car.getModel());
        });
        carCol.setPrefWidth(200);

        table.getColumns().addAll(trackCol, timeCol, carCol);

        // 3. Create the Input Form (Text Fields and Dropdowns)
        TextField trackInput = new TextField();
        trackInput.setPromptText("Track Name (e.g., Monza)");

        TextField timeInput = new TextField();
        timeInput.setPromptText("Time in ms (e.g., 84500)");

        // A ComboBox is a dropdown menu
        ComboBox<String> carTypeDropdown = new ComboBox<>();
        carTypeDropdown.getItems().addAll("Porsche 911 GT3 R", "Ferrari F1-75");
        carTypeDropdown.setValue("Porsche 911 GT3 R"); // Default value

        Button addLapBtn = new Button("Add Lap");

        // 4. Handle the Button Click
        addLapBtn.setOnAction(event -> {
            try {
                // Grab the text the user typed
                String track = trackInput.getText();
                long timeMs = Long.parseLong(timeInput.getText()); // Convert text to a number
                String selectedCar = carTypeDropdown.getValue();

                Vehicle car;
                // Basic logic to pick the right car object based on the dropdown
                if (selectedCar.equals("Porsche 911 GT3 R")) {
                    car = new GT3Car("Porsche", "911 GT3 R", 2023);
                } else {
                    car = new FormulaCar("Ferrari", "F1-75", 2022);
                }

                // Create the object and add it to our lists
                LapTime newLap = new LapTime(track, timeMs, car);
                manager.addLap(newLap);
                uiLapData.add(newLap);

                // Clear the input fields for the next entry
                trackInput.clear();
                timeInput.clear();

            } catch (NumberFormatException e) {
                // If they type letters into the time box, Java will panic.
                // This catch block prevents the app from crashing!
                System.out.println("Error: Please enter a valid number for the time.");
            }
        });

        // HBox puts elements side-by-side horizontally
        HBox formLayout = new HBox(10);
        formLayout.getChildren().addAll(trackInput, timeInput, carTypeDropdown, addLapBtn);

        // 5. Set up the Main Layout
        VBox layout = new VBox(15);
        layout.setPadding(new Insets(20));
        layout.getChildren().addAll(table, formLayout); // Put the table on top, form on bottom

        Scene scene = new Scene(layout, 650, 450);
        primaryStage.setScene(scene);
        primaryStage.show();
    }
}