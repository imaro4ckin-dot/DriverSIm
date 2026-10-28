package org.example.drivesimui;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Builds and owns the Live Dashboard tab content.
 *
 * Layout (mirrors the Timing Dash screenshot):
 *
 *  ┌────────────────────────────────────────────────────────────────────┐
 *  │  STOPWATCH  │  BEST LAP │ LAST LAP │ AVG LAP │ CONSISTENCY │ STATE │
 *  ├──────────────────────────────┬─────────────────────────────────────┤
 *  │  LAP TABLE (last 20 laps)    │  SESSION STATS   │  EVENTS LOG      │
 *  │  # | Time | Split | Car      │  Total laps      │  timestamped log  │
 *  │                              │  Best lap        │                   │
 *  │                              │  Total track time│                   │
 *  └──────────────────────────────┴─────────────────────────────────────┘
 */
public class DashboardController {

    // ── Stat card value labels (updated from Launcher) ───────────────
    private final Label bestLapValue    = makeStatValue("—",    "stat-card-value-red");
    private final Label lastLapValue    = makeStatValue("—",    "stat-card-value");
    private final Label avgLapValue     = makeStatValue("—",    "stat-card-value");
    private final Label consistencyValue= makeStatValue("—",    "stat-card-value-green");
    private final Label totalLapsValue  = makeStatValue("0",    "stat-card-value");
    private final Label trackTimeValue  = makeStatValue("0:00", "stat-card-value");
    private final Label gapToBestValue  = makeStatValue("—",    "stat-card-value");
    private final Label trackStateLabel = new Label("GREEN");

    // ── Stopwatch state ──────────────────────────────────────────────
    private long   stopwatchStartNano = 0;
    private long   stopwatchAccumMs   = 0;
    private boolean timerRunning      = false;
    private Timeline stopwatchTimeline;

    private final Label timerDisplay = new Label("0:00.000");
    private Button startStopBtn;
    private Runnable onTimerStopped; // callback → fills time input in Launcher

    // ── Lap table shown on dashboard ─────────────────────────────────
    private final TableView<LapTime> dashTable = new TableView<>();
    private ObservableList<LapTime>  dashItems;

    // ── Events log ───────────────────────────────────────────────────
    private final VBox eventsBox = new VBox(0);
    private static final DateTimeFormatter EVENT_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss");

    // ── Wall clock ───────────────────────────────────────────────────
    private final Label clockLabel = new Label();

    // ── Reference to Launcher's lap data ────────────────────────────
    private Map<String, ObservableList<LapTime>> lapDataMap;

    // ─────────────────────────────────────────────────────────────────
    public void init(Map<String, ObservableList<LapTime>> lapDataMap,
                     Runnable onTimerStopped) {
        this.lapDataMap    = lapDataMap;
        this.onTimerStopped = onTimerStopped;
    }

    // ─────────────────────────────────────────────────────────────────
    /** Called by Launcher every time a new lap is logged. */
    public void onNewLap(LapTime lap, String key) {
        refreshStats(key);
        appendEvent(lap, key);
        refreshDashTable(key);
    }

    // ─────────────────────────────────────────────────────────────────
    /** Builds and returns the full dashboard Node to be placed in a Tab. */
    public Node build() {
        // ── Top row: stopwatch + stat cards ──────────────────────────
        HBox topRow = new HBox(12,
                buildStopwatchCard(),
                buildStatCard("BEST LAP",    bestLapValue),
                buildStatCard("LAST LAP",    lastLapValue),
                buildStatCard("AVG LAP",     avgLapValue),
                buildStatCard("CONSISTENCY", consistencyValue),
                buildTrackStateCard());
        topRow.setAlignment(Pos.CENTER_LEFT);

        // ── Bottom row: lap table | session stats | events ────────────
        VBox lapTablePanel = buildLapTablePanel();
        VBox statsPanel    = buildSessionStatsPanel();
        VBox eventsPanel   = buildEventsPanel();

        HBox bottomRow = new HBox(12, lapTablePanel, statsPanel, eventsPanel);
        HBox.setHgrow(lapTablePanel, Priority.ALWAYS);
        HBox.setHgrow(eventsPanel,   Priority.ALWAYS);

        // ── Wall clock (bottom-right of top row) ──────────────────────
        startWallClock();

        VBox root = new VBox(14, topRow, bottomRow);
        root.getStyleClass().add("dashboard-root");
        VBox.setVgrow(bottomRow, Priority.ALWAYS);

        // Wrap in ScrollPane so it never clips on small windows
        ScrollPane sp = new ScrollPane(root);
        sp.setFitToWidth(true);
        sp.setFitToHeight(true);
        sp.setStyle("-fx-background-color: #0D1117; -fx-background: #0D1117;");
        return sp;
    }

