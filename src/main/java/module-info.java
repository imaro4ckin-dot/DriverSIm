module org.example.drivesimui {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.net.http;
    requires java.xml;
    requires org.apache.pdfbox;
    requires org.xerial.sqlitejdbc;
    requires org.json;

    opens org.example.drivesimui                      to javafx.fxml;
    opens org.example.drivesimui.model                to javafx.fxml;
    opens org.example.drivesimui.controller           to javafx.fxml;
    opens org.example.drivesimui.controller.platforms to javafx.fxml;

    exports org.example.drivesimui;
    exports org.example.drivesimui.model;
    exports org.example.drivesimui.db;
    exports org.example.drivesimui.util;
    exports org.example.drivesimui.controller;
    exports org.example.drivesimui.controller.platforms;
    exports org.example.drivesimui.service;
}
