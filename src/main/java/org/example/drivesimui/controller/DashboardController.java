package org.example.drivesimui.controller;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;
import org.example.drivesimui.LapTime;
import org.example.drivesimui.Vehicle;
import org.example.drivesimui.util.TimeFormatter;

import java.net.URL;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Controller for dashboard.fxml — live timing dashboard.
 */
public class DashboardController implements Initializable {

    // ── FXML fields ────────────────────────────────────────────────────────
    @FXML private Label      bestLapValue;
    @FXML private Label      lastLapValue;
    @FXML private Label      avgLapValue;
    @FXML private Label      consistencyValue;
    @FXML private Label      totalLapsValue;
    @FXML private Label      trackTimeValue;
    @FXML private Label      gapToBestValue;
    @FXML private Label      trackStateLabel;
    @FXML private Label      timerDisplay;
    @FXML private Button     startStopBtn;
    @FXML private Button     resetBtn;
    @FXML private TableView<LapTime> dashTable;
    @FXML private VBox       eventsBox;
    @FXML private Label      clockLabel;
    // Stat panel labels (duplicates of the card values — wired separately in FXML)
    @FXML private Label      statBestLapValue;
    @FXML private Label      statAvgValue;
    @FXML private Label      statConsistencyValue;

    // ── Stopwatch state ────────────────────────────────────────────────────
    private long    stopwatchStartNano = 0;
    private long    stopwatchAccumMs   = 0;
    private boolean timerRunning       = false;
    private Timeline stopwatchTimeline;
    private Runnable onTimerStopped;

    // ── Dashboard data ─────────────────────────────────────────────────────
    private ObservableList<LapTime>  dashItems;
    private Map<String, ObservableList<LapTime>> lapDataMap;

