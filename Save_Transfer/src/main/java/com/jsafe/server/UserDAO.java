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

                    // 核心逻辑：计算用户输入的密码 Hash，看是否与数据库一致
                    // 假设前端传来的已经是明文密码（实际项目前端应先Hash一次，这里为了演示简单）
                    // 逻辑：Hash(Pass + Salt) == DB_Hash ?
                    // 注意：这里为了方便测试，我们暂时假设数据库存的是直出的 Hash，暂时不加 Salt 逻辑
                    // 你之前的 INSERT 语句填的是 'DUMMY_HASH'，我们需要去数据库手动改一下，或者写个注册逻辑

                    // 简单起见，我们先比对明文（假设数据库里 pwd_hash 存的就是 '123456'）
                    // 等跑通了再换回 HashUtil.sha256
                    return password.equals(dbHash);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }
}