package com.jsafe.client;

import com.jsafe.common.Command;
import com.jsafe.common.Packet;
import com.jsafe.common.AESUtil;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class ClientTest {
    public static void main(String[] args) {
        try (Socket socket = new Socket("127.0.0.1", 8888);
             DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
             DataInputStream dis = new DataInputStream(socket.getInputStream())) {

            // 1. 构造原始 JSON
            String loginJson = "{\"u\":\"admin\", \"p\":\"123456\"}";

            // 2. 加密
            byte[] encryptedBody = AESUtil.encrypt(loginJson.getBytes(StandardCharsets.UTF_8));

            // 3. 封装并发送
            Packet loginPacket = new Packet(Command.REQ_AUTH, encryptedBody);
            System.out.println("发送加密数据包 (长度 " + encryptedBody.length + ")...");
            loginPacket.write(dos);

            // 4. 接收响应
            Packet resp = Packet.read(dis);
            // 解密响应
            byte[] decryptedResp = AESUtil.decrypt(resp.getBody());
            System.out.println("服务器响应(解密后): " + new String(decryptedResp, StandardCharsets.UTF_8));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}