package org.example.drivesimui.controller.platforms;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.TabPane;
import org.example.drivesimui.db.DriverStatsRepository;
import org.example.drivesimui.db.LapRepository;
import org.example.drivesimui.db.SessionRepository;

import java.net.URL;
import java.util.ResourceBundle;

/**
 * Outer controller for platforms/platforms.fxml.
 * Injects shared repositories into the three platform sub-controllers.
 */
public class PlatformsController implements Initializable {

    @FXML private TabPane          platformsTabPane;
    @FXML private IRacingController iracingController;
    @FXML private ACCController     accController;
    @FXML private LMUController     lmuController;

    private LapRepository         lapRepo;
    private SessionRepository     sessionRepo;
    private DriverStatsRepository statsRepo;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        // Repositories are set externally; wire them when available
    }

    public void setRepositories(LapRepository lapRepo, SessionRepository sessionRepo) {
        this.lapRepo     = lapRepo;
        this.sessionRepo = sessionRepo;
        this.statsRepo   = new DriverStatsRepository();

        if (iracingController != null)
            iracingController.setRepositories(lapRepo, sessionRepo, statsRepo);
        if (accController != null)
            accController.setRepositories(lapRepo, sessionRepo);
        if (lmuController != null)
            lmuController.setRepositories(lapRepo, sessionRepo);
    }
}