    // ─────────────────────────────────────────────────────────────────
    // STOPWATCH
    // ─────────────────────────────────────────────────────────────────
    private VBox buildStopwatchCard() {
        timerDisplay.getStyleClass().add("stopwatch-display");

        startStopBtn = new Button("▶  START");
        startStopBtn.getStyleClass().add("btn-timer-start");

        Button resetBtn = new Button("RESET");
        resetBtn.getStyleClass().add("btn-timer-reset");

        startStopBtn.setOnAction(e -> {
            if (!timerRunning) {
                // START
                stopwatchStartNano = System.nanoTime();
                timerRunning = true;
                startStopBtn.setText("■  STOP");
                startStopBtn.getStyleClass().setAll("btn-timer-stop");
                timerDisplay.getStyleClass().setAll("stopwatch-display-running");
                startStopwatchTick();
            } else {
                // STOP → capture time, fill input
                timerRunning = false;
                stopwatchTimeline.stop();
                long elapsed = stopwatchAccumMs +
                        (System.nanoTime() - stopwatchStartNano) / 1_000_000;
                stopwatchAccumMs = elapsed;
                startStopBtn.setText("▶  START");
                startStopBtn.getStyleClass().setAll("btn-timer-start");
                timerDisplay.getStyleClass().setAll("stopwatch-display");
                if (onTimerStopped != null) onTimerStopped.run();
            }
        });

        resetBtn.setOnAction(e -> {
            if (timerRunning) {
                timerRunning = false;
                stopwatchTimeline.stop();
                startStopBtn.setText("▶  START");
                startStopBtn.getStyleClass().setAll("btn-timer-start");
                timerDisplay.getStyleClass().setAll("stopwatch-display");
            }
            stopwatchAccumMs = 0;
            timerDisplay.setText("0:00.000");
        });

        Label header = new Label("LAP TIMER");
        header.getStyleClass().add("stat-card-header");

        HBox btnRow = new HBox(8, startStopBtn, resetBtn);
        btnRow.setAlignment(Pos.CENTER);

        VBox card = new VBox(8, header, timerDisplay, btnRow);
        card.getStyleClass().add("stopwatch-card");
        card.setAlignment(Pos.CENTER);
        return card;
    }

    private void startStopwatchTick() {
        stopwatchTimeline = new Timeline(new KeyFrame(Duration.millis(50), e -> {
            if (!timerRunning) return;
            long elapsed = stopwatchAccumMs +
                    (System.nanoTime() - stopwatchStartNano) / 1_000_000;
            timerDisplay.setText(formatMs(elapsed));
        }));
        stopwatchTimeline.setCycleCount(Animation.INDEFINITE);
        stopwatchTimeline.play();
    }

    /** Returns the current elapsed ms (for Launcher to read into the time field). */
    public long getElapsedMs() {
        if (timerRunning) {
            return stopwatchAccumMs +
                    (System.nanoTime() - stopwatchStartNano) / 1_000_000;
        }
        return stopwatchAccumMs;
    }

    // ─────────────────────────────────────────────────────────────────
    // STAT CARDS
    // ─────────────────────────────────────────────────────────────────
    private VBox buildStatCard(String title, Label valueLabel) {
        Label header = new Label(title);
        header.getStyleClass().add("stat-card-header");
        VBox card = new VBox(4, header, valueLabel);
        card.getStyleClass().add("stat-card");
        card.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(card, Priority.SOMETIMES);
        return card;
    }

    private VBox buildTrackStateCard() {
        Label header = new Label("TRACK STATE");
        header.getStyleClass().add("stat-card-header");
        trackStateLabel.getStyleClass().add("track-state-green");
        trackStateLabel.setAlignment(Pos.CENTER);
        trackStateLabel.setMaxWidth(Double.MAX_VALUE);

        VBox card = new VBox(8, header, trackStateLabel);
        card.getStyleClass().add("track-state-card");
        card.setAlignment(Pos.CENTER);
        return card;
    }

    private static Label makeStatValue(String text, String styleClass) {
        Label lbl = new Label(text);
        lbl.getStyleClass().add(styleClass);
        return lbl;
    }

