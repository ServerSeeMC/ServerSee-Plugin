# ServerSee-Plugin

ServerSee 是一个为 Paper 1.20.1+ 服务器设计的性能监控插件。它集成了 Spark API，并通过内置的 REST API 接口提供实时的服务器状态和性能指标。

## 主要功能

- **实时性能监控**: 获取 TPS (5s/1m)、MSPT、CPU 使用率（进程/系统）及详细的内存占用情况。
- **服务器元数据**: 提供 MOTD、版本号、在线人数、游戏模式等基本信息。
- **异步历史记录**: 自动将性能数据存储在 SQLite 数据库中，支持查询历史波动情况。
- **隐私控制**: 通过配置文件控制是否在 API 中暴露插件列表。
- **无冲突设计**: 采用 Maven Shade 重定向技术，确保依赖库（Javalin, Jetty 等）与服务器或其他插件互不干扰。

## API 接口

默认端口：`8080`

| 端点 | 描述 |
| :--- | :--- |
| `GET /status` | 获取服务器基本信息（MOTD, 版本, 玩家数等） |
| `GET /metrics` | 获取实时性能指标（TPS, MSPT, CPU, RAM） |
| `GET /history` | 获取历史性能数据（支持 `?limit=N` 参数，默认 60） |
| `GET /ping` | 检查 API 服务是否存活 |

## 管理员接口 (需要 Token 验证)

这些接口需要你从 `plugins/ServerSee/token.txt` 获取 Token，并在请求头中包含：
`Authorization: Bearer serversee_你的Token`

| 端点 | 方法 | 描述 | 参数 |
| :--- | :--- | :--- | :--- |
| `/admin/command` | `POST` | 执行控制台命令 | `command`: 要执行的命令 |
| `/admin/restart` | `POST` | 重启服务器 | 无 |
| `/admin/shutdown` | `POST` | 关闭服务器 | 无 |
| `/admin/whitelist` | `GET` | 获取白名单列表 | 无 |
| `/admin/whitelist/toggle` | `POST` | 开启/关闭白名单 | `enabled`: true/false |
| `/admin/whitelist/add` | `POST` | 添加玩家到白名单 | `name`: 玩家 ID |
| `/admin/whitelist/remove` | `POST` | 移出白名单 | `name`: 玩家 ID |

## 配置文件 (`config.yml`)

```yaml
# API 服务器监听端口
api-port: 8080

# 性能数据采集间隔 (单位：秒)
# 采集的数据将存储在 SQLite 中用于历史查询
collection-interval: 60

# 是否在 /status 接口中输出插件列表
show-plugins: false

# 调试模式
debug: false
```

## 技术细节

- **Spark API**: 深度集成 Paper 内置的 Spark 性能分析工具。
- **Javalin & Jetty**: 轻量级 Web 框架，提供高性能的 RESTful 服务。
- **SQLite**: 嵌入式数据库，用于持久化监控历史。
- **SLF4J**: 规范化的日志输出，自动适配 Paper 控制台颜色。

## 开发者信息

- **组织**: [ServerSeeMC](https://github.com/ServerSeeMC)
- **仓库**: [ServerSee-Plugin](https://github.com/ServerSeeMC/ServerSee-Plugin)
