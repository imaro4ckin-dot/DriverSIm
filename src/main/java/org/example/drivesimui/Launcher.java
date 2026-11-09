package org.example.drivesimui;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.util.Objects;

/**
 * Application entry point.
 * Loads main.fxml, which wires all sub-controllers via the fx:include pattern.
 */
public class Launcher extends Application {

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(
                Objects.requireNonNull(
                        getClass().getResource("/org/example/drivesimui/fxml/main.fxml"),
                        "main.fxml not found on classpath"));

        Parent root = loader.load();

        Scene scene = new Scene(root, 1280, 820);
        scene.getStylesheets().add(
                Objects.requireNonNull(
                        getClass().getResource("/org/example/drivesimui/dark-theme.css"),
                        "dark-theme.css not found").toExternalForm());

        primaryStage.setTitle("DriverSim");
        primaryStage.setMinWidth(1100);
        primaryStage.setMinHeight(720);
        primaryStage.setScene(scene);
        primaryStage.show();
    }
}