    private static final DateTimeFormatter EVENT_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    // ─────────────────────────────────────────────────────────────────────────
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        buildTableColumns();
        startWallClock();
    }

    public void setLapDataMap(Map<String, ObservableList<LapTime>> map) {
        this.lapDataMap = map;
    }

    public void setOnTimerStopped(Runnable r) {
        this.onTimerStopped = r;
    }

    /** Called by MainController after each new lap. */
    public void onNewLap(LapTime lap, String key) {
        refreshStats(key);
        appendEvent(lap, key);
        refreshDashTable(key);
    }

    public long getElapsedMs() {
        if (timerRunning)
            return stopwatchAccumMs + (System.nanoTime() - stopwatchStartNano) / 1_000_000;
        return stopwatchAccumMs;
    }

    // ── FXML action handlers ───────────────────────────────────────────────

    @FXML
    private void onStartStopTimer(ActionEvent e) {
        if (!timerRunning) {
            stopwatchStartNano = System.nanoTime();
            timerRunning = true;
            startStopBtn.setText("■  STOP");
            startStopBtn.getStyleClass().setAll("btn-timer-stop");
            timerDisplay.getStyleClass().setAll("stopwatch-display-running");
            startStopwatchTick();
        } else {
            timerRunning = false;
            stopwatchTimeline.stop();
            stopwatchAccumMs = stopwatchAccumMs + (System.nanoTime() - stopwatchStartNano) / 1_000_000;
            startStopBtn.setText("▶  START");
            startStopBtn.getStyleClass().setAll("btn-timer-start");
            timerDisplay.getStyleClass().setAll("stopwatch-display");
            if (onTimerStopped != null) onTimerStopped.run();
        }
    }

    @FXML
    private void onResetTimer(ActionEvent e) {
        if (timerRunning) {
            timerRunning = false;
            stopwatchTimeline.stop();
            startStopBtn.setText("▶  START");
            startStopBtn.getStyleClass().setAll("btn-timer-start");
            timerDisplay.getStyleClass().setAll("stopwatch-display");
        }
        stopwatchAccumMs = 0;
        timerDisplay.setText("0:00.000");
    }

    // ── Table ──────────────────────────────────────────────────────────────

    private void buildTableColumns() {
        dashTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        dashTable.setPlaceholder(new Label("No laps logged yet"));

        TableColumn<LapTime, String> numCol = new TableColumn<>("#");
        numCol.setCellValueFactory(cd -> {
            if (dashItems == null) return new SimpleStringProperty("—");
            int idx = dashItems.size() - dashItems.indexOf(cd.getValue());
            return new SimpleStringProperty(String.valueOf(idx));
        });
        numCol.setPrefWidth(40);

        TableColumn<LapTime, String> timeCol = new TableColumn<>("LAP TIME");
        timeCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getFormatedTime()));
        timeCol.setPrefWidth(100);

        TableColumn<LapTime, String> deltaCol = new TableColumn<>("SPLIT");
        deltaCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getFormattedDelta()));
        deltaCol.setPrefWidth(80);
        deltaCol.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("delta-faster", "delta-slower", "delta-neutral");
                if (item == null || empty) { setText(null); return; }
                setText(item);
                if      (item.startsWith("+"))                       getStyleClass().add("delta-slower");
                else if (item.startsWith("-") && item.length() > 1) getStyleClass().add("delta-faster");
                else                                                 getStyleClass().add("delta-neutral");
            }
        });

        TableColumn<LapTime, String> carCol = new TableColumn<>("CAR");
        carCol.setCellValueFactory(cd -> {
            Vehicle v = cd.getValue().getCarUsed();
            return new SimpleStringProperty(v.getMake() + " " + v.getModel());
        });

        TableColumn<LapTime, String> trackCol = new TableColumn<>("TRACK");
        trackCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getTrackName()));

        dashTable.getColumns().addAll(numCol, timeCol, deltaCol, carCol, trackCol);

        dashTable.setRowFactory(tv -> new TableRow<>() {
            @Override protected void updateItem(LapTime lap, boolean empty) {
                super.updateItem(lap, empty);
                getStyleClass().remove("best-lap-row");
                if (!empty && lap != null && dashItems != null) {
                    long best = dashItems.stream().mapToLong(LapTime::getTimeInMilliseconds).min().orElse(Long.MAX_VALUE);
                    if (lap.getTimeInMilliseconds() == best) getStyleClass().add("best-lap-row");
                }
            }
        });
    }

    // ── Stats refresh ──────────────────────────────────────────────────────

    private void refreshStats(String key) {
        if (lapDataMap == null) return;
        ObservableList<LapTime> laps = lapDataMap.get(key);
        if (laps == null || laps.isEmpty()) return;

        long   best    = laps.stream().mapToLong(LapTime::getTimeInMilliseconds).min().orElse(0);
        long   last    = laps.get(laps.size() - 1).getTimeInMilliseconds();
        double avg     = laps.stream().mapToLong(LapTime::getTimeInMilliseconds).average().orElse(0);
        long   totalMs = laps.stream().mapToLong(LapTime::getTimeInMilliseconds).sum();

        double mean    = avg;
        double variance = laps.stream()
                .mapToDouble(l -> Math.pow(l.getTimeInMilliseconds() - mean, 2))
                .average().orElse(0);
        double stdDev  = Math.sqrt(variance);
        long   gap     = last - best;
        int    total   = lapDataMap.values().stream().mapToInt(ObservableList::size).sum();

        bestLapValue.setText(TimeFormatter.formatMs(best));
        lastLapValue.setText(TimeFormatter.formatMs(last));
        avgLapValue.setText(TimeFormatter.formatMs((long) avg));
        consistencyValue.setText(String.format("±%.3f", stdDev / 1000.0));
        totalLapsValue.setText(String.valueOf(total));
        trackTimeValue.setText(TimeFormatter.formatMs(totalMs));
        gapToBestValue.setText(gap == 0 ? "—" : (gap > 0 ? "+" : "") + String.format("%.3f", gap / 1000.0));

        // Mirror to session-stats panel labels
        if (statBestLapValue    != null) statBestLapValue.setText(TimeFormatter.formatMs(best));
        if (statAvgValue        != null) statAvgValue.setText(TimeFormatter.formatMs((long) avg));
        if (statConsistencyValue != null) statConsistencyValue.setText(String.format("±%.3f", stdDev / 1000.0));

        if      (stdDev < 500)  { trackStateLabel.setText("GREEN");  trackStateLabel.getStyleClass().setAll("track-state-green"); }
        else if (stdDev < 2000) { trackStateLabel.setText("YELLOW"); trackStateLabel.getStyleClass().setAll("track-state-yellow"); }
        else                    { trackStateLabel.setText("RED");    trackStateLabel.getStyleClass().setAll("track-state-red"); }
    }

    private void refreshDashTable(String key) {
        if (lapDataMap == null) return;
        ObservableList<LapTime> laps = lapDataMap.get(key);
        if (laps == null) return;
        List<LapTime> reversed = new ArrayList<>(laps);
        Collections.reverse(reversed);
        dashItems = FXCollections.observableArrayList(reversed);
        dashTable.setItems(dashItems);
        dashTable.refresh();
    }

    // ── Events log ─────────────────────────────────────────────────────────

    private void appendEvent(LapTime lap, String key) {
        if (lapDataMap == null) return;
        ObservableList<LapTime> laps = lapDataMap.get(key);
        if (laps == null) return;

        String timeStamp = LocalTime.now().format(EVENT_FMT);
        long   best      = laps.stream().mapToLong(LapTime::getTimeInMilliseconds).min().orElse(Long.MAX_VALUE);
        boolean isBest   = lap.getTimeInMilliseconds() == best && laps.size() > 1;

        String msg; String styleClass = "event-text";
        if (laps.size() == 1) {
            msg = "Session started — " + lap.getTrackName() + " [" + lap.getCarUsed().getCarType() + "]";
        } else if (isBest) {
            msg = "NEW BEST  " + lap.getFormatedTime() + "  (" + lap.getFormattedDelta() + ")";
            styleClass = "event-text-highlight";
        } else if (lap.getDeltaMs() > 2000) {
            msg = "Lap " + laps.size() + "  " + lap.getFormatedTime() + "  — slow lap";
        } else if (lap.getDeltaMs() < 0) {
            msg = "Lap " + laps.size() + "  " + lap.getFormatedTime() + "  " + lap.getFormattedDelta();
            styleClass = "event-text-green";
        } else {
            msg = "Lap " + laps.size() + "  " + lap.getFormatedTime() + "  " + lap.getFormattedDelta();
        }

        Label timeLabel = new Label(timeStamp); timeLabel.getStyleClass().add("event-time");
        Label msgLabel  = new Label(msg);       msgLabel.getStyleClass().add(styleClass); msgLabel.setWrapText(true);
        HBox  row       = new HBox(10, timeLabel, msgLabel);
        row.getStyleClass().add("event-row"); row.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(msgLabel, Priority.ALWAYS);

        eventsBox.getChildren().add(0, row);
        if (eventsBox.getChildren().size() > 50)
            eventsBox.getChildren().remove(50, eventsBox.getChildren().size());
    }

    // ── Stopwatch tick ────────────────────────────────────────────────────

    private void startStopwatchTick() {
        stopwatchTimeline = new Timeline(new KeyFrame(Duration.millis(50), e -> {
            if (!timerRunning) return;
            long elapsed = stopwatchAccumMs + (System.nanoTime() - stopwatchStartNano) / 1_000_000;
            timerDisplay.setText(TimeFormatter.formatMs(elapsed));
        }));
        stopwatchTimeline.setCycleCount(Animation.INDEFINITE);
        stopwatchTimeline.play();
    }

    // ── Wall clock ────────────────────────────────────────────────────────

    private void startWallClock() {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm:ss");
        Timeline clock = new Timeline(new KeyFrame(Duration.seconds(1), e ->
                clockLabel.setText(LocalTime.now().format(fmt))));
        clock.setCycleCount(Animation.INDEFINITE);
        clock.play();
        clockLabel.setText(LocalTime.now().format(fmt));
    }
}
