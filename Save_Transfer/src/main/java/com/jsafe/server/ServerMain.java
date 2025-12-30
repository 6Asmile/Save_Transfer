package com.jsafe.server;

import com.jsafe.common.Command;
import com.jsafe.common.Packet;
import com.jsafe.common.AESUtil;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.ArrayList;
import java.util.List;

public class ServerMain extends Application { // 1. 继承 Application 实现可视化
    private static final int PORT = 8888;
    private static final ExecutorService threadPool = Executors.newFixedThreadPool(10);
    private static ServerController uiController; // 引用 UI 控制器

    // JavaFX 启动入口
    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/server-view.fxml"));
        stage.setScene(new Scene(loader.load()));
        stage.setTitle("J-SafeTransfer - 服务端可视化监控大屏");
        uiController = loader.getController();

        // 窗口关闭时关闭整个程序
        stage.setOnCloseRequest(e -> System.exit(0));
        stage.show();

        // 2. 核心逻辑：在后台线程启动 Socket 服务器，防止阻塞 UI
        new Thread(this::startSocketServer).start();
    }

    // 封装原本的服务器启动代码
    private void startSocketServer() {
        UDPProvider.start(); // 启动 UDP 广播
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            log("✅ Server started on port " + PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                log("📡 New connection: " + clientSocket.getInetAddress());

                // 🌟 使用线程池处理客户端逻辑
                threadPool.execute(new ClientHandler(clientSocket));
            }
        } catch (IOException e) {
            log("❌ Server Error: " + e.getMessage());
        }
    }

    // 静态日志方法，方便各个线程调用并更新到 UI
    public static void log(String msg) {
        System.out.println(msg);
        if (uiController != null) {
            Platform.runLater(() -> uiController.appendLog(msg));
        }
    }

    public static void main(String[] args) {
        launch(args); // 启动 JavaFX
    }

    // --- 内部类：处理单个客户端逻辑 ---
    static class ClientHandler implements Runnable {
        private Socket socket;
        private String currentFileMD5;
        private long currentFileSize;
        private String currentFileName;
        private String currentUsername;
        private int currentUserId;

        private static final String TEMP_DIR = "./server_temp";
        private static final String STORAGE_DIR = "./server_storage";

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            new File(TEMP_DIR).mkdirs();
            new File(STORAGE_DIR).mkdirs();

            try (DataInputStream dis = new DataInputStream(socket.getInputStream());
                 DataOutputStream dos = new DataOutputStream(socket.getOutputStream())) {

                while (true) {
                    Packet packet = Packet.read(dis);

                    switch (packet.getType()) {
                        case Command.REQ_AUTH -> handleLogin(packet, dos);
                        case Command.REQ_REGISTER -> handleRegister(packet, dos);
                        case Command.REQ_CHECK_RESUME -> handleCheckResume(packet, dos);
                        case Command.REQ_UPLOAD_DATA -> handleUploadData(packet, dos);
                        case Command.REQ_LIST_FILES -> handleListFiles(dos);
                        case Command.REQ_DOWNLOAD -> handleDownload(packet, dos);
                        case Command.REQ_SHARE -> handleShare(packet, dos);
                        case Command.REQ_DELETE -> handleDelete(packet, dos);
                        case Command.REQ_RENAME -> handleRename(packet, dos);
                        default -> log("⚠️ 未知指令: " + packet.getType());
                    }
                }
            } catch (IOException e) {
                log("🔌 Client disconnected: " + (currentUsername != null ? currentUsername : socket.getInetAddress()));
                // 从 UI 在线用户列表移除
                if (currentUsername != null) {
                    Platform.runLater(() -> ServerStatus.activeUsers.remove(currentUsername));
                }
            }
        }

        private void handleLogin(Packet req, DataOutputStream dos) throws IOException {
            try {
                byte[] decryptedBody = AESUtil.decrypt(req.getBody());
                String jsonStr = new String(decryptedBody, StandardCharsets.UTF_8);

                JsonObject jsonObj = new Gson().fromJson(jsonStr, JsonObject.class);
                String u = jsonObj.get("u").getAsString();
                String p = jsonObj.get("p").getAsString();

                UserDAO userDAO = new UserDAO();
                boolean isSuccess = userDAO.login(u, p);

                String respJson;
                if (isSuccess) {
                    this.currentUserId = userDAO.getUserId(u);
                    this.currentUsername = u;
                    respJson = "{\"code\":200, \"msg\":\"Login Success\"}";

                    log("👤 用户 " + u + " 登录成功");
                    // 🌟 上报到 UI 在线用户列表
                    Platform.runLater(() -> {
                        if (!ServerStatus.activeUsers.contains(u)) {
                            ServerStatus.activeUsers.add(u);
                        }
                    });
                } else {
                    respJson = "{\"code\":401, \"msg\":\"Login Failed\"}";
                }

                byte[] encryptedResp = AESUtil.encrypt(respJson.getBytes(StandardCharsets.UTF_8));
                new Packet(Command.RESP_AUTH, encryptedResp).write(dos);

            } catch (Exception e) {
                log("❌ Login Error: " + e.getMessage());
            }
        }

        private void handleUploadData(Packet packet, DataOutputStream dos) throws IOException {
            try {
                byte[] fileChunk = AESUtil.decrypt(packet.getBody());

                // 🌟 核心：上报流量数据到统计原子量
                ServerStatus.totalBytesExchanged.addAndGet(fileChunk.length);

                File tempFile = new File(TEMP_DIR, currentFileMD5 + ".temp");
                try (RandomAccessFile raf = new RandomAccessFile(tempFile, "rw")) {
                    raf.seek(raf.length());
                    raf.write(fileChunk);
                }

                if (tempFile.length() >= currentFileSize) {
                    File userDir = new File(STORAGE_DIR, currentUsername);
                    if (!userDir.exists()) userDir.mkdirs();

                    File destFile = new File(userDir, currentFileName);
                    if (destFile.exists()) destFile.delete();

                    if (tempFile.renameTo(destFile)) {
                        log("📦 文件上传完成: " + currentFileName + " (By " + currentUsername + ")");
                        new FileDAO().addFile(currentFileName, destFile.getAbsolutePath(), currentFileSize, currentFileMD5, currentUserId);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private void handleDownload(Packet packet, DataOutputStream dos) {
            try {
                byte[] decrypted = AESUtil.decrypt(packet.getBody());
                String fileName = new Gson().fromJson(new String(decrypted, StandardCharsets.UTF_8), JsonObject.class).get("fileName").getAsString();

                File file = new File(new File(STORAGE_DIR, currentUsername), fileName);
                if (!file.exists()) return;

                // 发送元数据
                String metaJson = String.format("{\"fileName\":\"%s\", \"size\":%d}", file.getName(), file.length());
                new Packet(Command.RESP_DOWNLOAD_START, AESUtil.encrypt(metaJson.getBytes(StandardCharsets.UTF_8))).write(dos);

                // 循环发送文件块
                try (FileInputStream fis = new FileInputStream(file)) {
                    byte[] buffer = new byte[64 * 1024]; // 64KB 块
                    int len;
                    while ((len = fis.read(buffer)) != -1) {
                        byte[] data = (len == buffer.length) ? buffer : Arrays.copyOf(buffer, len);
                        byte[] encrypted = AESUtil.encrypt(data);

                        // 🌟 上报流量：上报加密前的原始数据大小
                        ServerStatus.totalBytesExchanged.addAndGet(len);

                        new Packet(Command.RESP_DOWNLOAD_DATA, encrypted).write(dos);
                    }
                }
                log("📤 文件下载完成: " + fileName + " (To " + currentUsername + ")");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // --- 以下为原有功能的完整保留 ---

        private void handleCheckResume(Packet packet, DataOutputStream dos) throws IOException {
            try {
                String jsonStr = new String(AESUtil.decrypt(packet.getBody()), StandardCharsets.UTF_8);
                JsonObject req = new Gson().fromJson(jsonStr, JsonObject.class);
                this.currentFileMD5 = req.get("md5").getAsString();
                this.currentFileName = req.get("fileName").getAsString();
                this.currentFileSize = req.get("size").getAsLong();

                File tempFile = new File(TEMP_DIR, currentFileMD5 + ".temp");
                long offset = tempFile.exists() ? tempFile.length() : 0;

                String respJson = "{\"offset\":" + offset + "}";
                new Packet(Command.RESP_CHECK_RESUME, AESUtil.encrypt(respJson.getBytes(StandardCharsets.UTF_8))).write(dos);
            } catch (Exception e) { e.printStackTrace(); }
        }

        private void handleListFiles(DataOutputStream dos) throws IOException {
            try {
                File dir = new File(STORAGE_DIR, currentUsername);
                if (!dir.exists()) dir.mkdirs();
                String[] files = dir.list();
                String json = new Gson().toJson(files != null ? files : new String[0]);
                new Packet(Command.REQ_LIST_FILES, AESUtil.encrypt(json.getBytes(StandardCharsets.UTF_8))).write(dos);
            } catch (Exception e) { e.printStackTrace(); }
        }

        private void handleRegister(Packet packet, DataOutputStream dos) throws IOException {
            try {
                byte[] decrypted = AESUtil.decrypt(packet.getBody());
                JsonObject req = new Gson().fromJson(new String(decrypted, StandardCharsets.UTF_8), JsonObject.class);
                boolean success = new UserDAO().register(req.get("u").getAsString(), req.get("p").getAsString());
                String respJson = String.format("{\"code\":%d, \"msg\":\"%s\"}", success?200:409, success?"OK":"Failed");
                new Packet(Command.RESP_REGISTER, AESUtil.encrypt(respJson.getBytes(StandardCharsets.UTF_8))).write(dos);
                log("📝 新用户注册: " + req.get("u").getAsString());
            } catch (Exception e) { e.printStackTrace(); }
        }

        private void handleShare(Packet packet, DataOutputStream dos) {
            try {
                JsonObject json = new Gson().fromJson(new String(AESUtil.decrypt(packet.getBody()), StandardCharsets.UTF_8), JsonObject.class);
                String fileName = json.get("fileName").getAsString();
                String targetUser = json.get("targetUser").getAsString();

                File srcFile = new File(new File(STORAGE_DIR, currentUsername), fileName);
                File targetDir = new File(STORAGE_DIR, targetUser);

                if (srcFile.exists() && targetDir.exists()) {
                    java.nio.file.Files.copy(srcFile.toPath(), new File(targetDir, fileName).toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    log("🔗 分享文件: " + fileName + " 从 " + currentUsername + " 到 " + targetUser);
                    new Packet(Command.RESP_SHARE, AESUtil.encrypt("{\"code\":200}".getBytes())).write(dos);
                }
            } catch (Exception e) { e.printStackTrace(); }
        }

        private void handleDelete(Packet packet, DataOutputStream dos) {
            try {
                String fileName = new String(AESUtil.decrypt(packet.getBody()), StandardCharsets.UTF_8);
                File file = new File(new File(STORAGE_DIR, currentUsername), fileName);
                if (file.exists() && file.delete()) {
                    new FileDAO().deleteFileRecord(fileName, currentUserId);
                    log("🗑️ 文件删除: " + fileName);
                    new Packet(Command.RESP_DELETE, AESUtil.encrypt("{\"code\":200}".getBytes())).write(dos);
                }
            } catch (Exception e) { e.printStackTrace(); }
        }

        private void handleRename(Packet packet, DataOutputStream dos) {
            try {
                JsonObject json = new Gson().fromJson(new String(AESUtil.decrypt(packet.getBody()), StandardCharsets.UTF_8), JsonObject.class);
                String oldName = json.get("oldName").getAsString();
                String newName = json.get("newName").getAsString();
                File oldFile = new File(new File(STORAGE_DIR, currentUsername), oldName);
                File newFile = new File(new File(STORAGE_DIR, currentUsername), newName);
                if (oldFile.renameTo(newFile)) {
                    new FileDAO().updateFileName(oldName, newName, currentUserId);
                    log("✏️ 文件重命名: " + oldName + " -> " + newName);
                    new Packet(Command.RESP_RENAME, AESUtil.encrypt("{\"code\":200}".getBytes())).write(dos);
                }
            } catch (Exception e) { e.printStackTrace(); }
        }
    }
}