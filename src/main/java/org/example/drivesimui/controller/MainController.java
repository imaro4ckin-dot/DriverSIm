package org.example.drivesimui.controller;

import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.TabPane;
import org.example.drivesimui.LapTime;
import org.example.drivesimui.controller.platforms.PlatformsController;
import org.example.drivesimui.db.LapRepository;
import org.example.drivesimui.db.SessionRepository;

import java.net.URL;
import java.util.ResourceBundle;

/**
 * Root controller for main.fxml.
 * Owns the KPI header bar and coordinates all sub-controllers via
 * the fx:include nested-controller pattern.
 *
 * JavaFX automatically injects e.g. {@code garageController} when the
 * corresponding include has {@code fx:id="garage"}.
 */
public class MainController implements Initializable {

    // ── KPI bar labels (declared in main.fxml) ────────────────────────────
    @FXML private Label kpiBestValue;
    @FXML private Label kpiLastValue;
    @FXML private Label kpiTotalValue;
    @FXML private Label kpiSessionValue;
    @FXML private TabPane mainTabPane;

    // ── Sub-controllers (injected by FXML via fx:include fx:id="...") ─────
    @FXML private GarageController        garageController;
    @FXML private DashboardController     dashboardController;
    @FXML private LapComparisonController lapComparisonController;
    @FXML private FuelStrategyController  fuelStrategyController;
    @FXML private PlatformsController     platformsController;

    private final LapRepository     lapRepo     = new LapRepository();
    private final SessionRepository sessionRepo = new SessionRepository();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        // Wire cross-controller dependencies
        garageController.setMainController(this);

        // Share the garage's lap data map with dashboard, comparison, and fuel
        dashboardController.setLapDataMap(garageController.getLapDataMap());
        lapComparisonController.setLapDataMap(garageController.getLapDataMap());
        fuelStrategyController.setLapDataMap(garageController.getLapDataMap());

        // Wire repositories into garage and platforms
        garageController.setRepositories(lapRepo, sessionRepo);
        if (platformsController != null) platformsController.setRepositories(lapRepo, sessionRepo);

        // Stopwatch callback: when timer stops, fill the time input in the garage form
        dashboardController.setOnTimerStopped(() -> {
            long ms = dashboardController.getElapsedMs();
            if (ms > 0) garageController.fillTimeFromStopwatch(ms);
        });
    }

    /**
     * Called by {@link GarageController} every time a new lap is logged.
     * Propagates the event to dashboard, comparison, and fuel controllers,
     * and refreshes the global KPI bar.
     */
    public void onLapLogged(LapTime lap, String sessionKey) {
        updateKpis(sessionKey);
        dashboardController.onNewLap(lap, sessionKey);
        lapComparisonController.onNewLap(lap);
        fuelStrategyController.onNewLap(lap);
    }

    // ── KPI bar ────────────────────────────────────────────────────────────

    private void updateKpis(String key) {
        ObservableList<LapTime> laps = garageController.getLapDataMap().get(key);
        if (laps == null || laps.isEmpty()) return;

        long best = laps.stream().mapToLong(LapTime::getTimeInMilliseconds).min().orElse(0);
        LapTime last  = laps.get(laps.size() - 1);
        int total = garageController.getLapDataMap().values().stream()
                .mapToInt(ObservableList::size).sum();

        kpiBestValue.setText(formatMs(best));
        kpiLastValue.setText(last.getFormatedTime());
        kpiTotalValue.setText(String.valueOf(total));
        kpiSessionValue.setText(key.replace("|", " · "));
    }

    private String formatMs(long ms) {
        long minutes = (ms / 1000) / 60;
        long seconds = (ms / 1000) % 60;
        long millis  = ms % 1000;
        return String.format("%d:%02d.%03d", minutes, seconds, millis);
    }
}
