package com.jsafe.common;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

public class AESUtil {
    // 32字节 = 256位密钥 (必须严格是 32 chars)
    private static final String STATIC_KEY = "J-SafeTransfer_Secret_Key_202501";
    private static final String ALGORITHM = "AES/CBC/PKCS5Padding";

    // 加密
    public static byte[] encrypt(byte[] data) throws Exception {
        // 1. 生成随机 IV (16字节)
        byte[] iv = new byte[16];
        new SecureRandom().nextBytes(iv);
        IvParameterSpec ivSpec = new IvParameterSpec(iv);

        // 2. 初始化 Cipher
        SecretKeySpec keySpec = new SecretKeySpec(STATIC_KEY.getBytes(StandardCharsets.UTF_8), "AES");
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);

        // 3. 执行加密
        byte[] encrypted = cipher.doFinal(data);

        // 4. 拼接: [IV (16 bytes)] + [Encrypted Data]
        // 接收方需要先读前16字节拿到 IV，才能解密后面的数据
        byte[] result = new byte[iv.length + encrypted.length];
        System.arraycopy(iv, 0, result, 0, iv.length);
        System.arraycopy(encrypted, 0, result, iv.length, encrypted.length);

        return result;
    }

    // 解密
    public static byte[] decrypt(byte[] combinedData) throws Exception {
        // 1. 提取 IV
        byte[] iv = new byte[16];
        System.arraycopy(combinedData, 0, iv, 0, 16);
        IvParameterSpec ivSpec = new IvParameterSpec(iv);

        // 2. 提取密文
        int dataLen = combinedData.length - 16;
        byte[] encrypted = new byte[dataLen];
        System.arraycopy(combinedData, 16, encrypted, 0, dataLen);

        // 3. 解密
        SecretKeySpec keySpec = new SecretKeySpec(STATIC_KEY.getBytes(StandardCharsets.UTF_8), "AES");
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);

        return cipher.doFinal(encrypted);
    }
}