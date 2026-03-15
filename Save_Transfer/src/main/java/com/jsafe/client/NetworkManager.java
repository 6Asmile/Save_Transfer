package com.jsafe.client;

import com.jsafe.common.AESUtil;
import com.jsafe.common.Command;
import com.jsafe.common.HashUtil;
import com.jsafe.common.Packet;
import com.jsafe.common.RSAUtil;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 全局网络管理器 (单例)
 * 负责维护唯一的 Socket 连接，并处理安全握手
 */
public class NetworkManager {

    private static final NetworkManager INSTANCE = new NetworkManager();
    private Socket socket;
    private DataInputStream dis;
    private DataOutputStream dos;
    private boolean isConnected = false;

    // 🌟 动态生成的 AES 密钥
    private String currentSessionKey = null;
    public void setSessionKey(String key) { this.currentSessionKey = key; }
    public String getSessionKey() { return currentSessionKey; }

    public static String DEFAULT_SERVER_IP = "";

    // 全局缓存线程池
    private final ExecutorService executor = Executors.newCachedThreadPool();

    private NetworkManager() {}

    public static NetworkManager getInstance() {
        return INSTANCE;
    }

    // 1. 连接服务器
    public void connect(String ip, int port) throws IOException {
        if (isConnected && socket != null && !socket.isClosed()) return;

        this.socket = new Socket(ip, port);
        // 设置超时，防止无限等待
        this.socket.setSoTimeout(10000);
        this.dis = new DataInputStream(socket.getInputStream());
        this.dos = new DataOutputStream(socket.getOutputStream());
        this.isConnected = true;
        System.out.println("✅ 已连接到服务器: " + ip);
    }

    /**
     * 🌟 核心：执行 RSA + AES 安全握手
     * 此方法需要在登录(AUTH)之前调用
     */
    public synchronized void performSecureHandshake() throws Exception {
        if (currentSessionKey != null) return; // 已经握手过了

        System.out.println("🔐 正在建立安全隧道...");

        // (1) 请求 RSA 公钥 (指令 0x01)
        sendPacket(new Packet((byte) 0x01, new byte[0]));

        // (2) 接收公钥 (指令 0x02)
        Packet rsaResp = readPacket();
        if (rsaResp.getType() != (byte) 0x02) {
            throw new IOException("安全握手失败：服务器未返回有效公钥");
        }
        byte[] publicKeyBytes = rsaResp.getBody();

        // (3) 随机生成一个 16 位的 AES Session Key
        String tempKey = HashUtil.generateSalt(); // 使用你之前的盐生成工具作为随机Key

        // (4) 使用 RSA 公钥加密这个 AES Key
        byte[] encryptedAESKey = RSAUtil.encryptWithPublicKey(tempKey.getBytes(StandardCharsets.UTF_8), publicKeyBytes);

        // (5) 发送给服务端 (指令 0x03)
        sendPacket(new Packet((byte) 0x03, encryptedAESKey));

        // (6) 读取服务端确认
        Packet ack = readPacket();
        if (ack.getType() == (byte) 0x03) {
            this.currentSessionKey = tempKey;
            System.out.println("✅ 安全隧道建立成功，动态密钥已同步");
        } else {
            throw new IOException("安全握手失败：服务端拒绝密钥同步");
        }
    }

    // 2. 发送原始数据包 (同步锁)
    public synchronized void sendPacket(Packet packet) throws IOException {
        if (!isConnected || dos == null) throw new IOException("未连接服务器");
        packet.write(dos);
    }

    // 3. 读取原始数据包 (同步锁)
    public synchronized Packet readPacket() throws IOException {
        if (!isConnected || dis == null) throw new IOException("未连接服务器");
        return Packet.read(dis);
    }

    // 4. 发送加密 JSON 请求
    public synchronized void sendEncryptedJson(byte type, String json) throws Exception {
        if (currentSessionKey == null) {
            // 如果没握手，先尝试自动握手一次
            performSecureHandshake();
        }
        byte[] encryptedBody = AESUtil.encrypt(json.getBytes(StandardCharsets.UTF_8), currentSessionKey);
        sendPacket(new Packet(type, encryptedBody));
    }

    // 5. 解密收到的数据 (辅助方法)
    public byte[] decryptData(byte[] data) throws Exception {
        if (currentSessionKey == null) throw new Exception("解密失败：缺少 SessionKey");
        return AESUtil.decrypt(data, currentSessionKey);
    }

    // 6. 线程池运行异步任务
    public void runAsync(Runnable task) {
        if (!executor.isShutdown()) {
            executor.submit(task);
        }
    }

    public void shutdown() {
        executor.shutdownNow();
        close();
    }

    public void close() {
        try {
            if (socket != null) socket.close();
            isConnected = false;
            currentSessionKey = null; // 🌟 关键：连接断开时，密钥必须清除
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}