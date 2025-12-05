package org.example;

import engine.*;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SystemMonitorFX extends Application {

    private SystemInfoService service;
    private ScheduledExecutorService executor;

    private Label cpuValueLabel, cpuDetailLabel;
    private ProgressBar cpuBar;

    private Label ramValueLabel, ramDetailLabel;
    private ProgressBar ramBar;

    private Label gpuLoadValueLabel, gpuLoadDetailLabel;
    private ProgressBar gpuLoadBar;

    private Label gpuTempValueLabel, gpuTempDetailLabel;
    private ProgressBar gpuTempBar;

    private VBox disksContainer;

    private volatile List<DiskInfo> cachedDisks = null;
    private volatile double cachedGpuTemp = 0.0;
    private int tickCounter = 0;

    private static final String CSS_STYLES = """
        .root {
            -fx-background-color: #1e1e1e;
            -fx-font-family: 'Segoe UI', sans-serif;
        }
        .header-label {
            -fx-font-size: 24px;
            -fx-font-weight: bold;
            -fx-text-fill: #e0e0e0;
        }
        .card {
            -fx-background-color: #2d2d2d;
            -fx-background-radius: 10;
            -fx-border-radius: 10;
            -fx-border-color: #3d3d3d;
            -fx-border-width: 1;
            -fx-padding: 15;
        }
        .card-title {
            -fx-font-size: 14px;
            -fx-font-weight: bold;
            -fx-text-fill: #aaaaaa;
        }
        .metric-value {
            -fx-font-size: 32px;
            -fx-font-weight: bold;
            -fx-text-fill: white;
        }
        .metric-detail {
            -fx-font-size: 12px;
            -fx-text-fill: #808080;
        }
        .progress-bar {
            -fx-pref-height: 10px;
            -fx-background-color: #404040;
            -fx-background-radius: 5;
        }
        .progress-bar .track { -fx-background-color: transparent; }
        .progress-bar .bar {
            -fx-background-radius: 5;
            -fx-background-insets: 0;
            -fx-transition: -fx-background-color 0.2s;
        }
        .state-ok-blue .bar   { -fx-background-color: #2196f3; }
        .state-ok-purple .bar { -fx-background-color: #ab47bc; }
        .state-ok-pink .bar   { -fx-background-color: #ec407a; }
        .state-ok-cyan .bar   { -fx-background-color: #26c6da; }
        .state-ok-green .bar  { -fx-background-color: #66bb6a; }
        .state-warn .bar      { -fx-background-color: #ffca28; }
        .state-crit .bar      { -fx-background-color: #ef5350; }
        .scroll-pane { -fx-background-color: transparent; }
        .scroll-pane > .viewport { -fx-background-color: transparent; }
        .scroll-bar { -fx-background-color: transparent; }
        .scroll-bar:vertical { -fx-pref-width: 8px; }
        .scroll-bar .track { -fx-background-color: #2d2d2d; -fx-background-radius: 4px; }
        .scroll-bar .thumb { -fx-background-color: #555555; -fx-background-radius: 4px; }
        .scroll-bar .increment-button, .scroll-bar .decrement-button { -fx-pref-height: 0; -fx-pref-width: 0; }
    """;

    @Override
    public void start(Stage primaryStage) {
        service = SystemInfoService.createDefault();

        BorderPane root = new BorderPane();
        root.getStylesheets().add("data:text/css," + CSS_STYLES.replaceAll("\n", ""));

        HBox header = new HBox();
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(20));
        Label title = new Label("IRON VISION");
        title.getStyleClass().add("header-label");
        Rectangle statusDot = new Rectangle(8, 8, Color.LIMEGREEN);
        statusDot.setArcWidth(8);
        statusDot.setArcHeight(8);
        Label statusText = new Label(" MONITORING ACTIVE");
        statusText.setStyle("-fx-text-fill: #606060; -fx-font-size: 10px; -fx-font-weight: bold;");
        HBox statusBox = new HBox(5, statusDot, statusText);
        statusBox.setAlignment(Pos.CENTER_LEFT);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        header.getChildren().addAll(title, spacer, statusBox);
        root.setTop(header);

        GridPane grid = new GridPane();
        grid.setPadding(new Insets(0, 20, 20, 20));
        grid.setHgap(15);
        grid.setVgap(15);
        ColumnConstraints col1 = new ColumnConstraints(); col1.setPercentWidth(50);
        ColumnConstraints col2 = new ColumnConstraints(); col2.setPercentWidth(50);
        grid.getColumnConstraints().addAll(col1, col2);

        VBox cpuCard = createCard("PROCESSOR (CPU)");
        cpuValueLabel = new Label("0%"); cpuValueLabel.getStyleClass().add("metric-value");
        cpuDetailLabel = new Label("Loading..."); cpuDetailLabel.getStyleClass().add("metric-detail");
        cpuBar = new ProgressBar(0); cpuBar.setMaxWidth(Double.MAX_VALUE); cpuBar.getStyleClass().add("state-ok-blue");
        cpuCard.getChildren().addAll(cpuValueLabel, cpuBar, cpuDetailLabel);
        grid.add(cpuCard, 0, 0);

        VBox ramCard = createCard("MEMORY (RAM)");
        ramValueLabel = new Label("0%"); ramValueLabel.getStyleClass().add("metric-value");
        ramDetailLabel = new Label("Loading..."); ramDetailLabel.getStyleClass().add("metric-detail");
        ramBar = new ProgressBar(0); ramBar.setMaxWidth(Double.MAX_VALUE); ramBar.getStyleClass().add("state-ok-purple");
        ramCard.getChildren().addAll(ramValueLabel, ramBar, ramDetailLabel);
        grid.add(ramCard, 1, 0);

        VBox gpuLoadCard = createCard("GPU LOAD & VRAM");
        gpuLoadValueLabel = new Label("0%"); gpuLoadValueLabel.getStyleClass().add("metric-value");
        gpuLoadDetailLabel = new Label("Loading..."); gpuLoadDetailLabel.getStyleClass().add("metric-detail");
        gpuLoadBar = new ProgressBar(0); gpuLoadBar.setMaxWidth(Double.MAX_VALUE); gpuLoadBar.getStyleClass().add("state-ok-pink");
        gpuLoadCard.getChildren().addAll(gpuLoadValueLabel, gpuLoadBar, gpuLoadDetailLabel);
        grid.add(gpuLoadCard, 0, 1);

        VBox gpuTempCard = createCard("GPU TEMPERATURE");
        gpuTempValueLabel = new Label("0°C"); gpuTempValueLabel.getStyleClass().add("metric-value");
        gpuTempDetailLabel = new Label("Thermal Status"); gpuTempDetailLabel.getStyleClass().add("metric-detail");
        gpuTempBar = new ProgressBar(0); gpuTempBar.setMaxWidth(Double.MAX_VALUE); gpuTempBar.getStyleClass().add("state-ok-green");
        gpuTempCard.getChildren().addAll(gpuTempValueLabel, gpuTempBar, gpuTempDetailLabel);
        grid.add(gpuTempCard, 1, 1);

        VBox diskCard = createCard("STORAGE");
        disksContainer = new VBox(10); disksContainer.setPadding(new Insets(0, 5, 0, 0));
        ScrollPane scrollPane = new ScrollPane(disksContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setPrefHeight(100);
        diskCard.getChildren().add(scrollPane);
        grid.add(diskCard, 0, 2, 2, 1);

        root.setCenter(grid);
        Scene scene = new Scene(root, 700, 580);
        primaryStage.setTitle("IronVision v1.1");
        primaryStage.setScene(scene);

        startMonitoring();
        primaryStage.setOnCloseRequest(e -> stopMonitoring());
        primaryStage.show();
    }

    private VBox createCard(String titleText) {
        VBox card = new VBox(10);
        card.getStyleClass().add("card");
        DropShadow shadow = new DropShadow();
        shadow.setColor(Color.rgb(0,0,0, 0.3));
        shadow.setRadius(10);
        shadow.setOffsetY(2);
        card.setEffect(shadow);
        Label title = new Label(titleText);
        title.getStyleClass().add("card-title");
        card.getChildren().add(title);
        return card;
    }

    private void startMonitoring() {
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        });
        executor.scheduleAtFixedRate(this::updateData, 0, 500, TimeUnit.MILLISECONDS);
    }

    private void updateData() {
        try {
            CpuInfo cpu = service.readCpu();
            RamInfo ram = service.readRam();
            GpuInfo gpu = service.readGpu();

            if (tickCounter % 4 == 0) {
                cachedDisks = service.readDisks();
                cachedGpuTemp = gpu.getTemperature();
            }
            tickCounter++;

            Platform.runLater(() -> {
                cpuValueLabel.setText(String.format("%.1f%%", cpu.getUsage()));
                cpuDetailLabel.setText(String.format("%s\n%d Cores @ %.2f GHz",
                        cpu.getName(), cpu.getCores(), cpu.getBaseClock()));
                cpuBar.setProgress(cpu.getUsage() / 100.0);
                updateBarStyle(cpuBar, cpu.getUsage(), 60, 85, "state-ok-blue");

                ramValueLabel.setText(String.format("%.0f%%", ram.getUsagePercent()));
                ramDetailLabel.setText(String.format("Used: %s / Free: %s\nTotal: %s",
                        ram.getFormattedUsed(), ram.getFormattedFree(), ram.getFormattedTotal()));
                ramBar.setProgress(ram.getUsagePercent() / 100.0);
                updateBarStyle(ramBar, ram.getUsagePercent(), 75, 90, "state-ok-purple");

                gpuLoadValueLabel.setText(String.format("%.1f%%", gpu.getGpuLoad()));
                gpuLoadDetailLabel.setText(String.format("%s\nVRAM: %s",
                        gpu.getName(), gpu.getFormattedVramInfo()));
                gpuLoadBar.setProgress(gpu.getGpuLoad() / 100.0);
                updateBarStyle(gpuLoadBar, gpu.getGpuLoad(), 60, 90, "state-ok-pink");

                if (cachedGpuTemp > 0) {
                    gpuTempValueLabel.setText(String.format("%.0f°C", cachedGpuTemp));
                    gpuTempBar.setProgress(cachedGpuTemp / 100.0);
                    updateBarStyle(gpuTempBar, cachedGpuTemp, 65, 82, "state-ok-green");

                    if (cachedGpuTemp > 82) gpuTempValueLabel.setStyle("-fx-text-fill: #ef5350; -fx-font-weight: bold; -fx-font-size: 32px;");
                    else if (cachedGpuTemp > 65) gpuTempValueLabel.setStyle("-fx-text-fill: #ffca28; -fx-font-weight: bold; -fx-font-size: 32px;");
                    else gpuTempValueLabel.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 32px;");
                } else {
                    gpuTempValueLabel.setText("N/A");
                    gpuTempValueLabel.setStyle("-fx-text-fill: #808080; -fx-font-weight: bold; -fx-font-size: 32px;");
                    gpuTempBar.setProgress(0);
                }

                if (cachedDisks != null && tickCounter % 4 == 1) {
                    disksContainer.getChildren().clear();
                    for (DiskInfo disk : cachedDisks) {
                        disksContainer.getChildren().add(createDiskRow(disk));
                    }
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateBarStyle(ProgressBar bar, double value, double warnThreshold, double critThreshold, String okStyle) {
        bar.getStyleClass().removeAll("state-ok-blue", "state-ok-purple", "state-ok-pink",
                "state-ok-green", "state-ok-cyan", "state-warn", "state-crit");
        if (value >= critThreshold) bar.getStyleClass().add("state-crit");
        else if (value >= warnThreshold) bar.getStyleClass().add("state-warn");
        else bar.getStyleClass().add(okStyle);
    }

    private VBox createDiskRow(DiskInfo disk) {
        VBox row = new VBox(4);
        HBox topRow = new HBox();
        Label name = new Label(disk.getName());
        name.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 11px;");
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        Label stats = new Label(String.format("%.0f%%", disk.getUsagePercent()));
        stats.setStyle("-fx-text-fill: #a0a0a0; -fx-font-size: 11px;");
        topRow.getChildren().addAll(name, spacer, stats);

        ProgressBar pBar = new ProgressBar(disk.getUsagePercent() / 100.0);
        pBar.setMaxWidth(Double.MAX_VALUE);
        pBar.getStyleClass().add("disk-bar");
        pBar.setPrefHeight(6);
        updateBarStyle(pBar, disk.getUsagePercent(), 80, 92, "state-ok-cyan");

        Label details = new Label(String.format("%s free of %s", disk.getFormattedFreeSpace(), disk.getFormattedTotalSpace()));
        details.setStyle("-fx-text-fill: #606060; -fx-font-size: 10px;");
        row.getChildren().addAll(topRow, pBar, details);
        return row;
    }

    private void stopMonitoring() {
        if (executor != null) executor.shutdownNow();
        Platform.exit();
        System.exit(0);
    }

    public static void main(String[] args) {
        launch(args);
    }
}