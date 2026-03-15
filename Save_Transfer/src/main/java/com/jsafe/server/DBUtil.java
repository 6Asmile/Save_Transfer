package com.jsafe.server;

import java.io.FileInputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

public class DBUtil {
    // SQLite 默认配置
    private static final String SQLITE_URL = "jdbc:sqlite:jsafe_data.db";

    // MySQL 配置变量 (去掉了 final，因为要从文件读取)
    private static String mysqlUrl = "jdbc:mysql://localhost:3306/jsafe_db?useSSL=false&serverTimezone=UTC";
    private static String mysqlUser = "root";
    private static String mysqlPass = "root";

    // 核心开关
    private static boolean useMySQL = false;

    // 静态代码块：类加载时执行一次
    static {
        loadConfig(); // 1. 先读取配置文件
        loadDriver(); // 2. 再根据配置加载驱动
    }

    // --- 步骤1: 读取 server.properties ---
    private static void loadConfig() {
        try (FileInputStream fis = new FileInputStream("server.properties")) {
            Properties props = new Properties();
            props.load(fis);

            // 读取数据库类型
            String type = props.getProperty("db.type", "sqlite").trim(); // 默认 sqlite
            useMySQL = "mysql".equalsIgnoreCase(type);

            if (useMySQL) {
                // 如果是 MySQL，读取账号密码覆盖默认值
                mysqlUrl = props.getProperty("db.mysql.url", mysqlUrl);
                mysqlUser = props.getProperty("db.mysql.user", mysqlUser);
                mysqlPass = props.getProperty("db.mysql.password", mysqlPass);
                System.out.println("⚙️ 配置加载: 使用 MySQL 模式");
            } else {
                System.out.println("⚙️ 配置加载: 使用 SQLite 便携模式");
            }

        } catch (IOException e) {
            System.out.println("⚠️ 未找到 server.properties，默认使用 SQLite 模式");
            useMySQL = false;
        }
    }

    // --- 步骤2: 加载驱动 ---
    private static void loadDriver() {
        try {
            if (useMySQL) {
                Class.forName("com.mysql.cj.jdbc.Driver");
            } else {
                Class.forName("org.sqlite.JDBC");
                initSqliteTables(); // SQLite 需要自动建表
            }
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            System.err.println("❌ 严重错误: 数据库驱动加载失败！");
        }
    }

    // 获取连接
    public static Connection getConnection() throws SQLException {
        if (useMySQL) {
            return DriverManager.getConnection(mysqlUrl, mysqlUser, mysqlPass);
        } else {
            return DriverManager.getConnection(SQLITE_URL);
        }
    }

    public static boolean isMySQL() {
        return useMySQL;
    }

    // SQLite 初始化建表 (代码保持不变)
    private static void initSqliteTables() {
        String sqlUser = """
            CREATE TABLE IF NOT EXISTS tb_user (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                username TEXT NOT NULL UNIQUE,
                pwd_hash TEXT NOT NULL,
                salt TEXT NOT NULL
            );
        """;
        String sqlFile = """
            CREATE TABLE IF NOT EXISTS tb_file (
                file_id INTEGER PRIMARY KEY AUTOINCREMENT,
                real_name TEXT NOT NULL,
                save_path TEXT NOT NULL,
                size_bytes INTEGER NOT NULL,
                content_md5 TEXT NOT NULL,
                uploader_id INTEGER,
                upload_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            );
        """;
        try (Connection conn = DriverManager.getConnection(SQLITE_URL);
             Statement stmt = conn.createStatement()) {
            stmt.execute(sqlUser);
            stmt.execute(sqlFile);
        } catch (SQLException e) { e.printStackTrace(); }
    }
}