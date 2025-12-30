# J-SafeTransfer (Save_Transfer) 🚀

![Java](https://img.shields.io/badge/Java-17%2B-ED8B00?logo=openjdk&logoColor=white)
![JavaFX](https://img.shields.io/badge/GUI-JavaFX-4285F4?logo=java&logoColor=white)
![License](https://img.shields.io/badge/License-MIT-green)

**J-SafeTransfer** 是一个基于 Java 和 JavaFX 构建的高性能、安全局域网文件传输系统。
本项目抛弃了传统的 FTP 协议，采用自定义的 **LVP (Length-Value-Payload)** 协议，实现了基于 TCP 的**全链路 AES 加密传输**，并支持**断点续传**（秒传）功能。

---

## ✨ 核心特性 (Key Features)

*   **🔒 企业级安全**：通信过程采用 **AES-256-CBC** 对称加密，拒绝明文传输，确保数据机密性。
*   **💾 断点续传 (Resumable Upload)**：核心亮点。支持网络中断后的自动恢复，利用文件指纹 (MD5) 实现秒传和完整性校验。
*   **🖥️ 现代化 UI**：基于 **JavaFX** 开发的响应式界面，支持文件拖拽上传 (Drag & Drop)。
*   **⚡ 高性能网络**：基于 Java Socket (BIO) + 线程池模型，自定义二进制协议解决 TCP 粘包问题。
*   **📦 数据库集成**：使用 MySQL 存储用户凭证与文件元数据，支持多用户验证。

---

## 🛠️ 技术栈 (Tech Stack)

*   **开发语言**: Java 17 (LTS)
*   **GUI 框架**: JavaFX 17+
*   **构建工具**: Maven 3.8+
*   **数据库**: MySQL 8.0
*   **核心依赖**:
    *   `Gson`: JSON 协议解析
    *   `MySQL Connector`: 数据库驱动
    *   `Junit`: 单元测试

---

## 🏗️ 系统架构与协议 (Architecture & Protocol)

### 自定义通信协议 (LVP)
为了确保数据在 TCP 流中的完整性，项目设计了如下数据包结构：

| 字段 (Bytes) | 类型 | 说明 |
| :--- | :--- | :--- |
| **Magic (2)** | `short` | 协议魔数 `0xACED`，用于校验数据包合法性 |
| **Type (1)** | `byte` | 指令类型 (如 `0x10` 登录, `0x30` 上传数据) |
| **Length (4)** | `int` | Body 的长度 (Big-Endian) |
| **Body (N)** | `byte[]` | **AES 加密**后的 Payload (JSON 或 文件流) |

### 目录结构
*   `/server_storage`: 服务端文件存储区（上传完成的文件）。
*   `/server_temp`: 断点续传临时缓存区（上传未完成的 `.temp` 文件）。

---

## 🚀 快速开始 (Quick Start)

### 1. 环境准备
*   JDK 17 或更高版本
*   MySQL 8.0+
*   Maven

### 2. 数据库初始化
请在 MySQL 中执行以下 SQL 脚本：

```sql
CREATE DATABASE jsafe_db DEFAULT CHARSET utf8mb4;
USE jsafe_db;

-- 用户表
CREATE TABLE tb_user (
    id INT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(64) NOT NULL UNIQUE,
    pwd_hash CHAR(64) NOT NULL,
    salt CHAR(16) NOT NULL
);

-- 插入测试账号 (密码: 123456)
INSERT INTO tb_user (username, pwd_hash, salt) VALUES ('admin', '123456', '1234');

-- 文件表 (可选)
CREATE TABLE tb_file (
    file_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    real_name VARCHAR(255) NOT NULL,
    content_md5 CHAR(32) NOT NULL,
    upload_time DATETIME DEFAULT CURRENT_TIMESTAMP
);
```

### 3. 构建项目
在项目根目录下运行：
```bash
mvn clean package
```
构建成功后，`target` 目录下会生成 `Save_Transfer-1.0-SNAPSHOT.jar`。

### 4. 运行指南

**启动服务端 (Server):**
可以使用命令行参数启动，或者使用单独的启动脚本。
```bash
# 方式 A: 通过 Launcher 代码逻辑 (需修改 Launcher.java 支持参数)
java -jar target/Save_Transfer-1.0-SNAPSHOT.jar server

# 方式 B: 直接指定主类
java -cp target/Save_Transfer-1.0-SNAPSHOT.jar com.jsafe.server.ServerMain
```
*服务端默认监听端口: `8888`*

**启动客户端 (Client):**
```bash
java -jar target/Save_Transfer-1.0-SNAPSHOT.jar
```
1.  输入用户名 `admin` / 密码 `123456`。
2.  输入服务端 IP 地址 (内网测试如 `10.14.xx.xx`)。
3.  点击登录，开始传输文件！

---

## 📸 截图展示 (Screenshots)

<img width="696" height="519" alt="image" src="https://github.com/user-attachments/assets/f5391050-dde3-4599-931b-70958975513f" />
<img width="702" height="532" alt="2984d38622c07c4f5119e2b0690c1c08" src="https://github.com/user-attachments/assets/d4adf476-ebef-4ae6-8bf7-0bc41e8f0ada" />


*   **登录界面**: 简洁的安全认证入口。
*   **主工作台**: 支持拖拽、列表刷新、进度实时显示。

---

## 🔮 未来规划 (Roadmap)

- [x] 基础登录与加密传输
- [x] 断点续传与秒传
- [x] JavaFX 图形化界面
- [ ] 多用户文件隔离 (User Isolation)
- [ ] 文件下载功能 (Download Support)
- [ ] P2P 局域网自动发现 (UDP Broadcast)

---

## 🤝 贡献与许可

本项目仅供学习与课程设计参考。
Licensed under the [MIT License](LICENSE).
