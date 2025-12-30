package com.jsafe.server;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.util.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class ServerController {
    @FXML private ListView<String> userListView;
    @FXML private LineChart<String, Number> speedChart;
    @FXML private TextArea logArea;

    private XYChart.Series<String, Number> speedSeries = new XYChart.Series<>();
    private long lastBytes = 0;

    @FXML
    public void initialize() {
        // 1. 绑定用户列表
        userListView.setItems(ServerStatus.activeUsers);

        // 2. 初始化折线图
        speedChart.getData().add(speedSeries);

        // 3. 启动定时器：每秒计算一次速度并更新图表
        Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> updateDashboard()));
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();
    }

    private void updateDashboard() {
        long currentBytes = ServerStatus.totalBytesExchanged.get();
        double speedMB = (currentBytes - lastBytes) / 1024.0 / 1024.0;
        lastBytes = currentBytes;

        // 获取当前时间
        String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));

        // 更新折线图
        speedSeries.getData().add(new XYChart.Data<>(time, speedMB));
        if (speedSeries.getData().size() > 20) speedSeries.getData().remove(0); // 只保留最近20个点
    }

    // 暴露给外部打印日志的方法
    public void appendLog(String msg) {
        String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        logArea.appendText("[" + time + "] " + msg + "\n");
    }
}