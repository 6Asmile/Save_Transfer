package com.jsafe.client;

import com.jsafe.common.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class ClientUploadTest {
    // ⚠️ 找一个本地文件测试
    private static final String FILE_PATH = "test.jpg";

    public static void main(String[] args) {
        File file = new File(FILE_PATH);
        if (!file.exists()) {
            System.err.println("测试文件不存在，请修改 FILE_PATH 常量: " + file.getAbsolutePath());
            return;
        }

        try (Socket socket = new Socket("127.0.0.1", 8888);
             DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
             DataInputStream dis = new DataInputStream(socket.getInputStream())) {

            // 1. 先登录 (复用之前的逻辑)
            // ... 省略登录代码，或者服务端暂时注释掉 auth check，直接发 check resume 也可以
            // 为了严谨，建议保留登录包发送:
            String loginJson = "{\"u\":\"admin\", \"p\":\"123456\"}";
            new Packet(Command.REQ_AUTH, AESUtil.encrypt(loginJson.getBytes(StandardCharsets.UTF_8))).write(dos);
            Packet.read(dis); // 读取登录响应，暂不处理

            // 2. 计算 MD5
            System.out.println("正在计算文件 MD5...");
            String fileMd5 = HashUtil.getFileMD5(file);
            System.out.println("File MD5: " + fileMd5);

            // 3. 发送断点查询请求
            String checkJson = String.format("{\"md5\":\"%s\", \"fileName\":\"%s\", \"size\":%d}",
                    fileMd5, file.getName(), file.length());
            Packet checkPacket = new Packet(Command.REQ_CHECK_RESUME, AESUtil.encrypt(checkJson.getBytes(StandardCharsets.UTF_8)));
            checkPacket.write(dos);

            // 4. 读取服务端返回的 Offset
            Packet checkResp = Packet.read(dis);
            String respJsonStr = new String(AESUtil.decrypt(checkResp.getBody()), StandardCharsets.UTF_8);
            long offset = JsonParser.parseString(respJsonStr).getAsJsonObject().get("offset").getAsLong();
            System.out.println("服务器告诉我们要从位置 " + offset + " 开始传");

            // 5. 开始上传 (RandomAccessFile + AES)
            try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
                raf.seek(offset); //  核心：跳过已上传部分

                byte[] buffer = new byte[1024*128]; // 4KB 缓冲区
                int len;
                long progress = offset;

                while ((len = raf.read(buffer)) != -1) {
                    // 如果读不满buffer，需要截取有效数据
                    byte[] dataToSend = len == buffer.length ? buffer : Arrays.copyOf(buffer, len);

                    // 加密文件块
                    byte[] encryptedChunk = AESUtil.encrypt(dataToSend);

                    // 封装发送
                    Packet uploadPacket = new Packet(Command.REQ_UPLOAD_DATA, encryptedChunk);
                    uploadPacket.write(dos);

                    progress += len;
                    System.out.print("\rUploading... " + (progress * 100 / file.length()) + "%");

                    // ⚠ 模拟断网测试：取消下面的注释，在上传到 50% 时强行中断
                    /*
                    if (progress > file.length() / 2) {
                        System.out.println("\n 模拟网络中断！停止发送！");
                        break; // 退出循环，关闭 Socket
                    }
                    */
                }
            }
            System.out.println("\n上传逻辑结束。");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}