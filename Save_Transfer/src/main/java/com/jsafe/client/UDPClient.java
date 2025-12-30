package com.jsafe.client;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

public class UDPClient {
    private static final int SERVER_UDP_PORT = 9999;

    /**
     * @return 找到的服务器IP，如果没有找到则返回 null
     */
    public static String searchServer() {
        System.out.println(" 正在搜索局域网服务器...");
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true); // 开启广播权限
            socket.setSoTimeout(5000); // 只等 2 秒，等不到就算了

            // 1. 发送广播：WhereAreYou
            byte[] data = "WhereAreYou".getBytes(StandardCharsets.UTF_8);
            DatagramPacket packet = new DatagramPacket(
                    data,
                    data.length,
                    InetAddress.getByName("255.255.255.255"), // 广播地址
                    SERVER_UDP_PORT
            );
            socket.send(packet);

            // 2. 接收回音
            byte[] buffer = new byte[1024];
            DatagramPacket receivePacket = new DatagramPacket(buffer, buffer.length);
            socket.receive(receivePacket);

            String msg = new String(receivePacket.getData(), 0, receivePacket.getLength(), StandardCharsets.UTF_8);
            if ("I_Am_Here".equals(msg)) {
                String serverIP = receivePacket.getAddress().getHostAddress();
                System.out.println(" 找到服务器: " + serverIP);
                return serverIP;
            }

        } catch (Exception e) {
            System.out.println(" 搜索超时或失败，请手动输入IP");
        }
        return null;
    }
}