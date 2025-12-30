package com.jsafe.client;

import com.jsafe.common.AESUtil;
import com.jsafe.common.Command;
import com.jsafe.common.Packet;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    public static String DEFAULT_SERVER_IP = "";
    //  创建一个全局缓存线程池 (适合执行大量短时间的任务)
    private final ExecutorService executor = Executors.newCachedThreadPool();

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

    //所有的发送和读取方法加上 synchronized 关键字。这保证了当“上传线程”正在写数据时，“刷新线程”必须在门口等着，不能把数据插队发出去
    // 发送请求
    public synchronized void sendPacket(Packet packet) throws IOException {
        if (!isConnected || dos == null) throw new IOException("未连接服务器");
        packet.write(dos);
    }

    // 读取响应
    public synchronized Packet readPacket() throws IOException {
        if (!isConnected || dis == null) throw new IOException("未连接服务器");
        return Packet.read(dis);
    }

    // 发送加密 JSON 请求的辅助方法
    public synchronized void sendEncryptedJson(byte type, String json) throws Exception {
        byte[] encryptedBody = AESUtil.encrypt(json.getBytes(StandardCharsets.UTF_8));
        sendPacket(new Packet(type, encryptedBody));
    }

    //  专门用来跑后台任务
    public void runAsync(Runnable task) {
        if (!executor.isShutdown()) {
            executor.submit(task);
        }
    }
    //   程序关闭时销毁线程池
    public void shutdown() {
        executor.shutdownNow();
        close(); // 关闭 socket
    }
    public void close() {
        try {
            if(socket != null) socket.close();
            isConnected = false;
        } catch (IOException e) { e.printStackTrace(); }
    }
}