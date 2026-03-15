package com.jsafe.server;

import com.jsafe.common.HashUtil;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class UserDAO {

    // 验证登录
    public boolean login(String username, String password) {
        String sql = "SELECT pwd_hash, salt FROM tb_user WHERE username = ?";

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String dbHash = rs.getString("pwd_hash");
                    String salt = rs.getString("salt");
                    // 用查出的盐，对用户输入的密码进行相同的哈希运算
                    String calculatedHash = HashUtil.sha256(password + salt);
                    // 比对计算结果
                    return calculatedHash.equals(dbHash);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    // 注册
    public boolean register(String username, String password) {
        // 1. 生成唯一随机盐
        String salt = HashUtil.generateSalt();
        // 2. 计算加盐后的哈希值
        String pwdHash = HashUtil.sha256(password + salt);

        // 🌟 兼容性修改：MySQL 用 INSERT IGNORE，SQLite 用 INSERT OR IGNORE
        String sql;
        if (DBUtil.isMySQL()) {
            sql = "INSERT IGNORE INTO tb_user (username, pwd_hash, salt) VALUES (?, ?, ?)";
        } else {
            sql = "INSERT OR IGNORE INTO tb_user (username, pwd_hash, salt) VALUES (?, ?, ?)";
        }

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, username);
            ps.setString(2, pwdHash);
            ps.setString(3, salt);

            int rows = ps.executeUpdate();
            return rows > 0; // 如果影响行数 > 0，说明注册成功

        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public int getUserId(String username) {
        String sql = "SELECT id FROM tb_user WHERE username = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("id");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return -1; // 没找到
    }
}