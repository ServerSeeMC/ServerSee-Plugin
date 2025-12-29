# 项目规则与开发计划

## 项目信息
- **项目名称**: ServerSee
- **包名**: `cn.lemwood`
- **GitHub**: [leemwood/serversee](https://github.com/leemwood/serversee)
- **主要功能**: 通过 Spark API 获取 Paper 服务器性能数据，并通过独立端口提供 REST API。

## 开发环境
- **操作系统**: Windows Server 2022
- **Maven 路径**: `E:\apache-maven-3.9.12-bin\apache-maven-3.9.12\bin`
- **Java 版本**: 17
- **Minecraft 核心**: Paper 1.20.1+

## 开发规范
- 默认语言: 简体中文
- 禁止使用 Emoji
- 每次用户要求必须记录到项目规则文件中
- 新的知识点需记录在项目规则目录下

## 知识点记录
- **Spark API**: 用于获取服务器的 TPS、MSPT、CPU 使用率、内存使用率等性能指标。
- **Paper 1.20+**: 在较新版本的 Paper 中，spark 已作为内置插件/组件提供，无需额外安装。
- **Javalin**: 轻量级 Java Web 框架，用于快速构建 RESTful API。
- **Maven Shade**: 用于将依赖（如 Javalin）打包进插件 JAR 中，并进行重定向（Relocation）以避免类冲突。
- **Classloader Switch**: 在 Paper/Spigot 插件中使用 Javalin 等框架时，需要临时切换 `ContextClassLoader` 为插件的类加载器，以确保 Jetty (Javalin 的底层服务器) 能正确加载其依赖类。
- **Built-in Spark Loading**: Paper 内置的 spark 组件初始化较晚。插件需使用延迟初始化（如 `BukkitScheduler` 任务）来获取 `SparkProvider` 实例，否则在 `onEnable` 中直接获取可能会抛出 `IllegalStateException`。
- **SQLite Storage**: 使用 SQLite 存储历史性能数据。在 Paper 环境下，建议对 `org.sqlite` 驱动进行 Relocation 以避免潜在的版本冲突。
- **Async Metrics Collection**: 性能指标采集应在异步线程进行，以避免干扰主线程循环（TPS）。
- **Dependency Shading & Relocation**: 使用 Javalin 等复杂框架时，必须对所有传递依赖（Jetty, Kotlin, SLF4J, Jackson）进行 Relocation，以防止与服务器或其他插件冲突，并解决 `NoClassDefFoundError`。
- **Plugin Deployment**: 测试时需确保 `plugins/` 目录下无重复或名称冲突的 JAR 文件（如 `ServerSee.jar` 与 `serversee-1.0-SNAPSHOT.jar`），否则 Paper 会抛出 "Ambiguous plugin name" 错误。
- **Restart Mechanism**: 插件支持通过 API 远程重启。为了灵活性，重启命令可在 `config.yml` 中配置（`restart-command`），默认调用 Spigot 的 `restart` 命令。这需要服务器在 `spigot.yml` 中配置有效的 `restart-script`。
- **JNA & OSHI Relocation**: OSHI 依赖 JNA 进行底层系统调用。在插件环境中，如果对 `com.sun.jna` 进行重定向（Relocation），会导致 JNA 无法找到其内置的本地库（Native Libraries），报错 `UnsatisfiedLinkError: 'java.lang.String cn.lemwood.serversee.lib.jna.Native.getNativeVersion()'`。
    - **解决方案**: 保持 `com.sun.jna` 不重定向，仅对 `oshi` 进行重定向。OSHI 会自动寻找 classpath 下的 `com.sun.jna` 包。由于 Paper 核心通常不包含 JNA，插件自带的 JNA (不重定向) 可以正常工作。

## 测试环境
- **目录**: `test/`
- **内存配置**: 1.5GB (`-Xms1536M -Xmx1536M`)
- **核心文件**: `paper.jar` (已准备，内置 spark)
- **启动脚本**: `test/start.bat`
- **数据文件**: `test/plugins/ServerSee/data.db` (SQLite 数据库)

## 开发计划
1. [x] 初始化项目结构和 `pom.xml`
2. [x] 实现 Spark API 集成
3. [x] 集成 Javalin Web 服务器
4. [x] 设计并实现监控 API 端点
5. [x] 验证编译与基本功能
6. [x] 插件配置与多端口支持
7. [x] 实现 SQLite 异步数据持久化与历史查询
8. [x] 扩展服务器元数据 API (MOTD, 版本, 插件列表)
9. [x] 集成物理主机指标采集 (OSHI) 并解决 JNA 冲突
