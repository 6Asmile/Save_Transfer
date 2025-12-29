package com.jsafe.client;

import com.jsafe.common.AESUtil;
import com.jsafe.common.Command;
import com.jsafe.common.Packet;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * 全局网络管理器 (单例)
 * 负责维护唯一的 Socket 连接，避免每次操作都重新连
 */
public class NetworkManager {
    private static final NetworkManager INSTANCE = new NetworkManager();
    private Socket socket;
    private DataInputStream dis;
    private DataOutputStream dos;
    private boolean isConnected = false;

    private NetworkManager() {}

    public static NetworkManager getInstance() {
        return INSTANCE;
    }

    // 连接服务器
    public void connect(String ip, int port) throws IOException {
        if (isConnected && socket != null && !socket.isClosed()) return;

        this.socket = new Socket(ip, port);
        this.dis = new DataInputStream(socket.getInputStream());
        this.dos = new DataOutputStream(socket.getOutputStream());
        this.isConnected = true;
        System.out.println("✅ 已连接到服务器: " + ip);
    }

    // 发送请求
    public void sendPacket(Packet packet) throws IOException {
        if (!isConnected) throw new IOException("未连接服务器");
        packet.write(dos);
    }

    // 读取响应 (阻塞式，后续会改为异步)
    public Packet readPacket() throws IOException {
        if (!isConnected) throw new IOException("未连接服务器");
        return Packet.read(dis);
    }

    // 发送加密 JSON 请求的辅助方法
    public void sendEncryptedJson(byte type, String json) throws Exception {
        byte[] encryptedBody = AESUtil.encrypt(json.getBytes(StandardCharsets.UTF_8));
        new Packet(type, encryptedBody).write(dos);
    }

    public void close() {
        try {
            if(socket != null) socket.close();
            isConnected = false;
        } catch (IOException e) { e.printStackTrace(); }
    }
}