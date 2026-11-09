package org.example.drivesimui.controller.platforms;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import org.example.drivesimui.db.LapRepository;
import org.example.drivesimui.db.SessionRepository;
import org.example.drivesimui.model.RaceSession;
import org.example.drivesimui.service.LMUService;

import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.Executors;

/**
 * Controller for platforms/lmu.fxml.
 */
public class LMUController implements Initializable {

    // ── Account / display section ──────────────────────────────────────────
    @FXML private TextField   lmuDisplayName;

    // ── Live API section ───────────────────────────────────────────────────
    @FXML private TextField   lmuApiUrl;
    @FXML private Button      fetchLiveDataBtn;
    @FXML private Label       lmuApiStatus;

    // ── File import section ────────────────────────────────────────────────
    @FXML private Button      importLmuResultsBtn;
    @FXML private ProgressBar lmuProgress;
    @FXML private Label       lmuImportStatus;

    // ── Data tables ────────────────────────────────────────────────────────
    @FXML private TableView<RaceSession> lmuSessionsTable;
    @FXML private TableView<String>      lmuLapsTable;

    // ── State ──────────────────────────────────────────────────────────────
    private LapRepository     lapRepo;
    private SessionRepository sessionRepo;
    private LMUService        lmuService;

    private final ObservableList<RaceSession> sessions = FXCollections.observableArrayList();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        lmuService = new LMUService();
        buildSessionsTable();
        lmuProgress.setVisible(false);
        lmuApiUrl.setText("http://localhost:5397");
    }

    public void setRepositories(LapRepository lapRepo, SessionRepository sessionRepo) {
        this.lapRepo     = lapRepo;
        this.sessionRepo = sessionRepo;
        loadSavedAccount();
        loadSavedSessions();
    }

    // ── FXML handlers ──────────────────────────────────────────────────────

    @FXML
    private void onFetchLiveData(ActionEvent e) {
        String baseUrl = lmuApiUrl.getText().trim();
        if (baseUrl.isEmpty()) { lmuApiStatus.setText("Enter API URL first."); return; }

        lmuApiStatus.setText("Connecting to " + baseUrl + "…");
        lmuProgress.setVisible(true);
        lmuProgress.setProgress(-1);

        Executors.newSingleThreadExecutor().submit(() -> {
            try {
                List<RaceSession> fetched = lmuService.fetchLiveSession(baseUrl);
                Platform.runLater(() -> {
                    sessions.addAll(fetched);
                    lmuApiStatus.setText("Fetched " + fetched.size() + " session(s) from live API.");
                    lmuProgress.setVisible(false);
                    if (sessionRepo != null) fetched.forEach(s -> sessionRepo.insert(s));
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    lmuApiStatus.setText("Failed: " + ex.getMessage() + " — is LMU running?");
                    lmuProgress.setVisible(false);
                });
            }
        });
    }

    @FXML
    private void onImportLmuResults(ActionEvent e) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Import LMU / rF2 Result Files");
        fc.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Result files (*.xml, *.json)", "*.xml", "*.json"),
                new FileChooser.ExtensionFilter("XML files (*.xml)", "*.xml"),
                new FileChooser.ExtensionFilter("JSON files (*.json)", "*.json"));

        File defaultDir = new File(System.getProperty("user.home"),
                "Documents/Le Mans Ultimate/UserData/player/Results");
        if (!defaultDir.exists()) defaultDir = new File(System.getProperty("user.home"),
                "Documents/rFactor2/UserData/player/Results");
        if (defaultDir.exists()) fc.setInitialDirectory(defaultDir);

        List<File> files = fc.showOpenMultipleDialog(importLmuResultsBtn.getScene().getWindow());
        if (files == null || files.isEmpty()) return;

        lmuImportStatus.setText("Importing " + files.size() + " file(s)…");
        lmuProgress.setVisible(true);
        lmuProgress.setProgress(-1);

        Executors.newSingleThreadExecutor().submit(() -> {
            try {
                List<RaceSession> imported = lmuService.importResultFiles(files);
                Platform.runLater(() -> {
                    sessions.addAll(imported);
                    lmuImportStatus.setText("Imported " + imported.size() + " session(s).");
                    lmuProgress.setVisible(false);
                    if (sessionRepo != null) imported.forEach(s -> sessionRepo.insert(s));
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    lmuImportStatus.setText("Import failed: " + ex.getMessage());
                    lmuProgress.setVisible(false);
                });
            }
        });
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private void loadSavedAccount() {
        if (sessionRepo == null) return;
        String name = sessionRepo.loadPlatformEmail("LMU");
        if (name != null && !name.isEmpty()) lmuDisplayName.setText(name);
    }

    private void loadSavedSessions() {
        if (sessionRepo == null) return;
        List<RaceSession> saved = sessionRepo.loadByPlatform("LMU");
        sessions.setAll(saved);
    }

    private void buildSessionsTable() {
        lmuSessionsTable.setItems(sessions);
        lmuSessionsTable.setPlaceholder(new Label("Import LMU/rF2 result files or fetch live session"));
        lmuSessionsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

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

        lmuSessionsTable.getColumns().addAll(dateCol, trackCol, carCol, typeCol, posCol, bestCol);
    }
}
