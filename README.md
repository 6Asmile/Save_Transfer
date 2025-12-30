# J-SafeTransfer (Save_Transfer) 🚀

![Java](https://img.shields.io/badge/Java-17%2B-ED8B00?logo=openjdk&logoColor=white)
![JavaFX](https://img.shields.io/badge/GUI-JavaFX-4285F4?logo=java&logoColor=white)
![License](https://img.shields.io/badge/License-MIT-green)

**J-SafeTransfer** 是一个基于 Java 和 JavaFX 构建的高性能、安全局域网文件传输系统。
本项目抛弃了传统的 FTP 协议，采用自定义的 **LVP (Length-Value-Payload)** 协议，实现了基于 TCP 的**全链路 AES 加密传输**，并支持**断点续传**（秒传）功能。

---

- [ ] # 🚀 J-SafeTransfer 技术亮点与深度解析

  **项目定义**：一个基于 Java 17 和 JavaFX 构建的高性能、全链路加密、支持断点续传的企业级文件传输系统。

  ## 1. 🛡️ 全链路安全架构 (Security First)

  - 
  - **对称加密护航**：系统核心采用 **AES-256-CBC** 模式对文件流进行实时加密。与传统明文 FTP 不同，J-SafeTransfer 在网络上传输的每一比特数据均为密文，有效防止中间人攻击（MITM）。
  - **身份验证机制**：基于 **SHA-256 + Salt** 的密码哈希存储方案，确保数据库即便泄露，原始密码也无法被还原。
  - **数据完整性校验**：采用 **MD5 指纹技术**。在传输前、续传握手阶段及传输完成后进行三级校验，确保文件在弱网环境下也不会出现 1 bit 的损坏。

  ## 2. ⚡ 高性能传输与断点续传 (Reliability)

  - ![image-20251230154106992](C:\Users\26465\AppData\Roaming\Typora\typora-user-images\image-20251230154106992.png)
  - **断点续传算法**：自主实现基于 **RandomAccessFile** 的偏移量指针定位技术。上传前通过 MD5 握手协议向服务端查询已存在块大小（Offset），实现“秒级恢复”传输，极大提升了超大文件传输的成功率。
  - **双端分块缓存**：针对局域网环境优化，采用 **2MB 动态缓冲区**。平衡了内存占用与磁盘 I/O 效率，实测内网传输速度可突破 200MB/s。
  - **秒传功能**：服务端通过文件 MD5 指纹库进行全局检索，若相同文件已存在，则触发秒传逻辑，瞬间完成“上传”。

  ## 3. 📡 现代化网络通信协议 (Networking)

  - 
  - **自定义 LVP 协议**：为了彻底解决 TCP “粘包/拆包”问题，设计了 **Magic Number (魔数) + Type + Length + Value** 的应用层协议格式。
  - **UDP 自动发现服务**：集成 **UDP 广播 (Service Discovery)** 技术。客户端开启后可自动探测局域网内的活动服务端，实现“零配置”连接，极大优化了用户体验。
  - **并发连接管理**：采用 **ThreadPoolExecutor (固定大小线程池)** 架构。服务端能够稳定支撑多用户并发请求，避免了传统 BIO 模型下“一连接一线程”导致的系统资源枯竭问题。

  ## 4. 📊 工业级监控与交互 (UX & Visualization)

  - ![image-20251230154243373](C:\Users\26465\AppData\Roaming\Typora\typora-user-images\image-20251230154243373.png)
  - **全态可视化面板**：利用 JavaFX 绘图引擎构建 **服务端监控大屏**。实时绘制全站带宽波形图（LineChart），并动态展示在线用户状态及操作审计日志。
  - **响应式 UI 交互**：
    - 
    - **实时搜索过滤**：基于 FilteredList 实现海量文件列表的毫秒级实时搜索。
    - **智能文件管理**：支持右键菜单（重命名、删除、分享）、文件图标智能识别、拖拽上传等现代交互逻辑。
    - **传输状态回显**：实时计算传输速率（MB/s）及预计剩余时间，让传输进度直观透明。

  ## 5. 🏗️ 工程化实践 (Engineering)

  - 
  - **多端合一架构**：通过 Launcher 引导层实现客户端与服务端的逻辑分离与代码高度复用。系统可根据同级目录下的 server.properties 自动切换运行模式。
  - **高可用性保障**：针对多线程并发环境下 Socket 竞争导致的流同步失效问题，引入了**严格的同步锁（Synchronized Stream Access）与 异常重连机制**，确保长连接的稳定性。
  - **跨平台打包**：通过 Maven Shade 插件构建“Fat JAR”，并利用 Launch4j 封装为原生 EXE 执行文件，脱离开发环境限制，实现一键部署。

  ------

  

  ### 📈 技术栈总结 (Tech Stack)

  - 
  - **Language**: Java 17 (LTS)
  - **GUI Framework**: JavaFX 23
  - **Networking**: Socket (BIO) + UDP Broadcast
  - **Security**: AES-256-CBC, MD5, SHA-256
  - **Database**: MySQL 8.0 + JDBC
  - **Tools**: Maven, Gson, Launch4j

---

## 🤝 贡献与许可

本项目仅供学习与课程设计参考。
Licensed under the [MIT License](LICENSE).
