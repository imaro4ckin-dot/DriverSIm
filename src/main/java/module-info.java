module org.example.drivesimui {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.net.http;
    requires org.apache.pdfbox;

    opens org.example.drivesimui to javafx.fxml;
    exports org.example.drivesimui;
}