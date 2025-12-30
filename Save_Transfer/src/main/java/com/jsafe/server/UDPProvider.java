package com.jsafe.server;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.charset.StandardCharsets;

public class UDPProvider {
    private static final int UDP_PORT = 9999; // 广播专用端口，别和 TCP 8888 冲突

    public static void start() {
        new Thread(() -> {
            try (DatagramSocket socket = new DatagramSocket(UDP_PORT)) {
                System.out.println(" UDP 广播监听已启动 (Port " + UDP_PORT + ")...");

                byte[] buffer = new byte[1024];
                while (true) {
                    // 1. 准备接收包裹
                    DatagramPacket receivePacket = new DatagramPacket(buffer, buffer.length);
                    socket.receive(receivePacket); // 阻塞等待

                    // 2. 看看谁喊话，喊了什么
                    String msg = new String(receivePacket.getData(), 0, receivePacket.getLength(), StandardCharsets.UTF_8);

                    if ("WhereAreYou".equals(msg)) {
                        // 3. 回复：I_Am_Here
                        String response = "I_Am_Here";
                        byte[] data = response.getBytes(StandardCharsets.UTF_8);

                        // 拿到发送者的 IP 和 端口
                        DatagramPacket responsePacket = new DatagramPacket(
                                data,
                                data.length,
                                receivePacket.getAddress(),
                                receivePacket.getPort()
                        );
                        socket.send(responsePacket);
                        System.out.println("已回应客户端搜索: " + receivePacket.getAddress());
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
}