package org.example.drivesimui.controller.platforms;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.example.drivesimui.db.DriverStatsRepository;
import org.example.drivesimui.db.LapRepository;
import org.example.drivesimui.db.SessionRepository;
import org.example.drivesimui.model.DriverProfile;
import org.example.drivesimui.model.RaceSession;
import org.example.drivesimui.service.CredentialService;
import org.example.drivesimui.service.IRacingService;
import org.example.drivesimui.util.TimeFormatter;

import java.io.File;
import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.Executors;

/**
 * Controller for platforms/iracing.fxml.
 */
public class IRacingController implements Initializable {

    // ── Account section ────────────────────────────────────────────────────
    @FXML private TextField       iracingEmail;
    @FXML private PasswordField   iracingPassword;
    @FXML private Button          iracingConnectBtn;
    @FXML private Button          iracingSyncBtn;
    @FXML private Label           iracingStatusLabel;
    @FXML private ProgressBar     syncProgress;

    // ── File import section ────────────────────────────────────────────────
    @FXML private Button          importIbtBtn;
    @FXML private Button          importCsvBtn;
    @FXML private Label           importStatusLabel;

    // ── Data tables + stats ────────────────────────────────────────────────
    @FXML private TableView<RaceSession> recentRacesTable;
    @FXML private TableView<String>      lapDataTable;
    @FXML private VBox                   driverStatsBox;

    // ── State ──────────────────────────────────────────────────────────────
    private LapRepository         lapRepo;
    private SessionRepository     sessionRepo;
    private DriverStatsRepository statsRepo;
    private IRacingService        iRacingService;
    private CredentialService     credService;

    private final ObservableList<RaceSession> recentRaces = FXCollections.observableArrayList();

