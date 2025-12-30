package com.jsafe.server;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import java.util.concurrent.atomic.AtomicLong;

public class ServerStatus {
    // 实时在线用户列表 (UI 绑定)
    public static final ObservableList<String> activeUsers = FXCollections.observableArrayList();

    // 累计传输字节数 (用于计算网速)
    public static final AtomicLong totalBytesExchanged = new AtomicLong(0);

    // 记录日志的方法
    public static void addLog(String msg) {
        // 后续会在 Controller 里绑定
    }
}