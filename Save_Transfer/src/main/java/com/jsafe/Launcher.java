package com.jsafe;

import com.jsafe.client.NetworkManager;
import com.jsafe.server.ServerMain;
import javafx.application.Platform;

import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;

public class Launcher {

    // 定义配置文件名
    private static final String SERVER_CONFIG = "server.properties";
    private static final String CLIENT_CONFIG = "client.properties";

    public static void main(String[] args) {
        // 1. 检查是否存在服务端配置文件
        File serverFile = new File(SERVER_CONFIG);
        if (serverFile.exists()) {
            startServerMode(serverFile);
        } else {
            startClientMode();
        }
    }

    // --- 启动服务端模式 ---
    private static void startServerMode(File configFile) {
        System.out.println(" 检测到 " + SERVER_CONFIG + "，正在以 [服务端模式] 启动...");
        try {
            // 读取配置 (端口等)
            Properties props = new Properties();
            try (FileInputStream fis = new FileInputStream(configFile)) {
                props.load(fis);
            }

            // 这里你可以把 props 传给 ServerMain，或者目前先只用来做开关
            // 比如读取端口: int port = Integer.parseInt(props.getProperty("port", "8888"));

            // 启动服务端逻辑
            ServerMain.main(new String[]{});

        } catch (Exception e) {
            e.printStackTrace();
            System.out.println(" 服务端启动失败，按回车退出...");
            try { System.in.read(); } catch (Exception ignored) {}
        }
    }

    // --- 启动客户端模式 ---
    private static void startClientMode() {
        System.out.println(" 未检测到服务端配置，默认以 [客户端模式] 启动...");

        // 尝试读取 client.properties (如果有的话，用于自动填IP)
        File clientFile = new File(CLIENT_CONFIG);
        if (clientFile.exists()) {
            try (FileInputStream fis = new FileInputStream(clientFile)) {
                Properties props = new Properties();
                props.load(fis);
                // 将读取到的 IP 存入 NetworkManager 的静态变量，供 LoginController 使用
                String autoIp = props.getProperty("server.ip");
                if (autoIp != null && !autoIp.isEmpty()) {
                    System.out.println(" 读取到预设IP: " + autoIp);
                    // 💡 你需要在 NetworkManager 加一个静态变量来存这个值
                    NetworkManager.DEFAULT_SERVER_IP = autoIp;
                }
            } catch (Exception e) {
                System.out.println(" 客户端配置读取失败，将使用默认设置");
            }
        }

        // 启动 JavaFX 界面
        App.main(new String[]{});
    }
}