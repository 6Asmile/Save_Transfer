package com.jsafe.client;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jsafe.common.*;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import javafx.scene.input.TransferMode;

public class MainController {
    @FXML private ListView<String> serverFileListView;
    @FXML private ProgressBar progressBar;
    @FXML private Label statusLabel;
    @FXML private TextArea logArea;
    @FXML private Label speedLabel;
    @FXML private TextField searchField;
    //  定义一个“底层数据源”，存储从服务器拿到的所有文件名
    private final ObservableList<String> masterData = FXCollections.observableArrayList();
    @FXML
    public void initialize() {
        //  将底层数据包装成“可过滤列表”
        FilteredList<String> filteredData = new FilteredList<>(masterData, p -> true);
        //  监听搜索框的文字变化
        searchField.textProperty().addListener((observable, oldValue, newValue) -> {
            filteredData.setPredicate(fileName -> {
                // 如果搜索框为空，显示所有
                if (newValue == null || newValue.isEmpty()) return true;
                // 忽略大小写匹配
                return fileName.toLowerCase().contains(newValue.toLowerCase());
            });
        });

        //  将 ListView 绑定到过滤后的列表，而不是原始列表
        serverFileListView.setItems(filteredData);


        //  【视觉美化】自定义单元格：显示 Emoji 图标
        serverFileListView.setCellFactory(param -> new ListCell<String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    // 简单的后缀判断逻辑
                    String icon = "📝";  // 默认文件
                    String lower = item.toLowerCase();
                    // 根据包含的后缀名判断
                    if (lower.contains(".jpg") || lower.contains(".png") || lower.contains(".jpeg")) {
                        icon = "📸"; // 相机
                    } else if (lower.contains(".mp4") || lower.contains(".avi") || lower.contains(".mkv")) {
                        icon = "▶";  // 播放按钮
                    } else if (lower.contains(".zip") || lower.contains(".rar") || lower.contains(".7z")) {
                        icon = "📦"; // 压缩包
                    } else if (lower.contains(".pdf") || lower.contains(".doc") || lower.contains(".txt")) {
                        icon = "📝"; // 备忘录/文档
                    } else {
                        icon = "📄"; // 其他普通文件
                    }
                    setText(icon + "  " + item);
                    // 设置样式：让列表看起来不那么拥挤
                    setStyle("-fx-font-size: 14px; -fx-padding: 5; -fx-text-fill: #333;");
                }
            }
        });

        // 2. 【交互升级】右键菜单 (Context Menu)
        ContextMenu contextMenu = new ContextMenu();

        MenuItem downloadItem = new MenuItem("📥 下载");
        downloadItem.setOnAction(e -> onDownloadClick());

        MenuItem shareItem = new MenuItem("🔗 分享给他人");
        shareItem.setOnAction(e -> onShareClick());

        MenuItem deleteItem = new MenuItem("🗑️ 删除");
        deleteItem.setStyle("-fx-text-fill: red;"); // 红色警示
        deleteItem.setOnAction(e -> onDeleteClick());

        contextMenu.getItems().addAll(downloadItem, shareItem, new SeparatorMenuItem(), deleteItem);
        serverFileListView.setContextMenu(contextMenu);
        // 在右键菜单里增加重命名项
        MenuItem renameItem = new MenuItem("✏️ 重命名");
        renameItem.setOnAction(e -> onRenameClick());
        serverFileListView.getContextMenu().getItems().add(2, renameItem); // 插在分享和删除中间
        // 自动刷新一次
        onRefreshClick();
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
        NetworkManager.getInstance().runAsync(() -> {
            try {
                NetworkManager net = NetworkManager.getInstance();
                // 发送请求 (Body 为空即可，填个空字节)
                net.sendPacket(new Packet(Command.REQ_LIST_FILES, new byte[0]));

                // 读取响应
                Packet resp = net.readPacket();

                // 解密数据
                byte[] decrypted = AESUtil.decrypt(resp.getBody());
                String json = new String(decrypted, StandardCharsets.UTF_8);


                // 🌟 核心保护：只有类型匹配才解析为数组
                if (resp.getType() == Command.REQ_LIST_FILES || resp.getType() == (byte)0x41) {
                    String[] files = new Gson().fromJson(json, String[].class);
                    Platform.runLater(() -> {
                        masterData.clear();
                        masterData.addAll(files);

                        statusLabel.setText("列表已刷新 (" + files.length + " 个文件)");
                    });
                } else {
                    // 如果服务端返回了错误信息（通常是 JsonObject）
                    log("刷新异常，收到包类型: " + resp.getType());
                }


            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> {
                    statusLabel.setText("刷新失败");
                    log("刷新失败: " + e.getMessage());
                });
            }
        });
    }

    @FXML
    public void onDownloadClick() {
        // 1. 获取选中的文件名
        String selectedFile = serverFileListView.getSelectionModel().getSelectedItem();
        if (selectedFile == null) return;
        // 去掉可能的后缀 (刚刚上传)，只要文件名
        if(selectedFile.contains(" (")) selectedFile = selectedFile.split(" \\(")[0];

        final String fileName = selectedFile;

        // 2. 选择保存路径
        FileChooser fileChooser = new FileChooser();
        fileChooser.setInitialFileName(fileName);
        File saveFile = fileChooser.showSaveDialog(null);
        if (saveFile == null) return;

        // 3. 开启下载线程
        new Thread(() -> {
            try {
                NetworkManager net = NetworkManager.getInstance();

                // --- 发送下载请求 ---
                String json = String.format("{\"fileName\":\"%s\"}", fileName);
                net.sendEncryptedJson(Command.REQ_DOWNLOAD, json);

                // --- 等待服务端响应 ---
                Packet resp = net.readPacket();
                if (resp.getType() == Command.RESP_DOWNLOAD_START) {
                    // 1. 解析元数据
                    byte[] metaDec = AESUtil.decrypt(resp.getBody());
                    String metaJson = new String(metaDec, StandardCharsets.UTF_8);
                    long totalSize = new Gson().fromJson(metaJson, JsonObject.class).get("size").getAsLong();

                    log("开始下载，文件大小: " + totalSize);

                    // 2. 循环接收数据
                    try (FileOutputStream fos = new FileOutputStream(saveFile)) {
                        long currentSize = 0;
                        while (currentSize < totalSize) {
                            Packet dataPacket = net.readPacket();
                            if (dataPacket.getType() == Command.RESP_DOWNLOAD_DATA) {
                                byte[] chunk = AESUtil.decrypt(dataPacket.getBody());
                                fos.write(chunk);

                                currentSize += chunk.length;

                                // 更新进度条
                                double p = (double) currentSize / totalSize;
                                Platform.runLater(() -> progressBar.setProgress(p));
                            }
                        }
                    }
                    Platform.runLater(() -> {
                        statusLabel.setText("下载完成");
                        progressBar.setProgress(1.0);
                        log("文件已保存至: " + saveFile.getAbsolutePath());
                    });
                }

            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> log("下载出错: " + e.getMessage()));
            }
        }).start();
    }

    @FXML
    public void onShareClick() {
        String selectedFile = serverFileListView.getSelectionModel().getSelectedItem();
        if (selectedFile == null) return;
        // 处理文件名后缀逻辑同上...
        final String fileName = selectedFile.split(" \\(")[0];

        // 弹出一个输入框让用户输对方名字
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("文件分享");
        dialog.setHeaderText("将 " + fileName + " 分享给...");
        dialog.setContentText("请输入对方用户名:");

        dialog.showAndWait().ifPresent(targetUser -> {
            new Thread(() -> {
                try {
                    NetworkManager net = NetworkManager.getInstance();
                    String json = String.format("{\"fileName\":\"%s\", \"targetUser\":\"%s\"}", fileName, targetUser);
                    net.sendEncryptedJson(Command.REQ_SHARE, json);

                    Packet resp = net.readPacket();
                    // ... 解密并 Alert 显示结果 (和注册成功的逻辑一样) ...
                    // 建议 Platform.runLater 弹窗提示 msg

                } catch (Exception e) { e.printStackTrace(); }
            }).start();
        });
    }

    @FXML
    public void onRenameClick() {
        // 1. 获取选中的原始文件名
        String selected = serverFileListView.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        // 2. 🌟 关键修正：selected 是原始数据，不包含 Emoji 图标
        // 我们只需要把上传成功时手动加的后缀去掉即可
        String cleanName = selected;
        if (cleanName.contains(" (刚刚上传)")) {
            cleanName = cleanName.split(" \\(")[0];
        }

        final String oldName = cleanName;

        // 3. 弹出输入对话框
        TextInputDialog dialog = new TextInputDialog(oldName);
        dialog.setTitle("重命名文件");
        dialog.setHeaderText("将文件 \"" + oldName + "\" 重命名为：");
        dialog.setContentText("请输入新文件名:");

        // 4. 处理用户输入
        dialog.showAndWait().ifPresent(newName -> {
            // 如果名字没变或者是空的，直接返回
            if (newName.equals(oldName) || newName.trim().isEmpty()) return;

            NetworkManager.getInstance().runAsync(() -> {
                try {
                    // 5. 封装请求 JSON
                    JsonObject json = new JsonObject();
                    json.addProperty("oldName", oldName);
                    json.addProperty("newName", newName);

                    NetworkManager.getInstance().sendEncryptedJson(Command.REQ_RENAME, json.toString());

                    // 6. 读取服务端响应
                    Packet resp = NetworkManager.getInstance().readPacket();

                    // 🌟 核心保护：判断是否是重命名响应
                    if (resp.getType() == Command.RESP_RENAME) {
                        byte[] data = AESUtil.decrypt(resp.getBody());
                        String respStr = new String(data, StandardCharsets.UTF_8);

                        // 使用 JsonElement 先解析，再判断是对象还是数组
                        com.google.gson.JsonElement element = com.google.gson.JsonParser.parseString(respStr);
                        if (element.isJsonObject()) {
                            JsonObject result = element.getAsJsonObject();
                            Platform.runLater(() -> {
                                if (result.get("code").getAsInt() == 200) {
                                    statusLabel.setText("✅ 重命名成功");
                                    onRefreshClick();
                                }
                                else {
                                    log("❌ 重命名失败：" + result.get("msg").getAsString());
                                }
                            });
                        }
                    }
//
                } catch (Exception e) {
                    e.printStackTrace();
                    Platform.runLater(() -> log("❌ 网络错误：" + e.getMessage()));
                }
            });
        });
    }

    public void onDeleteClick() {
        String item = serverFileListView.getSelectionModel().getSelectedItem();
        if (item == null) return;
        // 去掉可能的后缀 (刚刚上传)
        final String fileName = item.contains(" (") ? item.split(" \\(")[0] : item;

        // 二次确认弹窗
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "确定要永久删除 " + fileName + " 吗？");
        if (alert.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;

        NetworkManager.getInstance().runAsync(() -> { // ✅ 使用线程池
            try {
                NetworkManager net = NetworkManager.getInstance();
                // 发送纯文件名即可
                byte[] body = AESUtil.encrypt(fileName.getBytes(StandardCharsets.UTF_8));
                net.sendPacket(new Packet(Command.REQ_DELETE, body));

                Packet resp = net.readPacket();
                // ... 解密 resp 并判断 code == 200 ...
                // 成功后：
                Platform.runLater(() -> {
                    statusLabel.setText("删除成功");
                    onRefreshClick(); // 刷新列表
                });
            } catch (Exception e) { e.printStackTrace(); }
        });
    }

    private void startUploadTask(File file) {
        NetworkManager.getInstance().runAsync(() -> {
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
                    Platform.runLater(() -> {
                        progressBar.setProgress(1.0);
                        statusLabel.setText("秒传成功");
                        speedLabel.setText("完成");
                        masterData.add(file.getName() + " (刚刚上传)");
                    });
                    return;
                }

                // 4. 开始传输
                try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
                    raf.seek(offset);

                    // 2MB 缓冲区 (注意：如果网络环境差，包太大可能会丢包，建议 64KB-512KB，但内网 2MB 也可以)
                    byte[] buffer = new byte[1024 * 2048];
                    int len;
                    long current = offset;

                    // 📊 速度计算变量初始化
                    long startTime = System.currentTimeMillis();
                    long lastUpdateBytes = current;
                    long lastUpdateTime = startTime;

                    while ((len = raf.read(buffer)) != -1) {
                        // 准备数据
                        byte[] data = (len == buffer.length) ? buffer : Arrays.copyOf(buffer, len);

                        // 修复 Bug：这里只需要加密一次，然后直接传入 Packet
                        byte[] encrypted = AESUtil.encrypt(data);
                        net.sendPacket(new Packet(Command.REQ_UPLOAD_DATA, encrypted));

                        // 更新当前进度
                        current += len;
                        double p = (double) current / file.length();

                        // 📊 实时计算速度 (每 500ms 更新一次 UI，避免界面闪烁)
                        long now = System.currentTimeMillis();
                        if (now - lastUpdateTime > 500) {
                            // 计算时间差(秒)
                            double deltaSeconds = (now - lastUpdateTime) / 1000.0;
                            // 计算字节差
                            long deltaBytes = current - lastUpdateBytes;
                            // 计算速度 (KB/s)
                            double speedKB = (deltaBytes / 1024.0) / deltaSeconds;

                            // 计算剩余时间 (秒)
                            long remainingBytes = file.length() - current;
                            double remainingSeconds = (remainingBytes / 1024.0) / (speedKB <= 0 ? 1 : speedKB); // 防止除以0

                            // 格式化显示的字符串
                            String speedStr;
                            if (speedKB > 1024) {
                                speedStr = String.format("%.1f MB/s", speedKB / 1024);
                            } else {
                                speedStr = String.format("%.0f KB/s", speedKB);
                            }

                            String timeLeft = String.format("剩 %.0f 秒", remainingSeconds);

                            // JavaFX UI 更新必须在 UI 线程
                            Platform.runLater(() -> {
                                progressBar.setProgress(p);
                                // 更新速度标签 (前提是你 FXML 里加了 fx:id="speedLabel")
                                if (speedLabel != null) {
                                    speedLabel.setText(speedStr + " | " + timeLeft);
                                }
                                statusLabel.setText(String.format("上传中 %.1f%%", p * 100));
                            });

                            // 重置计数器
                            lastUpdateBytes = current;
                            lastUpdateTime = now;
                        }
                    }
                }

                log("上传完成！");
                Platform.runLater(() -> {
                    statusLabel.setText("上传成功");
                    if (speedLabel != null) speedLabel.setText("完成");
                    progressBar.setProgress(1.0);
                    Platform.runLater(() -> {
                        statusLabel.setText("上传成功");
                        if (speedLabel != null) speedLabel.setText("完成");
                        progressBar.setProgress(1.0);

                        // 🌟 关键修改：往数据源里添加，而不是往界面列表里添加
                        masterData.add(file.getName() + " (刚刚上传)");
                    });
                });

            } catch (Exception e) {
                e.printStackTrace();
                log("错误: " + e.getMessage());
                Platform.runLater(() -> statusLabel.setText("上传出错"));
            }
        });
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