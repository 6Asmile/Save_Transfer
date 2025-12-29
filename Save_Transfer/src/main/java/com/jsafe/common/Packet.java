package com.jsafe.common;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class Packet {
    public static final short MAGIC_NUMBER = (short) 0xACED;

    private byte type;      // 消息类型
    private byte[] body;    // 消息体 (JSON字符串或文件字节)

    public Packet(byte type, byte[] body) {
        this.type = type;
        this.body = body;
    }

    // --- 序列化：将对象转为网络传输的字节流 ---
    public void write(DataOutputStream dos) throws IOException {
        dos.writeShort(MAGIC_NUMBER); // 2 bytes
        dos.writeByte(type);          // 1 byte
        dos.writeInt(body.length);    // 4 bytes
        dos.write(body);              // N bytes
        dos.flush();
    }

    // --- 反序列化：从网络流读取完整的数据包 ---
    public static Packet read(DataInputStream dis) throws IOException {
        // 1. 验证魔数 (解决粘包的第一步)
        short magic = dis.readShort();
        if (magic != MAGIC_NUMBER) {
            throw new IOException("Invalid Magic Number: " + Integer.toHexString(magic));
        }

        // 2. 读取头部
        byte type = dis.readByte();
        int length = dis.readInt();

        // 3. 读取Body (严格按照Length读取)
        byte[] body = new byte[length];
        dis.readFully(body); // readFully 保证读满 length 个字节

        return new Packet(type, body);
    }

    // Getters
    public byte getType() { return type; }
    public byte[] getBody() { return body; }
}