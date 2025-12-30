package com.jsafe.client;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.jsafe.common.AESUtil;
import com.jsafe.common.Command;
import com.jsafe.common.Packet;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.nio.charset.StandardCharsets;

public class LoginController {
    @FXML
    private TextField usernameField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private Label messageLabel;
    @FXML
    private TextField ipField; // 绑定控件
    @FXML
    protected void onLoginButtonClick() {
        String u = usernameField.getText();
        String p = passwordField.getText();
        String ip = ipField.getText(); // 获取用户输入的 IP

        if (u.isEmpty() || p.isEmpty()) {
            messageLabel.setText("请输入用户名和密码");
            return;
        }

        // 为了不卡死界面，网络请求要在子线程做
        new Thread(() -> {
            try {
                // 1. 获取网络实例并连接 (如果还没连)
                NetworkManager net = NetworkManager.getInstance();
//                net.connect("127.0.0.1", 8888);
                net.connect(ip, 8888); // 使用动态 IP
                // 2. 发送登录包
                String loginJson = String.format("{\"u\":\"%s\", \"p\":\"%s\"}", u, p);
                net.sendEncryptedJson(Command.REQ_AUTH, loginJson);

                // 3. 读取响应
                Packet resp = net.readPacket();

                // 4. 处理结果 (回到 UI 线程更新界面)
                Platform.runLater(() -> {
                    try {
                        if (resp.getType() == Command.RESP_AUTH) {
                            byte[] decrypted = AESUtil.decrypt(resp.getBody());
                            String jsonStr = new String(decrypted, StandardCharsets.UTF_8);

                            JsonObject jsonObj = new Gson().fromJson(jsonStr, JsonObject.class);
                            int code = jsonObj.get("code").getAsInt();

                            if (code == 200) {
                                messageLabel.setStyle("-fx-text-fill: green;");
                                messageLabel.setText("✅ 登录成功！正在跳转...");
                                // TODO: 这里下一步要跳转到主界面 (MainView)
                                // --- 新增跳转逻辑 ---
                                // 1. 关闭登录窗口
                                Stage loginStage = (Stage) usernameField.getScene().getWindow();
                                loginStage.close();

                                // 2. 打开主窗口
                                try {
                                    FXMLLoader loader = new FXMLLoader(getClass().getResource("/main-view.fxml"));
                                    Stage mainStage = new Stage();
                                    mainStage.setTitle("J-SafeTransfer - 文件传输工作台");
                                    mainStage.setScene(new javafx.scene.Scene(loader.load()));
                                    mainStage.show();
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                                System.out.println("Login Success: " + jsonStr);
                            } else {
                                messageLabel.setStyle("-fx-text-fill: red;");
                                messageLabel.setText("登录失败: " + jsonObj.get("msg").getAsString());
                            }
                        }
                    } catch (Exception e) {
                        messageLabel.setText("解密失败");
                        e.printStackTrace();
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> messageLabel.setText("连接错误: " + e.getMessage()));
            }
        }).start();
    }
    @FXML
    public void initialize() {
        // 优先填入配置文件里的 IP
        if (!NetworkManager.DEFAULT_SERVER_IP.isEmpty()) {
            ipField.setText(NetworkManager.DEFAULT_SERVER_IP);
        }
        // 界面打开时，自动在后台搜一下
        new Thread(() -> {
            String ip = UDPClient.searchServer();
            if (ip != null) {
                // 回到 UI 线程更新输入框
                Platform.runLater(() -> {
                    ipField.setText(ip); // 自动填入 IP
                    // 甚至可以自动把“连接”按钮变绿，或者弹个提示
                });
            }
        }).start();
    }
}