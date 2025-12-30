package com.jsafe.server;

import java.sql.Connection;
import java.sql.PreparedStatement;

public class FileDAO {

    public boolean addFile(String realName, String savePath, long size, String md5, int uploaderId) {
        String sql = "INSERT INTO tb_file (real_name, save_path, size_bytes, content_md5, uploader_id) VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, realName);
            ps.setString(2, savePath);
            ps.setLong(3, size);
            ps.setString(4, md5);
            ps.setInt(5, uploaderId);

            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // 删除记录
    public boolean deleteFileRecord(String realName, int uploaderId) {
        String sql = "DELETE FROM tb_file WHERE real_name = ? AND uploader_id = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, realName);
            ps.setInt(2, uploaderId);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    //更新记录
    public boolean updateFileName(String oldName, String newName, int uploaderId) {
        String sql = "UPDATE tb_file SET real_name = ? WHERE real_name = ? AND uploader_id = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newName);
            ps.setString(2, oldName);
            ps.setInt(3, uploaderId);
            return ps.executeUpdate() > 0;
        } catch (Exception e) { e.printStackTrace(); return false; }
    }
}