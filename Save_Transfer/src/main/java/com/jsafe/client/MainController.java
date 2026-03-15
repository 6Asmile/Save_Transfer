package com.jsafe.client;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.jsafe.common.*;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.scene.input.TransferMode;

import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class MainController {
    @FXML private ListView<String> serverFileListView;
    @FXML private ProgressBar progressBar;
    @FXML private Label statusLabel;
    @FXML private TextArea logArea;
    @FXML private Label speedLabel;
    @FXML private TextField searchField;

    private final ObservableList<String> masterData = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        FilteredList<String> filteredData = new FilteredList<>(masterData, p -> true);
        searchField.textProperty().addListener((observable, oldValue, newValue) -> {
            filteredData.setPredicate(fileName -> {
                if (newValue == null || newValue.isEmpty()) return true;
                return fileName.toLowerCase().contains(newValue.toLowerCase());
            });
        });

        serverFileListView.setItems(filteredData);

        serverFileListView.setCellFactory(param -> new ListCell<String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    String icon = "📄";
                    String lower = item.toLowerCase();
                    if (lower.contains(".jpg") || lower.contains(".png") || lower.contains(".jpeg")) {
                        icon = "📸";
                    } else if (lower.contains(".mp4") || lower.contains(".avi") || lower.contains(".mkv")) {
                        icon = "▶";
                    } else if (lower.contains(".zip") || lower.contains(".rar") || lower.contains(".7z")) {
                        icon = "📦";
                    } else if (lower.contains(".pdf") || lower.contains(".doc") || lower.contains(".txt")) {
                        icon = "📝";
                    }
                    setText(icon + "  " + item);
                    setStyle("-fx-font-size: 14px; -fx-padding: 5; -fx-text-fill: #333;");
                }
            }
        });

        ContextMenu contextMenu = new ContextMenu();
        MenuItem downloadItem = new MenuItem("📥 下载");
        downloadItem.setOnAction(e -> onDownloadClick());

        MenuItem shareItem = new MenuItem("🔗 分享给他人");
        shareItem.setOnAction(e -> onShareClick());

        MenuItem deleteItem = new MenuItem("🗑️ 删除");
        deleteItem.setStyle("-fx-text-fill: red;");
        deleteItem.setOnAction(e -> onDeleteClick());

        MenuItem renameItem = new MenuItem("✏️ 重命名");
        renameItem.setOnAction(e -> onRenameClick());

        contextMenu.getItems().addAll(downloadItem, shareItem, new SeparatorMenuItem(), renameItem, deleteItem);
        serverFileListView.setContextMenu(contextMenu);

        onRefreshClick();
        setupDragAndDrop();
    }

    @FXML
    public void onUploadClick() {
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
                net.sendPacket(new Packet(Command.REQ_LIST_FILES, new byte[0]));

                Packet resp = net.readPacket();

                // ✅ 修改：传入 SessionKey 解密
                byte[] decrypted = AESUtil.decrypt(resp.getBody(), net.getSessionKey());
                String json = new String(decrypted, StandardCharsets.UTF_8);

                if (resp.getType() == Command.REQ_LIST_FILES || resp.getType() == (byte)0x41) {
                    String[] files = new Gson().fromJson(json, String[].class);
                    Platform.runLater(() -> {
                        masterData.clear();
                        masterData.addAll(files);
                        statusLabel.setText("列表已刷新 (" + files.length + " 个文件)");
                    });
                } else {
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
        String selectedFile = serverFileListView.getSelectionModel().getSelectedItem();
        if (selectedFile == null) return;
        if(selectedFile.contains(" (")) selectedFile = selectedFile.split(" \\(")[0];

        final String fileName = selectedFile;

        FileChooser fileChooser = new FileChooser();
        fileChooser.setInitialFileName(fileName);
        File saveFile = fileChooser.showSaveDialog(null);
        if (saveFile == null) return;

        NetworkManager.getInstance().runAsync(() -> {
            try {
                NetworkManager net = NetworkManager.getInstance();
                String json = String.format("{\"fileName\":\"%s\"}", fileName);
                net.sendEncryptedJson(Command.REQ_DOWNLOAD, json);

                Packet resp = net.readPacket();
                if (resp.getType() == Command.RESP_DOWNLOAD_START) {
                    // ✅ 修改：传入 SessionKey 解密
                    byte[] metaDec = AESUtil.decrypt(resp.getBody(), net.getSessionKey());
                    String metaJson = new String(metaDec, StandardCharsets.UTF_8);
                    long totalSize = new Gson().fromJson(metaJson, JsonObject.class).get("size").getAsLong();

                    log("开始下载，文件大小: " + totalSize);

                    try (FileOutputStream fos = new FileOutputStream(saveFile)) {
                        long currentSize = 0;
                        while (currentSize < totalSize) {
                            Packet dataPacket = net.readPacket();
                            if (dataPacket.getType() == Command.RESP_DOWNLOAD_DATA) {
                                // ✅ 修改：传入 SessionKey 解密
                                byte[] chunk = AESUtil.decrypt(dataPacket.getBody(), net.getSessionKey());
                                fos.write(chunk);
                                currentSize += chunk.length;
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
        });
    }

    @FXML
    public void onShareClick() {
        String selectedFile = serverFileListView.getSelectionModel().getSelectedItem();
        if (selectedFile == null) return;
        final String fileName = selectedFile.split(" \\(")[0];

        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("文件分享");
        dialog.setHeaderText("将 " + fileName + " 分享给...");
        dialog.setContentText("请输入对方用户名:");

        dialog.showAndWait().ifPresent(targetUser -> {
            NetworkManager.getInstance().runAsync(() -> {
                try {
                    NetworkManager net = NetworkManager.getInstance();
                    String json = String.format("{\"fileName\":\"%s\", \"targetUser\":\"%s\"}", fileName, targetUser);
                    net.sendEncryptedJson(Command.REQ_SHARE, json);

                    Packet resp = net.readPacket();
                    // 这里可以加解密逻辑处理响应
                    Platform.runLater(() -> log("分享请求已发送"));
                } catch (Exception e) { e.printStackTrace(); }
            });
        });
    }

    @FXML
    public void onRenameClick() {
        String selected = serverFileListView.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        String cleanName = selected;
        if (cleanName.contains(" (刚刚上传)")) {
            cleanName = cleanName.split(" \\(")[0];
        }
        final String oldName = cleanName;

        TextInputDialog dialog = new TextInputDialog(oldName);
        dialog.setTitle("重命名文件");
        dialog.setHeaderText("将文件 \"" + oldName + "\" 重命名为：");
        dialog.setContentText("请输入新文件名:");

        dialog.showAndWait().ifPresent(newName -> {
            if (newName.equals(oldName) || newName.trim().isEmpty()) return;

            NetworkManager.getInstance().runAsync(() -> {
                try {
                    JsonObject json = new JsonObject();
                    json.addProperty("oldName", oldName);
                    json.addProperty("newName", newName);

                    NetworkManager.getInstance().sendEncryptedJson(Command.REQ_RENAME, json.toString());

                    Packet resp = NetworkManager.getInstance().readPacket();
                    if (resp.getType() == Command.RESP_RENAME) {
                        // ✅ 修改：传入 SessionKey 解密
                        byte[] data = AESUtil.decrypt(resp.getBody(), NetworkManager.getInstance().getSessionKey());
                        String respStr = new String(data, StandardCharsets.UTF_8);

                        JsonObject result = new Gson().fromJson(respStr, JsonObject.class);
                        Platform.runLater(() -> {
                            if (result.get("code").getAsInt() == 200) {
                                statusLabel.setText("✅ 重命名成功");
                                onRefreshClick();
                            } else {
                                log("❌ 重命名失败：" + result.get("msg").getAsString());
                            }
                        });
                    }
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
        final String fileName = item.contains(" (") ? item.split(" \\(")[0] : item;

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "确定要永久删除 " + fileName + " 吗？");
        if (alert.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;

        NetworkManager.getInstance().runAsync(() -> {
            try {
                NetworkManager net = NetworkManager.getInstance();
                // ✅ 修改：传入 SessionKey 加密
                byte[] body = AESUtil.encrypt(fileName.getBytes(StandardCharsets.UTF_8), net.getSessionKey());
                net.sendPacket(new Packet(Command.REQ_DELETE, body));

                Packet resp = net.readPacket();
                Platform.runLater(() -> {
                    statusLabel.setText("删除成功");
                    onRefreshClick();
                });
            } catch (Exception e) { e.printStackTrace(); }
        });
    }

    private void startUploadTask(File file) {
        NetworkManager.getInstance().runAsync(() -> {
            try {
                log("准备上传: " + file.getName());
                NetworkManager net = NetworkManager.getInstance();

                Platform.runLater(() -> statusLabel.setText("正在计算 MD5..."));
                String md5 = HashUtil.getFileMD5(file);

                String checkJson = String.format("{\"md5\":\"%s\", \"fileName\":\"%s\", \"size\":%d}",
                        md5, file.getName(), file.length());
                net.sendEncryptedJson(Command.REQ_CHECK_RESUME, checkJson);

                Packet resp = net.readPacket();
                // ✅ 修改：传入 SessionKey 解密
                String respStr = new String(AESUtil.decrypt(resp.getBody(), net.getSessionKey()), StandardCharsets.UTF_8);
                long offset = new Gson().fromJson(respStr, JsonObject.class).get("offset").getAsLong();

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

                try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
                    raf.seek(offset);
                    byte[] buffer = new byte[1024 * 2048];
                    int len;
                    long current = offset;
                    long startTime = System.currentTimeMillis();
                    long lastUpdateBytes = current;
                    long lastUpdateTime = startTime;

                    while ((len = raf.read(buffer)) != -1) {
                        byte[] data = (len == buffer.length) ? buffer : Arrays.copyOf(buffer, len);
                        // ✅ 修改：传入 SessionKey 加密
                        byte[] encrypted = AESUtil.encrypt(data, net.getSessionKey());
                        net.sendPacket(new Packet(Command.REQ_UPLOAD_DATA, encrypted));

                        current += len;
                        double p = (double) current / file.length();
                        long now = System.currentTimeMillis();
                        if (now - lastUpdateTime > 500) {
                            double deltaSeconds = (now - lastUpdateTime) / 1000.0;
                            long deltaBytes = current - lastUpdateBytes;
                            double speedKB = (deltaBytes / 1024.0) / deltaSeconds;
                            long remainingBytes = file.length() - current;
                            double remainingSeconds = (remainingBytes / 1024.0) / (speedKB <= 0 ? 1 : speedKB);

                            String speedStr = speedKB > 1024 ? String.format("%.1f MB/s", speedKB / 1024) : String.format("%.0f KB/s", speedKB);
                            String timeLeft = String.format("剩 %.0f 秒", remainingSeconds);

                            Platform.runLater(() -> {
                                progressBar.setProgress(p);
                                if (speedLabel != null) speedLabel.setText(speedStr + " | " + timeLeft);
                                statusLabel.setText(String.format("上传中 %.1f%%", p * 100));
                            });
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
                    masterData.add(file.getName() + " (刚刚上传)");
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
        serverFileListView.setOnDragOver(event -> {
            if (event.getDragboard().hasFiles()) event.acceptTransferModes(TransferMode.COPY);
            event.consume();
        });

        serverFileListView.setOnDragDropped(event -> {
            var db = event.getDragboard();
            if (db.hasFiles()) {
                File file = db.getFiles().get(0);
                log("检测到拖拽文件: " + file.getName());
                startUploadTask(file);
                event.setDropCompleted(true);
            } else {
                event.setDropCompleted(false);
            }
            event.consume();
        });
    }
}