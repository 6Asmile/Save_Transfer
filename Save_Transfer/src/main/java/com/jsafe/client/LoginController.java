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

        // ：使用 NetworkManager 的线程池，而不是 new Thread
        NetworkManager.getInstance().runAsync(() -> {
            try {
                // 1. 获取网络实例并连接
                NetworkManager net = NetworkManager.getInstance();
                net.connect(ip, 8888);

                // 执行 RSA + AES 安全握手 (必须在发数据前调用)
                net.performSecureHandshake();

                //  发送登录包 (内部会自动使用握手后的 SessionKey 加密)
                String loginJson = String.format("{\"u\":\"%s\", \"p\":\"%s\"}", u, p);
                net.sendEncryptedJson(Command.REQ_AUTH, loginJson);

                // 3. 读取响应
                Packet resp = net.readPacket();

                // 4. 处理结果 (回到 UI 线程更新界面)
                Platform.runLater(() -> {
                    try {
                        if (resp.getType() == Command.RESP_AUTH) {
                            // 解密时传入动态 SessionKey
                            byte[] decrypted = AESUtil.decrypt(resp.getBody(), net.getSessionKey());
                            String jsonStr = new String(decrypted, StandardCharsets.UTF_8);

                            JsonObject jsonObj = new Gson().fromJson(jsonStr, JsonObject.class);
                            int code = jsonObj.get("code").getAsInt();

                            if (code == 200) {
                                messageLabel.setStyle("-fx-text-fill: green;");
                                messageLabel.setText("✅ 登录成功！正在跳转...");

                                // 关闭登录窗口
                                Stage loginStage = (Stage) usernameField.getScene().getWindow();
                                loginStage.close();

                                // 打开主窗口
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
                        messageLabel.setText("解密失败: " + e.getMessage());
                        e.printStackTrace();
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> messageLabel.setText("连接错误: " + e.getMessage()));
                // 出现错误时关闭连接，确保下次重试能重新握手
                NetworkManager.getInstance().close();
            }
        });
    }

    @FXML
    public void initialize() {
        // 优先填入配置文件里的 IP
        if (!NetworkManager.DEFAULT_SERVER_IP.isEmpty()) {
            ipField.setText(NetworkManager.DEFAULT_SERVER_IP);
        }

        // 后台搜索也放入线程池
        NetworkManager.getInstance().runAsync(() -> {
            String ip = UDPClient.searchServer();
            if (ip != null) {
                // 回到 UI 线程更新输入框
                Platform.runLater(() -> {
                    ipField.setText(ip); // 自动填入 IP
                });
            }
        });
    }

    @FXML
    protected void onRegisterClick() {
        String u = usernameField.getText();
        String p = passwordField.getText();
        String ip = ipField.getText();

        if (u.isEmpty() || p.isEmpty()) {
            messageLabel.setText("注册需填写用户名和密码");
            return;
        }

        // 注册逻辑也放入线程池
        NetworkManager.getInstance().runAsync(() -> {
            try {
                NetworkManager net = NetworkManager.getInstance();
                net.connect(ip, 8888);

                // 注册也需要安全握手 (否则密码是明文发的)
                net.performSecureHandshake();

                // 1. 发送注册包
                String json = String.format("{\"u\":\"%s\", \"p\":\"%s\"}", u, p);
                net.sendEncryptedJson(Command.REQ_REGISTER, json);

                // 2. 等待响应
                Packet resp = net.readPacket();

                // 3. 处理结果
                Platform.runLater(() -> {
                    try {
                        if (resp.getType() == Command.RESP_REGISTER) {
                            // 解密使用动态 Key
                            byte[] data = AESUtil.decrypt(resp.getBody(), net.getSessionKey());
                            String respStr = new String(data, StandardCharsets.UTF_8);

                            JsonObject obj = new Gson().fromJson(respStr, JsonObject.class);
                            int code = obj.get("code").getAsInt();
                            String msg = obj.get("msg").getAsString();

                            if (code == 200) {
                                showAlert("注册成功", "账号 " + u + " 已创建，请直接点击登录。");
                            } else {
                                messageLabel.setText("错误: " + msg);
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });

            } catch (Exception e) {
                Platform.runLater(() -> messageLabel.setText("连接失败: " + e.getMessage()));
                NetworkManager.getInstance().close();
            }
        });
    }

    // 注册辅助方法：弹窗提示
    private void showAlert(String title, String content) {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}