    // ─────────────────────────────────────────────────────────────────
    // LAP TABLE
    // ─────────────────────────────────────────────────────────────────
    private VBox buildLapTablePanel() {
        dashTable.getStyleClass().add("dash-lap-table");
        dashTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        dashTable.setPlaceholder(new Label("No laps logged yet"));

        TableColumn<LapTime, String> numCol = new TableColumn<>("#");
        numCol.setCellValueFactory(cd -> {
            if (dashItems == null) return new javafx.beans.property.SimpleStringProperty("—");
            int idx = dashItems.size() - dashItems.indexOf(cd.getValue());
            return new javafx.beans.property.SimpleStringProperty(String.valueOf(idx));
        });
        numCol.setPrefWidth(40);

        TableColumn<LapTime, String> timeCol = new TableColumn<>("LAP TIME");
        timeCol.setCellValueFactory(cd ->
                new javafx.beans.property.SimpleStringProperty(cd.getValue().getFormatedTime()));
        timeCol.setPrefWidth(100);

        TableColumn<LapTime, String> deltaCol = new TableColumn<>("SPLIT");
        deltaCol.setCellValueFactory(cd ->
                new javafx.beans.property.SimpleStringProperty(cd.getValue().getFormattedDelta()));
        deltaCol.setPrefWidth(80);
        deltaCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
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
            return new javafx.beans.property.SimpleStringProperty(v.getMake() + " " + v.getModel());
        });

        TableColumn<LapTime, String> trackCol = new TableColumn<>("TRACK");
        trackCol.setCellValueFactory(cd ->
                new javafx.beans.property.SimpleStringProperty(cd.getValue().getTrackName()));

        dashTable.getColumns().addAll(numCol, timeCol, deltaCol, carCol, trackCol);

        // Row factory: highlight best lap in gold
        dashTable.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(LapTime lap, boolean empty) {
                super.updateItem(lap, empty);
                getStyleClass().remove("best-lap-row");
                if (!empty && lap != null && dashItems != null) {
                    long best = dashItems.stream()
                            .mapToLong(LapTime::getTimeInMilliseconds).min().orElse(Long.MAX_VALUE);
                    if (lap.getTimeInMilliseconds() == best) getStyleClass().add("best-lap-row");
                }
            }
        });

        Label title = new Label("RECENT LAPS");
        title.getStyleClass().add("dash-panel-title");

        VBox panel = new VBox(8, title, dashTable);
        panel.getStyleClass().add("dash-panel");
        VBox.setVgrow(dashTable, Priority.ALWAYS);
        panel.setMinWidth(380);
        return panel;
    }

    // ─────────────────────────────────────────────────────────────────
    // SESSION STATS
    // ─────────────────────────────────────────────────────────────────
    private VBox buildSessionStatsPanel() {
        Label title = new Label("SESSION STATS");
        title.getStyleClass().add("dash-panel-title");

        clockLabel.getStyleClass().add("clock-display");

        VBox rows = new VBox(10,
                statRow("TOTAL LAPS",    totalLapsValue),
                statRow("BEST LAP",      bestLapValue),
                statRow("AVERAGE",       avgLapValue),
                statRow("CONSISTENCY",   consistencyValue),
                statRow("TRACK TIME",    trackTimeValue),
                statRow("GAP TO BEST",   gapToBestValue),
                new Separator(),
                statRow("CLOCK",         clockLabel));

        VBox panel = new VBox(10, title, rows);
        panel.getStyleClass().add("dash-panel");
        panel.setPrefWidth(230);
        panel.setMinWidth(200);
        return panel;
    }

    private HBox statRow(String label, Node valueNode) {
        Label lbl = new Label(label);
        lbl.getStyleClass().add("dash-stat-row-label");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        if (valueNode instanceof Label vl) vl.getStyleClass().add("dash-stat-row-value");
        HBox row = new HBox(8, lbl, spacer, valueNode);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    // ─────────────────────────────────────────────────────────────────
    // EVENTS LOG
    // ─────────────────────────────────────────────────────────────────
    private VBox buildEventsPanel() {
        Label title = new Label("RACE EVENTS");
        title.getStyleClass().add("dash-panel-title");

        eventsBox.setSpacing(0);

        ScrollPane scroll = new ScrollPane(eventsBox);
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(false);
        scroll.setPrefHeight(300);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");

        VBox panel = new VBox(8, title, scroll);
        panel.getStyleClass().add("dash-panel");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        panel.setMinWidth(260);
        return panel;
    }

    private void appendEvent(LapTime lap, String key) {
        String timeStamp = LocalTime.now().format(EVENT_FMT);
        ObservableList<LapTime> laps = lapDataMap.get(key);

        // Determine event message
        String msg;
        String styleClass = "event-text";

        long best = laps.stream().mapToLong(LapTime::getTimeInMilliseconds).min().orElse(Long.MAX_VALUE);
        boolean isBest = lap.getTimeInMilliseconds() == best && laps.size() > 1;

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

        Label timeLabel = new Label(timeStamp);
        timeLabel.getStyleClass().add("event-time");

        Label msgLabel = new Label(msg);
        msgLabel.getStyleClass().add(styleClass);
        msgLabel.setWrapText(true);

        HBox row = new HBox(10, timeLabel, msgLabel);
        row.getStyleClass().add("event-row");
        row.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(msgLabel, Priority.ALWAYS);

        // Add to top of list (newest first)
        eventsBox.getChildren().add(0, row);

        // Keep max 50 events
        if (eventsBox.getChildren().size() > 50)
            eventsBox.getChildren().remove(50, eventsBox.getChildren().size());
    }

    // ─────────────────────────────────────────────────────────────────
    // STATS REFRESH
    // ─────────────────────────────────────────────────────────────────
    private void refreshStats(String key) {
        ObservableList<LapTime> laps = lapDataMap.get(key);
        if (laps == null || laps.isEmpty()) return;

        long best = laps.stream().mapToLong(LapTime::getTimeInMilliseconds).min().orElse(0);
        long last = laps.get(laps.size() - 1).getTimeInMilliseconds();
        double avg = laps.stream().mapToLong(LapTime::getTimeInMilliseconds).average().orElse(0);
        long totalMs = laps.stream().mapToLong(LapTime::getTimeInMilliseconds).sum();

        // Consistency: std deviation in ms, show as ±X.XXX
        double mean = avg;
        double variance = laps.stream()
                .mapToDouble(l -> Math.pow(l.getTimeInMilliseconds() - mean, 2))
                .average().orElse(0);
        double stdDev = Math.sqrt(variance);

        // Gap: last lap vs best
        long gap = last - best;

        // Total laps across all sessions
        int total = lapDataMap.values().stream().mapToInt(ObservableList::size).sum();

        bestLapValue.setText(formatMs(best));
        lastLapValue.setText(formatMs(last));
        avgLapValue.setText(formatMs((long) avg));
        consistencyValue.setText(String.format("±%.3f", stdDev / 1000.0));
        totalLapsValue.setText(String.valueOf(total));
        trackTimeValue.setText(formatMs(totalMs));
        gapToBestValue.setText(gap == 0 ? "—" : (gap > 0 ? "+" : "") + String.format("%.3f", gap / 1000.0));

        // Track state: green/yellow based on consistency
        if (stdDev < 500) {
            trackStateLabel.setText("GREEN");
            trackStateLabel.getStyleClass().setAll("track-state-green");
        } else if (stdDev < 2000) {
            trackStateLabel.setText("YELLOW");
            trackStateLabel.getStyleClass().setAll("track-state-yellow");
        } else {
            trackStateLabel.setText("RED");
            trackStateLabel.getStyleClass().setAll("track-state-red");
        }
    }

    private void refreshDashTable(String key) {
        ObservableList<LapTime> laps = lapDataMap.get(key);
        if (laps == null) return;

        // Build a reverse-order copy (newest lap at top)
        List<LapTime> reversed = new ArrayList<>(laps);
        java.util.Collections.reverse(reversed);
        dashItems = javafx.collections.FXCollections.observableArrayList(reversed);
        dashTable.setItems(dashItems);
        dashTable.refresh();
    }

    // ─────────────────────────────────────────────────────────────────
    // WALL CLOCK
    // ─────────────────────────────────────────────────────────────────
    private void startWallClock() {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm:ss");
        Timeline clock = new Timeline(new KeyFrame(Duration.seconds(1), e ->
                clockLabel.setText(LocalTime.now().format(fmt))));
        clock.setCycleCount(Animation.INDEFINITE);
        clock.play();
        clockLabel.setText(LocalTime.now().format(fmt));
    }

    // ─────────────────────────────────────────────────────────────────
    private String formatMs(long ms) {
        long minutes = (ms / 1000) / 60;
        long seconds = (ms / 1000) % 60;
        long millis  = ms % 1000;
        return String.format("%d:%02d.%03d", minutes, seconds, millis);
    }
}
