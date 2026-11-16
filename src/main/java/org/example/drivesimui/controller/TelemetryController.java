package org.example.drivesimui.controller;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.*;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.stage.FileChooser;
import javafx.util.Duration;
import org.example.drivesimui.model.TelemetryLap;
import org.example.drivesimui.model.TelemetrySample;
import org.example.drivesimui.service.IbtTelemetryReader;
import org.example.drivesimui.service.CsvTelemetryImporter;
import org.example.drivesimui.util.TimeFormatter;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.Executors;

/**
 * Controller for the TELEMETRY tab.
 *
 * Responsibilities:
 *  - Import .ibt / CSV telemetry files
 *  - Render Speed (LineChart), Throttle (AreaChart), Brake (AreaChart)
 *  - Shared vertical cursor line that scrubs all three charts in sync
 *  - 2-D track map on a Canvas (from integrated velocity or PosX/Y columns)
 *  - Embedded MediaPlayer with time-offset sync to the telemetry cursor
 */
public class TelemetryController implements Initializable {

    // ── FXML injections ───────────────────────────────────────────────────
    @FXML private Button      importIbtBtn;
    @FXML private Button      importCsvBtn;
    @FXML private ComboBox<TelemetryLap> lapSelector;
    @FXML private Label       statusLabel;

    @FXML private LineChart<Number,Number> speedChart;
    @FXML private AreaChart<Number,Number> throttleChart;
    @FXML private AreaChart<Number,Number> brakeChart;

    @FXML private NumberAxis  speedXAxis;
    @FXML private NumberAxis  throttleXAxis;
    @FXML private NumberAxis  brakeXAxis;

    @FXML private Slider      scrubSlider;
    @FXML private Label       cursorTimeLabel;
    @FXML private Label       cursorSpeedLabel;
    @FXML private Label       cursorThrottleLabel;
    @FXML private Label       cursorBrakeLabel;

    @FXML private StackPane   trackMapPane;
    @FXML private Canvas      trackCanvas;
    @FXML private Label       noMapLabel;

    @FXML private MediaView   mediaView;
    @FXML private Button      loadVideoBtn;
    @FXML private Button      playPauseBtn;
    @FXML private TextField   videoOffsetField;
    @FXML private Label       noVideoLabel;

    // ── State ─────────────────────────────────────────────────────────────
    private final ObservableList<TelemetryLap> loadedLaps = FXCollections.observableArrayList();
    private TelemetryLap activeLap;
    private MediaPlayer  mediaPlayer;
    private Timeline     playbackTimeline;
    private boolean      suppressScrubListener = false;

    // Cursor overlay lines (one per chart)
    private Line speedCursor;
    private Line throttleCursor;
    private Line brakeCursor;

    // Track-map normalization params (set in redrawTrackMap)
    private double mapMinX, mapMinY, mapScale, mapOffX, mapOffY, mapH;

    // ── Initialisation ────────────────────────────────────────────────────

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        lapSelector.setItems(loadedLaps);

        lapSelector.valueProperty().addListener((obs, old, lap) -> {
            if (lap != null) displayLap(lap);
        });

        scrubSlider.valueProperty().addListener((obs, old, val) -> {
            if (!suppressScrubListener) onCursorMoved(val.doubleValue());
        });

        // Bind canvas to pane size so it scales with window resize
        trackCanvas.widthProperty().bind(trackMapPane.widthProperty());
        trackCanvas.heightProperty().bind(trackMapPane.heightProperty());
        trackCanvas.widthProperty().addListener(e -> redrawTrackMap());
        trackCanvas.heightProperty().addListener(e -> redrawTrackMap());