    private static final DateTimeFormatter CHART_DATE_FMT =
            DateTimeFormatter.ofPattern("MMM dd");

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        iRacingService = new IRacingService();
        credService    = new CredentialService();
        buildRacesTable();
        iracingSyncBtn.setDisable(true);
        syncProgress.setVisible(false);
    }

    public void setRepositories(LapRepository lapRepo, SessionRepository sessionRepo,
                                 DriverStatsRepository statsRepo) {
        this.lapRepo    = lapRepo;
        this.sessionRepo = sessionRepo;
        this.statsRepo  = statsRepo;
        loadSavedAccount();
    }

    // ── FXML handlers ──────────────────────────────────────────────────────

    @FXML
    private void onConnect(ActionEvent e) {
        String email = iracingEmail.getText().trim();
        String pw    = iracingPassword.getText();
        if (email.isEmpty() || pw.isEmpty()) {
            setStatus("Enter email and password.", "status-warn"); return;
        }
        setStatus("Connecting…", "status-neutral");
        iracingConnectBtn.setDisable(true);
        syncProgress.setVisible(true);
        syncProgress.setProgress(-1);

        // Copy password to char[] before async work; clear field immediately
        char[] pwChars = pw.toCharArray();
        iracingPassword.clear();

        Executors.newSingleThreadExecutor().submit(() -> {
            try {
                iRacingService.authenticate(email, new String(pwChars));

                // Save credential (zeros pwChars internally)
                try { credService.savePassword("IRACING", pwChars); }
                catch (Exception ce) {
                    System.err.println("[IRacingController] Could not save credential: " + ce.getMessage());
                    Arrays.fill(pwChars, '\0');
                }

                Platform.runLater(() -> {
                    setStatus("Connected as " + email + "  —  credential saved", "status-ok");
                    iracingSyncBtn.setDisable(false);
                    syncProgress.setVisible(false);
                    iracingConnectBtn.setDisable(false);
                    if (sessionRepo != null) sessionRepo.savePlatformAccount("IRACING", email, email, "");
                });
            } catch (Exception ex) {
                Arrays.fill(pwChars, '\0');
                Platform.runLater(() -> {
                    setStatus("Connection failed: " + ex.getMessage(), "status-warn");
                    syncProgress.setVisible(false);
                    iracingConnectBtn.setDisable(false);
                });
            }
        });
    }

    @FXML
    private void onSyncData(ActionEvent e) {
        setStatus("Syncing recent races…", "status-neutral");
        syncProgress.setVisible(true);
        syncProgress.setProgress(-1);
        iracingSyncBtn.setDisable(true);

        Executors.newSingleThreadExecutor().submit(() -> {
            try {
                List<RaceSession> sessions = iRacingService.fetchRecentRaces();
                DriverProfile profile      = iRacingService.fetchDriverProfile();

                Platform.runLater(() -> {
                    recentRaces.setAll(sessions);
                    renderDriverStats(profile);
                    syncProgress.setProgress(1);
                    setStatus("Synced " + sessions.size() + " sessions.", "status-ok");
                    iracingSyncBtn.setDisable(false);
                    syncProgress.setVisible(false);
                    if (sessionRepo != null) sessions.forEach(s -> sessionRepo.insert(s));
                    if (statsRepo != null) {
                        statsRepo.upsert(profile);
                        statsRepo.insertIRatingSnapshot("IRACING", profile.getIrating(),
                                LocalDate.now().toString());
                        refreshIRatingChart();
                    }
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    setStatus("Sync failed: " + ex.getMessage(), "status-warn");
                    syncProgress.setVisible(false);
                    iracingSyncBtn.setDisable(false);
                });
            }
        });
    }

    @FXML
    private void onImportIbt(ActionEvent e) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Import iRacing IBT file");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("iRacing Telemetry (*.ibt)", "*.ibt"));
        File f = fc.showOpenDialog(importIbtBtn.getScene().getWindow());
        if (f == null) return;

        importStatusLabel.setText("Importing " + f.getName() + "…");
        Executors.newSingleThreadExecutor().submit(() -> {
            try {
                List<RaceSession> sessions = iRacingService.importIbtFile(f);
                Platform.runLater(() -> {
                    recentRaces.addAll(sessions);
                    importStatusLabel.setText("Imported " + sessions.size() + " session(s) from " + f.getName());
                    if (sessionRepo != null) sessions.forEach(s -> sessionRepo.insert(s));
                });
            } catch (Exception ex) {
                Platform.runLater(() -> importStatusLabel.setText("Import failed: " + ex.getMessage()));
            }
        });
    }

    @FXML
    private void onImportCsv(ActionEvent e) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Import iRacing CSV Export");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files (*.csv)", "*.csv"));
        File f = fc.showOpenDialog(importCsvBtn.getScene().getWindow());
        if (f == null) return;

        importStatusLabel.setText("Importing " + f.getName() + "…");
        Executors.newSingleThreadExecutor().submit(() -> {
            try {
                List<RaceSession> sessions = iRacingService.importCsvFile(f);
                Platform.runLater(() -> {
                    recentRaces.addAll(sessions);
                    importStatusLabel.setText("Imported " + sessions.size() + " session(s) from " + f.getName());
                    if (sessionRepo != null) sessions.forEach(s -> sessionRepo.insert(s));
                });
            } catch (Exception ex) {
                Platform.runLater(() -> importStatusLabel.setText("Import failed: " + ex.getMessage()));
            }
        });
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private void loadSavedAccount() {
        // Pre-fill email from DB
        if (sessionRepo != null) {
            String savedEmail = sessionRepo.loadPlatformEmail("IRACING");
            if (savedEmail != null && !savedEmail.isEmpty()) {
                iracingEmail.setText(savedEmail);
            }
        }

        // Pre-fill password from encrypted credential file
        if (credService.hasSavedCredential("IRACING")) {
            try {
                char[] saved = credService.loadPassword("IRACING");
                iracingPassword.setText(new String(saved));
                Arrays.fill(saved, '\0');
                setStatus("Saved credential loaded — click Connect.", "status-neutral");
            } catch (Exception ex) {
                setStatus("Could not load saved credential: " + ex.getMessage(), "status-warn");
            }
        }

        // Load last-known driver stats from DB
        if (statsRepo != null) {
            DriverProfile last = statsRepo.findLatestByPlatform("IRACING");
            if (last != null) renderDriverStats(last);
        }
    }

    // ── Driver stats + iRating chart ──────────────────────────────────────

    private void renderDriverStats(DriverProfile p) {
        if (p == null) return;
        driverStatsBox.getChildren().clear();

        // Stat labels
        Label title = new Label("DRIVER STATS");
        title.getStyleClass().add("section-title");
        driverStatsBox.getChildren().add(title);

        driverStatsBox.getChildren().addAll(
                statLabel("Driver",        p.getDriverName()),
                statLabel("iRating",       String.valueOf(p.getIrating())),
                statLabel("Safety Rating", String.format("%.2f  %s", p.getSafetyRating(), p.getLicenseClass())),
                statLabel("Total Races",   String.valueOf(p.getTotalRaces())),
                statLabel("Wins",          String.valueOf(p.getWins())),
                statLabel("Podiums",       String.valueOf(p.getPodiums())),
                statLabel("Avg Finish",    String.format("%.1f", p.getAvgFinishPos()))
        );

        // iRating trend chart (populated from DB history)
        refreshIRatingChart();
    }

    private void refreshIRatingChart() {
        if (statsRepo == null) return;
        List<long[]> history = statsRepo.loadIRatingHistory("IRACING");
        if (history.size() < 2) return;  // need at least 2 points for a trend

        // Remove any existing chart (last child if it's a LineChart)
        if (!driverStatsBox.getChildren().isEmpty() &&
                driverStatsBox.getChildren().get(driverStatsBox.getChildren().size() - 1)
                        instanceof LineChart<?,?>) {
            driverStatsBox.getChildren().remove(driverStatsBox.getChildren().size() - 1);
        }

        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("Date");

        long minIr = history.stream().mapToLong(r -> r[1]).min().orElse(1000);
        long maxIr = history.stream().mapToLong(r -> r[1]).max().orElse(2000);
        NumberAxis yAxis = new NumberAxis(minIr - 100, maxIr + 100, 100);
        yAxis.setLabel("iRating");
        yAxis.setAutoRanging(false);

        LineChart<String, Number> chart = new LineChart<>(xAxis, yAxis);
        chart.setTitle("iRATING TREND");
        chart.setLegendVisible(false);
        chart.setAnimated(false);
        chart.setCreateSymbols(true);
        chart.setPrefHeight(220);
        chart.setPrefWidth(Double.MAX_VALUE);
        VBox.setMargin(chart, new Insets(12, 0, 0, 0));

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        for (long[] point : history) {
            String dateLabel;
            try {
                dateLabel = LocalDate.ofEpochDay(point[0]).format(CHART_DATE_FMT);
            } catch (Exception ex) {
                dateLabel = String.valueOf(point[0]);
            }
            series.getData().add(new XYChart.Data<>(dateLabel, point[1]));
        }
        chart.getData().add(series);

        // Rotate x-axis labels to avoid overlap
        xAxis.setTickLabelRotation(-45);

        driverStatsBox.getChildren().add(chart);
    }

    private void buildRacesTable() {
        recentRacesTable.setItems(recentRaces);
        recentRacesTable.setPlaceholder(new Label("Connect and sync, or import a file to see races"));
        recentRacesTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

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
            return new SimpleStringProperty(ms > 0 ? TimeFormatter.formatMs(ms) : "—");
        });
        bestCol.setPrefWidth(90);

        recentRacesTable.getColumns().addAll(dateCol, trackCol, carCol, typeCol, posCol, bestCol);
    }

    private Label statLabel(String key, String value) {
        Label l = new Label(key + ":  " + value);
        l.getStyleClass().add("driver-stat-label");
        return l;
    }

    private void setStatus(String msg, String styleClass) {
        iracingStatusLabel.setText(msg);
        iracingStatusLabel.getStyleClass().setAll("platform-status", styleClass);
    }
}
