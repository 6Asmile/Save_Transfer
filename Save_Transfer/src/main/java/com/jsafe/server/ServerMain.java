package com.jsafe.server;

import com.jsafe.common.Command;
import com.jsafe.common.Packet;
// 在 ServerMain 头部引入
import com.jsafe.common.AESUtil;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.File;
import java.io.RandomAccessFile;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.ArrayList;
import java.util.List;

public class ServerMain {
    private static final int PORT = 8888;
    // 线程池：避免为每个客户端无限创建线程，导致服务器崩溃
    private static final ExecutorService threadPool = Executors.newFixedThreadPool(10);

    public static void main(String[] args) {
        // 启动 UDP 监听
        UDPProvider.start();
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println(" Server started on port " + PORT);

            while (true) {
                // 阻塞等待客户端连接
                Socket clientSocket = serverSocket.accept();
                System.out.println("New connection: " + clientSocket.getInetAddress());

                // 丢给线程池处理
                threadPool.execute(new ClientHandler(clientSocket));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 内部类：处理单个客户端逻辑
    static class ClientHandler implements Runnable {
        private Socket socket;
        // 上传状态上下文
        private String currentFileMD5;
        private long currentFileSize;      // 文件总大小
        private String currentFileName;

        // ⚠️ 请修改为你电脑上的实际路径
        private static final String TEMP_DIR = "./server_temp";
        private static final String STORAGE_DIR = "./server_storage";

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            // 确保目录存在
            new File(TEMP_DIR).mkdirs();
            new File(STORAGE_DIR).mkdirs();

            try (DataInputStream dis = new DataInputStream(socket.getInputStream());
                 DataOutputStream dos = new DataOutputStream(socket.getOutputStream())) {

                while (true) {
                    Packet packet = Packet.read(dis);

                    switch (packet.getType()) {
                        case Command.REQ_AUTH -> handleLogin(packet, dos);
                        case Command.REQ_REGISTER -> handleRegister(packet, dos);
                        case Command.REQ_CHECK_RESUME -> handleCheckResume(packet, dos);
                        case Command.REQ_UPLOAD_DATA -> handleUploadData(packet, dos);
                        case Command.REQ_LIST_FILES -> handleListFiles(dos);
                        default -> System.out.println("未知指令: " + packet.getType());
                    }
                }
            } catch (IOException e) {
                System.out.println("Client disconnected.");
            }
        }

        private void handleLogin(Packet req, DataOutputStream dos) throws IOException {
            try {
                // 1. 解密请求体
                byte[] decryptedBody = AESUtil.decrypt(req.getBody());
                String jsonStr = new String(decryptedBody, StandardCharsets.UTF_8);
                System.out.println("收到加密登录请求(解密后): " + jsonStr);

                // 2. 解析 JSON
                Gson gson = new Gson();
                JsonObject jsonObj = gson.fromJson(jsonStr, JsonObject.class);
                String u = jsonObj.get("u").getAsString();
                String p = jsonObj.get("p").getAsString();

                // 3. 查库校验
                UserDAO userDAO = new UserDAO();
                boolean isSuccess = userDAO.login(u, p);

                // 4. 构造响应
                String respJson;
                if (isSuccess) {
                    respJson = "{\"code\":200, \"msg\":\"Login Success from DB!\"}";
                } else {
                    respJson = "{\"code\":401, \"msg\":\"Login Failed\"}";
                }

                // 5. 加密响应并发回
                byte[] encryptedResp = AESUtil.encrypt(respJson.getBytes(StandardCharsets.UTF_8));
                Packet resp = new Packet(Command.RESP_AUTH, encryptedResp);
                resp.write(dos);

            } catch (Exception e) {
                e.printStackTrace();
                // 发送错误包...
            }
        }

        // 1. 处理断点检查请求
        private void handleCheckResume(Packet packet, DataOutputStream dos) throws IOException {
            try {
                // 解密 Body
                String jsonStr = new String(AESUtil.decrypt(packet.getBody()), StandardCharsets.UTF_8);
                System.out.println("收到续传检查: " + jsonStr);

                // 解析: {"md5":"...", "fileName":"...", "size": 102400}
                Gson gson = new Gson();
                JsonObject req = gson.fromJson(jsonStr, JsonObject.class);

                this.currentFileMD5 = req.get("md5").getAsString();
                this.currentFileName = req.get("fileName").getAsString();
                this.currentFileSize = req.get("size").getAsLong();

                // 检查临时文件是否存在
                File tempFile = new File(TEMP_DIR, currentFileMD5 + ".temp");
                long offset = 0;
                if (tempFile.exists()) {
                    offset = tempFile.length();
                    System.out.println("发现断点文件，当前进度: " + offset);
                }

                // 构造响应: {"offset": 12345}
                String respJson = "{\"offset\":" + offset + "}";
                Packet resp = new Packet(Command.RESP_CHECK_RESUME, AESUtil.encrypt(respJson.getBytes(StandardCharsets.UTF_8)));
                resp.write(dos);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // 2. 处理文件数据块上传
        private void handleUploadData(Packet packet, DataOutputStream dos) throws IOException {
            try {
                // 解密得到原始文件块
                byte[] fileChunk = AESUtil.decrypt(packet.getBody());

                if (currentFileMD5 == null) {
                    System.out.println("错误：未经过握手直接上传数据");
                    return;
                }

                // 追加写入临时文件
                File tempFile = new File(TEMP_DIR, currentFileMD5 + ".temp");
                try (RandomAccessFile raf = new RandomAccessFile(tempFile, "rw")) {
                    raf.seek(raf.length()); // 移动到文件末尾
                    raf.write(fileChunk);
                }

                // 检查是否传输完成
                if (tempFile.length() >= currentFileSize) {
                    System.out.println("文件传输完成: " + currentFileName);
                    // 重命名为正式文件
                    File destFile = new File(STORAGE_DIR, currentFileName);
                    // 如果存在同名文件则先删除
                    if (destFile.exists()) destFile.delete();

                    boolean renamed = tempFile.renameTo(destFile);
                    if (renamed) {
                        // TODO: 这里可以插入数据库 tb_file 记录
                        System.out.println(" 文件已归档至 storage 目录");
                    }
                }

                // 可选：发送一个简单的 ACK (为了追求速度，通常不每包都回，但这演示用TCP流控即可)
                // 这里为了简化，服务端不回复 ACK，客户端只管发（TCP保证有序）

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // 3.文件列表
        private void handleListFiles(DataOutputStream dos) throws IOException {
            try {
                File dir = new File(STORAGE_DIR);
                File[] files = dir.listFiles();

                List<String> fileNames = new ArrayList<>();
                if (files != null) {
                    for (File f : files) {
                        // 这里简单只传文件名，以后可以传大小、时间等对象
                        fileNames.add(f.getName());
                    }
                }

                // 转成 JSON: ["a.jpg", "b.txt"]
                String json = new Gson().toJson(fileNames);
                System.out.println("客户端请求列表，返回: " + json);

                // 加密发送
                byte[] encrypted = AESUtil.encrypt(json.getBytes(StandardCharsets.UTF_8));
                new Packet(Command.REQ_LIST_FILES, encrypted).write(dos); // 复用 type，或者定义一个 RESP_LIST_FILES

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // 4.注册
        private void handleRegister(Packet packet, DataOutputStream dos) throws IOException {
            try {
                // 1. 解密
                byte[] decrypted = AESUtil.decrypt(packet.getBody());
                String jsonStr = new String(decrypted, StandardCharsets.UTF_8);
                System.out.println("收到注册请求: " + jsonStr);

                // 2. 解析 JSON
                JsonObject req = new Gson().fromJson(jsonStr, JsonObject.class);
                String u = req.get("u").getAsString();
                String p = req.get("p").getAsString();

                // 3. 写入数据库
                UserDAO dao = new UserDAO();
                boolean success = dao.register(u, p);

                // 4. 返回结果
                String msg = success ? "注册成功" : "注册失败(用户名可能已存在)";
                int code = success ? 200 : 409;

                String respJson = String.format("{\"code\":%d, \"msg\":\"%s\"}", code, msg);

                // 5. 加密发送
                byte[] encryptedResp = AESUtil.encrypt(respJson.getBytes(StandardCharsets.UTF_8));
                new Packet(Command.RESP_REGISTER, encryptedResp).write(dos);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }
}