        // Install cursor lines once the scene is ready
        speedChart.sceneProperty().addListener((obs, o, scene) -> {
            if (scene != null) Platform.runLater(this::installCursorLines);
        });
    }

    // ── Import handlers ───────────────────────────────────────────────────

    @FXML
    private void onImportIbt(ActionEvent e) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Open iRacing .ibt Telemetry File");
        fc.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("iRacing Telemetry (*.ibt)", "*.ibt"));
        File f = fc.showOpenDialog(importIbtBtn.getScene().getWindow());
        if (f == null) return;

        statusLabel.setText("Reading " + f.getName() + "…");
        Executors.newSingleThreadExecutor().submit(() -> {
            try {
                List<TelemetryLap> laps = new IbtTelemetryReader().readLaps(f);
                Platform.runLater(() -> {
                    loadedLaps.addAll(laps);
                    if (!laps.isEmpty()) lapSelector.setValue(laps.get(0));
                    statusLabel.setText("Loaded " + laps.size() + " lap(s) from " + f.getName());
                });
            } catch (IOException ex) {
                Platform.runLater(() -> statusLabel.setText("Error: " + ex.getMessage()));
            }
        });
    }

    @FXML
    private void onImportCsv(ActionEvent e) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Open Telemetry CSV File");
        fc.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("CSV Files (*.csv)", "*.csv"));
        File f = fc.showOpenDialog(importCsvBtn.getScene().getWindow());
        if (f == null) return;

        statusLabel.setText("Reading " + f.getName() + "…");
        Executors.newSingleThreadExecutor().submit(() -> {
            try {
                TelemetryLap lap = new CsvTelemetryImporter().importFile(f);
                Platform.runLater(() -> {
                    loadedLaps.add(lap);
                    lapSelector.setValue(lap);
                    statusLabel.setText("Loaded " + lap.getSamples().size()
                        + " samples from " + f.getName());
                });
            } catch (Exception ex) {
                Platform.runLater(() -> statusLabel.setText("Error: " + ex.getMessage()));
            }
        });
    }

    // ── Display lap ───────────────────────────────────────────────────────

    private void displayLap(TelemetryLap lap) {
        activeLap = lap;
        populateCharts(lap);
        redrawTrackMap();
        suppressScrubListener = true;
        scrubSlider.setValue(0);
        suppressScrubListener = false;
        onCursorMoved(0.0);
        statusLabel.setText(lap.getSamples().size() + " samples  ·  "
            + TimeFormatter.formatMs(lap.durationMs()));
    }

    // ── Chart population ──────────────────────────────────────────────────

    private void populateCharts(TelemetryLap lap) {
        List<TelemetrySample> samples = lap.getSamples();
        // Down-sample so the chart renders quickly (JavaFX LineChart slows beyond ~2000 pts)
        int stride = Math.max(1, samples.size() / 2000);

        XYChart.Series<Number,Number> speedSeries    = new XYChart.Series<>();
        XYChart.Series<Number,Number> throttleSeries = new XYChart.Series<>();
        XYChart.Series<Number,Number> brakeSeries    = new XYChart.Series<>();

        for (int i = 0; i < samples.size(); i += stride) {
            TelemetrySample s = samples.get(i);
            speedSeries.getData().add(   new XYChart.Data<>(s.distancePct(), s.speedKmh()));
            throttleSeries.getData().add(new XYChart.Data<>(s.distancePct(), s.throttle() * 100.0));
            brakeSeries.getData().add(   new XYChart.Data<>(s.distancePct(), s.brake()    * 100.0));
        }

        speedChart.getData().setAll(List.of(speedSeries));
        throttleChart.getData().setAll(List.of(throttleSeries));
        brakeChart.getData().setAll(List.of(brakeSeries));
    }

    // ── Cursor line overlay ───────────────────────────────────────────────

    private void installCursorLines() {
        speedCursor    = attachCursorLine(speedChart);
        throttleCursor = attachCursorLine(throttleChart);
        brakeCursor    = attachCursorLine(brakeChart);
    }

    private Line attachCursorLine(XYChart<?,?> chart) {
        Line line = new Line();
        line.getStyleClass().add("telemetry-cursor-line");
        line.setMouseTransparent(true);
        line.setVisible(false);

        // Add the line into the chart's plot area overlay
        Pane plotBg = (Pane) chart.lookup(".chart-plot-background");
        if (plotBg != null) {
            Pane overlay = (Pane) plotBg.getParent();
            overlay.getChildren().add(line);
        }
        return line;
    }

    private void positionCursorLine(Line cursor, XYChart<Number,Number> chart, double distPct) {
        if (cursor == null) return;
        NumberAxis xAxis = (NumberAxis) chart.getXAxis();
        // Convert distPct → pixel X within the chart's coordinate system
        double displayX = xAxis.getDisplayPosition(distPct);
        // xAxis local coords → chart local coords
        double chartX = xAxis.localToParent(displayX, 0).getX();

        Pane plotBg = (Pane) chart.lookup(".chart-plot-background");
        if (plotBg == null) { cursor.setVisible(false); return; }

        // Position relative to the overlay pane (parent of plotBg)
        double offsetX = plotBg.getLayoutX() + chartX;
        cursor.setStartX(offsetX);
        cursor.setEndX(offsetX);
        cursor.setStartY(plotBg.getLayoutY());
        cursor.setEndY(plotBg.getLayoutY() + plotBg.getHeight());
        cursor.setVisible(true);
    }

    // ── Cursor movement (scrub) ───────────────────────────────────────────

    private void onCursorMoved(double distPct) {
        if (activeLap == null) return;
        TelemetrySample s = findSampleByDist(activeLap.getSamples(), distPct);
        if (s == null) return;

        cursorTimeLabel.setText(TimeFormatter.formatMs(s.timeMs()));
        cursorSpeedLabel.setText(String.format("%.0f km/h", s.speedKmh()));
        cursorThrottleLabel.setText(String.format("T: %.0f%%", s.throttle() * 100));
        cursorBrakeLabel.setText(String.format("B: %.0f%%", s.brake() * 100));

        positionCursorLine(speedCursor,    speedChart,    distPct);
        positionCursorLine(throttleCursor, throttleChart, distPct);
        positionCursorLine(brakeCursor,    brakeChart,    distPct);

        redrawTrackMapWithCursor(s);
        syncVideoToSample(s);
    }

    // ── Track map ─────────────────────────────────────────────────────────

    private void redrawTrackMap() {
        if (activeLap == null) { clearCanvas(); return; }
        List<TelemetrySample> samples = activeLap.getSamples();
        boolean hasPos = samples.stream().anyMatch(s -> !Double.isNaN(s.posX()));
        noMapLabel.setVisible(!hasPos);
        if (!hasPos) { clearCanvas(); return; }

        double W = trackCanvas.getWidth();
        double H = trackCanvas.getHeight();
        if (W < 10 || H < 10) return;

        GraphicsContext gc = trackCanvas.getGraphicsContext2D();
        gc.clearRect(0, 0, W, H);

        // Bounding box
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (TelemetrySample s : samples) {
            if (Double.isNaN(s.posX())) continue;
            if (s.posX() < minX) minX = s.posX();
            if (s.posX() > maxX) maxX = s.posX();
            if (s.posY() < minY) minY = s.posY();
            if (s.posY() > maxY) maxY = s.posY();
        }
        double rX = maxX - minX, rY = maxY - minY;
        if (rX < 1e-6 || rY < 1e-6) return;

        double pad   = 20;
        double scale = Math.min((W - pad * 2) / rX, (H - pad * 2) / rY);
        double offX  = (W - rX * scale) / 2.0;
        double offY  = (H - rY * scale) / 2.0;

        // Cache normalisation params for cursor dot
        mapMinX  = minX;  mapMinY  = minY;
        mapScale = scale; mapOffX  = offX; mapOffY = offY; mapH = H;

        // Draw track outline
        gc.setStroke(Color.web("#2E3A50"));
        gc.setLineWidth(2.5);
        gc.beginPath();
        boolean first = true;
        for (TelemetrySample s : samples) {
            if (Double.isNaN(s.posX())) continue;
            double px = offX + (s.posX() - minX) * scale;
            double py = H - (offY + (s.posY() - minY) * scale);
            if (first) { gc.moveTo(px, py); first = false; }
            else        { gc.lineTo(px, py); }
        }
        gc.stroke();
    }

    private void redrawTrackMapWithCursor(TelemetrySample sample) {
        redrawTrackMap();
        if (sample == null || Double.isNaN(sample.posX())) return;

        GraphicsContext gc = trackCanvas.getGraphicsContext2D();
        double px = mapOffX + (sample.posX() - mapMinX) * mapScale;
        double py = mapH - (mapOffY + (sample.posY() - mapMinY) * mapScale);

        gc.setEffect(new DropShadow(10, Color.web("#E8002D")));
        gc.setFill(Color.web("#E8002D"));
        gc.fillOval(px - 6, py - 6, 12, 12);
        gc.setEffect(null);
    }

    private void clearCanvas() {
        GraphicsContext gc = trackCanvas.getGraphicsContext2D();
        gc.clearRect(0, 0, trackCanvas.getWidth(), trackCanvas.getHeight());
    }

    // ── Video ─────────────────────────────────────────────────────────────

    @FXML
    private void onLoadVideo(ActionEvent e) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Open Replay Video");
        fc.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Video Files", "*.mp4", "*.mov", "*.m4v", "*.avi"));
        File f = fc.showOpenDialog(loadVideoBtn.getScene().getWindow());
        if (f == null) return;

        if (mediaPlayer != null) {
            stopPlayback();
            mediaPlayer.dispose();
            mediaPlayer = null;
        }

        try {
            Media media = new Media(f.toURI().toString());
            mediaPlayer = new MediaPlayer(media);
            mediaView.setMediaPlayer(mediaPlayer);
            mediaPlayer.setOnReady(() -> {
                playPauseBtn.setDisable(false);
                noVideoLabel.setVisible(false);
                statusLabel.setText("Video ready: " + f.getName());
            });
            mediaPlayer.setOnError(() ->
                statusLabel.setText("Video error: " + mediaPlayer.getError().getMessage()));
        } catch (Exception ex) {
            statusLabel.setText("Could not load video: " + ex.getMessage());
        }
    }

    @FXML
    private void onPlayPause(ActionEvent e) {
        if (mediaPlayer == null) return;
        if (mediaPlayer.getStatus() == MediaPlayer.Status.PLAYING) {
            stopPlayback();
            playPauseBtn.setText("▶  PLAY");
            playPauseBtn.getStyleClass().setAll("btn-timer-start");
        } else {
            mediaPlayer.play();
            playPauseBtn.setText("⏸  PAUSE");
            playPauseBtn.getStyleClass().setAll("btn-timer-stop");
            startPlaybackSync();
        }
    }

    /** Drives the scrub slider while video is playing, keeping telemetry in sync. */
    private void startPlaybackSync() {
        playbackTimeline = new Timeline(new KeyFrame(Duration.millis(50), ev -> {
            if (activeLap == null || mediaPlayer == null) return;
            double offsetSec    = parseOffsetField();
            double videoTimeSec = mediaPlayer.getCurrentTime().toSeconds() - offsetSec;
            if (videoTimeSec < 0) return;

            long videoTimeMs = (long)(videoTimeSec * 1000);
            TelemetrySample s = findSampleByTime(activeLap.getSamples(), videoTimeMs);
            if (s == null) return;

            suppressScrubListener = true;
            scrubSlider.setValue(s.distancePct());
            suppressScrubListener = false;
            onCursorMoved(s.distancePct());
        }));
        playbackTimeline.setCycleCount(Animation.INDEFINITE);
        playbackTimeline.play();
    }

    private void stopPlayback() {
        if (playbackTimeline != null) { playbackTimeline.stop(); playbackTimeline = null; }
        if (mediaPlayer != null)      mediaPlayer.pause();
    }

    /** Seeks the video to the sample's timestamp when the user drags the slider. */
    private void syncVideoToSample(TelemetrySample s) {
        if (mediaPlayer == null) return;
        if (mediaPlayer.getStatus() == MediaPlayer.Status.PLAYING) return; // don't interrupt
        double targetSec = s.timeMs() / 1000.0 + parseOffsetField();
        if (targetSec >= 0) {
            mediaPlayer.seek(Duration.seconds(targetSec));
        }
    }

    private double parseOffsetField() {
        try { return Double.parseDouble(videoOffsetField.getText().trim()); }
        catch (NumberFormatException ex) { return 0.0; }
    }

    // ── Binary searches ───────────────────────────────────────────────────

    private TelemetrySample findSampleByDist(List<TelemetrySample> samples, double dist) {
        if (samples.isEmpty()) return null;
        int lo = 0, hi = samples.size() - 1;
        while (lo < hi) {
            int mid = (lo + hi) / 2;
            if (samples.get(mid).distancePct() < dist) lo = mid + 1;
            else hi = mid;
        }
        return samples.get(lo);
    }

    private TelemetrySample findSampleByTime(List<TelemetrySample> samples, long timeMs) {
        if (samples.isEmpty()) return null;
        int lo = 0, hi = samples.size() - 1;
        while (lo < hi) {
            int mid = (lo + hi) / 2;
            if (samples.get(mid).timeMs() < timeMs) lo = mid + 1;
            else hi = mid;
        }
        return samples.get(lo);
    }
}
