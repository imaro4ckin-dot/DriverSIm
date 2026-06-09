module org.example.drivesimui {
    // These are the only two libraries we actually need!
    requires javafx.controls;
    requires javafx.fxml;

    opens org.example.drivesimui to javafx.fxml;
    exports org.example.drivesimui;
}