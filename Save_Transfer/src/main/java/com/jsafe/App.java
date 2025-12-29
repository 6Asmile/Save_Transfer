package com.jsafe;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class App extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        // 加载登录界面 FXML
        FXMLLoader fxmlLoader = new FXMLLoader(App.class.getResource("/login-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 400, 300);

        stage.setTitle("J-SafeTransfer - 安全登录");
        stage.setScene(scene);
        stage.setResizable(false); // 禁止改变窗口大小
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}