package org.example.drivesimui.controller.platforms;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import org.example.drivesimui.db.LapRepository;
import org.example.drivesimui.db.SessionRepository;
import org.example.drivesimui.model.RaceSession;
import org.example.drivesimui.service.ACCService;

import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.Executors;

/**
 * Controller for platforms/acc.fxml.
 */
public class ACCController implements Initializable {

    // ── Account section ────────────────────────────────────────────────────
    @FXML private TextField   accDisplayName;

    // ── File import section ────────────────────────────────────────────────
    @FXML private Button      importAccSessionBtn;
    @FXML private Button      importMotecCsvBtn;
    @FXML private ProgressBar accProgress;
    @FXML private Label       accImportStatus;

    // ── Data tables ────────────────────────────────────────────────────────
    @FXML private TableView<RaceSession> accSessionsTable;
    @FXML private TableView<String>      accLapsTable;

    // ── State ──────────────────────────────────────────────────────────────
    private LapRepository lapRepo;
    private SessionRepository sessionRepo;
    private ACCService accService;

    private final ObservableList<RaceSession> sessions = FXCollections.observableArrayList();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        accService = new ACCService();
        buildSessionsTable();
        accProgress.setVisible(false);
    }

    public void setRepositories(LapRepository lapRepo, SessionRepository sessionRepo) {
        this.lapRepo     = lapRepo;
        this.sessionRepo = sessionRepo;
        loadSavedAccount();
        loadSavedSessions();
    }

    // ── FXML handlers ──────────────────────────────────────────────────────

    @FXML
    private void onImportAccSession(ActionEvent e) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Import ACC Session Results");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("ACC Results (*.json)", "*.json"));
        // Default to ACC results folder if it exists
        File defaultDir = new File(System.getProperty("user.home"),
                "Documents/Assetto Corsa Competizione/Results");
        if (defaultDir.exists()) fc.setInitialDirectory(defaultDir);
        File f = fc.showOpenDialog(importAccSessionBtn.getScene().getWindow());
        if (f == null) return;

        accImportStatus.setText("Importing " + f.getName() + "…");
        accProgress.setVisible(true);
        accProgress.setProgress(-1);

        Executors.newSingleThreadExecutor().submit(() -> {
            try {
                List<RaceSession> imported = accService.importSessionJson(f);
                Platform.runLater(() -> {
                    sessions.addAll(imported);
                    accImportStatus.setText("Imported " + imported.size() + " session(s) from " + f.getName());
                    accProgress.setVisible(false);
                    if (sessionRepo != null) imported.forEach(s -> sessionRepo.insert(s));
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    accImportStatus.setText("Import failed: " + ex.getMessage());
                    accProgress.setVisible(false);
                });
            }
        });
    }

    @FXML
    private void onImportAccFolder(ActionEvent e) {
        DirectoryChooser dc = new DirectoryChooser();
        dc.setTitle("Select ACC Results Folder");
        File defaultDir = new File(System.getProperty("user.home"),
                "Documents/Assetto Corsa Competizione/Results");
        if (defaultDir.exists()) dc.setInitialDirectory(defaultDir);
        File dir = dc.showDialog(importAccSessionBtn.getScene().getWindow());
        if (dir == null) return;

        accImportStatus.setText("Scanning " + dir.getName() + "…");
        accProgress.setVisible(true);
        accProgress.setProgress(-1);

        Executors.newSingleThreadExecutor().submit(() -> {
            try {
                List<RaceSession> imported = accService.importFolder(dir);
                Platform.runLater(() -> {
                    sessions.addAll(imported);
                    accImportStatus.setText("Imported " + imported.size() + " session(s) from folder.");
                    accProgress.setVisible(false);
                    if (sessionRepo != null) imported.forEach(s -> sessionRepo.insert(s));
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    accImportStatus.setText("Import failed: " + ex.getMessage());
                    accProgress.setVisible(false);
                });
            }
        });
    }

    @FXML
    private void onImportMotecCsv(ActionEvent e) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Import MoTeC CSV Export");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files (*.csv)", "*.csv"));
        File f = fc.showOpenDialog(importMotecCsvBtn.getScene().getWindow());
        if (f == null) return;

        accImportStatus.setText("Importing " + f.getName() + "…");
        accProgress.setVisible(true);
        accProgress.setProgress(-1);

        Executors.newSingleThreadExecutor().submit(() -> {
            try {
                List<RaceSession> imported = accService.importMotecCsv(f);
                Platform.runLater(() -> {
                    sessions.addAll(imported);
                    accImportStatus.setText("Imported " + imported.size() + " session(s) from " + f.getName());
                    accProgress.setVisible(false);
                    if (sessionRepo != null) imported.forEach(s -> sessionRepo.insert(s));
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    accImportStatus.setText("Import failed: " + ex.getMessage());
                    accProgress.setVisible(false);
                });
            }
        });
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private void loadSavedAccount() {
        if (sessionRepo == null) return;
        String name = sessionRepo.loadPlatformEmail("ACC");
        if (name != null && !name.isEmpty()) accDisplayName.setText(name);
    }

    private void loadSavedSessions() {
        if (sessionRepo == null) return;
        List<RaceSession> saved = sessionRepo.loadByPlatform("ACC");
        sessions.setAll(saved);
    }

    private void buildSessionsTable() {
        accSessionsTable.setItems(sessions);
        accSessionsTable.setPlaceholder(new Label("Import ACC result files to see sessions"));
        accSessionsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<RaceSession, String> dateCol = new TableColumn<>("DATE");
        dateCol.setCellValueFactory(cd -> new SimpleStringProperty(
                cd.getValue().getSessionDate() != null ? cd.getValue().getSessionDate().toString() : "—"));
        dateCol.setPrefWidth(100);

        TableColumn<RaceSession, String> trackCol = new TableColumn<>("TRACK");
        trackCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getTrackName()));
        trackCol.setPrefWidth(160);

        TableColumn<RaceSession, String> carCol = new TableColumn<>("CAR");
        carCol.setCellValueFactory(cd -> new SimpleStringProperty(
                cd.getValue().getCarName() != null ? cd.getValue().getCarName() : "—"));
        carCol.setPrefWidth(130);

        TableColumn<RaceSession, String> typeCol = new TableColumn<>("TYPE");
        typeCol.setCellValueFactory(cd -> new SimpleStringProperty(
                cd.getValue().getSessionType() != null ? cd.getValue().getSessionType() : "—"));
        typeCol.setPrefWidth(80);

        TableColumn<RaceSession, String> posCol = new TableColumn<>("FINISH");
        posCol.setCellValueFactory(cd -> {
            int pos = cd.getValue().getFinishPosition();
            int tot = cd.getValue().getTotalDrivers();
            return new SimpleStringProperty(pos > 0 ? pos + "/" + tot : "—");
        });
        posCol.setPrefWidth(70);

        TableColumn<RaceSession, String> bestCol = new TableColumn<>("BEST LAP");
        bestCol.setCellValueFactory(cd -> {
            long ms = cd.getValue().getBestLapMs();
            return new SimpleStringProperty(ms > 0 ? org.example.drivesimui.util.TimeFormatter.formatMs(ms) : "—");
        });
        bestCol.setPrefWidth(90);

        accSessionsTable.getColumns().addAll(dateCol, trackCol, carCol, typeCol, posCol, bestCol);
    }
}
