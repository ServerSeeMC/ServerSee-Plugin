# ServerSee-Plugin

ServerSee 是一个为 Paper 1.20.1+ 服务器设计的性能监控插件。它通过内置的 WebSocket 接口提供极速的服务器状态同步和远程管理功能。

## 主要功能

- **极速 WebSocket 通信**: 采用自定义 JSON 协议，全面替代传统的 HTTP REST API，显著降低连接开销并支持实时推送。
- **全量日志捕获**: 基于 Log4j2 Appender 实时同步服务器控制台输出，包括核心日志及所有插件日志。
- **深度指标采集**:
    - **Minecraft 指标**: TPS (5s/1m/5m)、MSPT、在线人数等（集成 Spark API）。
    - **系统指标**: CPU 使用率（进程/系统）、物理内存、JVM 内存、磁盘空间等（集成 OSHI）。
- **历史数据存储**: 自动将性能数据存储在 SQLite 数据库中，支持客户端查询 TPS 趋势图表。
- **管理员控制台**: 支持远程执行命令、白名单管理、服务器生命周期控制（需 Token 鉴权）。
- **超轻量依赖**: 经过深度优化（Minimize Jar），移除冗余框架，构建产物仅约 13.5MB。

## 通信协议

默认端口：`8080` (WebSocket)

所有通信通过 WebSocket 完成，支持基于 Token 的身份验证和操作审计。

## 配置文件 (`config.yml`)

```yaml
# API 服务器监听端口 (WebSocket)
api-port: 8080

# 性能数据采集间隔 (单位：秒)
collection-interval: 60

# 初始连接时同步的历史日志行数
log-history-lines: 50

# 调试模式
debug: false
```

## 技术细节

- **Java-WebSocket**: 轻量级 WebSocket 库，实现高性能实时双向通信。
- **Spark API**: 深度集成 Paper 内置的性能分析工具。
- **OSHI & JNA**: 跨平台硬件和系统信息采集。
- **Log4j2**: 拦截控制台日志流实现毫秒级同步。
- **SQLite**: 嵌入式数据库，用于持久化监控历史。

## 开发者信息

- **版本**: 1.0.0-beta.2
- **组织**: [ServerSeeMC](https://github.com/ServerSeeMC)
- **仓库**: [ServerSee-Plugin](https://github.com/ServerSeeMC/ServerSee-Plugin)
