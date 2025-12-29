package com.jsafe.client;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.jsafe.common.*;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import javafx.scene.input.TransferMode;

public class MainController {
    @FXML private ListView<String> serverFileListView;
    @FXML private ProgressBar progressBar;
    @FXML private Label statusLabel;
    @FXML private TextArea logArea;

    @FXML
    public void initialize() {
        // 窗口启动时自动刷新一次
        onRefreshClick();

        // 开启拖拽功能 (下一步的代码)
        setupDragAndDrop();
    }

    // 点击上传按钮
    @FXML
    public void onUploadClick() {
        // 1. 选择文件
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("选择要上传的文件");
        File file = fileChooser.showOpenDialog(null);

        if (file != null) {
            startUploadTask(file);
        }
    }

    @FXML
    public void onRefreshClick() {
        new Thread(() -> {
            try {
                NetworkManager net = NetworkManager.getInstance();
                // 发送请求 (Body 为空即可，填个空字节)
                net.sendPacket(new Packet(Command.REQ_LIST_FILES, new byte[0]));

                // 读取响应
                Packet resp = net.readPacket();

                // ⚠️ 注意：如果你在 Command 里加了 RESP_LIST_FILES，这里要判断 resp.getType() == Command.RESP_LIST_FILES

                // 解密
                byte[] decrypted = AESUtil.decrypt(resp.getBody());
                String json = new String(decrypted, StandardCharsets.UTF_8);

                // 解析 JSON 数组
                String[] files = new Gson().fromJson(json, String[].class);

                // 更新 UI (必须在主线程)
                Platform.runLater(() -> {
                    serverFileListView.getItems().clear();
                    serverFileListView.getItems().addAll(files);
                    statusLabel.setText("列表已更新");
                });

            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> log("刷新失败: " + e.getMessage()));
            }
        }).start();
    }

    // 开启后台线程上传，防止界面卡死
    private void startUploadTask(File file) {
        new Thread(() -> {
            try {
                log("准备上传: " + file.getName());
                NetworkManager net = NetworkManager.getInstance();

                // 2. 计算 MD5
                Platform.runLater(() -> statusLabel.setText("正在计算 MD5..."));
                String md5 = HashUtil.getFileMD5(file);

                // 3. 询问断点
                String checkJson = String.format("{\"md5\":\"%s\", \"fileName\":\"%s\", \"size\":%d}",
                        md5, file.getName(), file.length());
                net.sendEncryptedJson(Command.REQ_CHECK_RESUME, checkJson);

                Packet resp = net.readPacket();
                String respStr = new String(AESUtil.decrypt(resp.getBody()), StandardCharsets.UTF_8);
                long offset = JsonParser.parseString(respStr).getAsJsonObject().get("offset").getAsLong();

                log("服务器断点位置: " + offset);
                if (offset >= file.length()) {
                    log("文件已存在，秒传成功！");
                    Platform.runLater(() -> progressBar.setProgress(1.0));
                    return;
                }

                // 4. 开始传输
                try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
                    raf.seek(offset);
                    byte[] buffer = new byte[1024*2048];  //2MB
                    int len;
                    long current = offset;

                    while ((len = raf.read(buffer)) != -1) {
                        // 准备数据
                        byte[] data = (len == buffer.length) ? buffer : Arrays.copyOf(buffer, len);
                        byte[] encrypted = AESUtil.encrypt(data);

                        net.sendPacket(new Packet(Command.REQ_UPLOAD_DATA, encrypted));

                        // 更新进度条
                        current += len;
                        double p = (double) current / file.length();

                        // JavaFX UI 更新必须在 UI 线程
                        Platform.runLater(() -> progressBar.setProgress(p));
                    }
                }

                log("上传完成！");
                Platform.runLater(() -> {
                    statusLabel.setText("上传成功");
                    serverFileListView.getItems().add(file.getName() + " (刚刚上传)");
                });

            } catch (Exception e) {
                e.printStackTrace();
                log("错误: " + e.getMessage());
            }
        }).start();
    }

    private void log(String msg) {
        Platform.runLater(() -> logArea.appendText(msg + "\n"));
    }

    private void setupDragAndDrop() {
        // 1. 当文件被拖到列表区域上方时
        serverFileListView.setOnDragOver(event -> {
            if (event.getDragboard().hasFiles()) {
                // 允许复制模式 (鼠标指针变号)
                event.acceptTransferModes(TransferMode.COPY);
            }
            event.consume();
        });

        // 2. 当鼠标松开 (放下文件) 时
        serverFileListView.setOnDragDropped(event -> {
            var db = event.getDragboard();
            if (db.hasFiles()) {
                // 获取拖入的文件（支持多个，这里演示取第一个）
                File file = db.getFiles().get(0);
                log("检测到拖拽文件: " + file.getName());

                // 直接调用之前的上传逻辑！
                startUploadTask(file);

                event.setDropCompleted(true);
            } else {
                event.setDropCompleted(false);
            }
            event.consume();
        });
    }
}