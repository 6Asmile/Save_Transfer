package com.jsafe;

import com.jsafe.server.ServerMain;

public class ServerLauncher {
    public static void main(String[] args) {
        // 通过这个普通类作为跳板，启动继承了 Application 的 ServerMain
        ServerMain.main(args);
    }
}