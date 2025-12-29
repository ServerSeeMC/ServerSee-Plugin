# ServerSee API 接口文档

ServerSee 插件提供了一套完整的 REST API，用于监控 Paper 服务器状态并执行管理员操作。

## 基础信息
- **Base URL**: `http://<server-ip>:<port>` (默认端口: `8080`)
- **数据格式**: `application/json`
- **身份验证**: 
  - 所有以 `/admin/` 开头的接口均需在 Header 中携带 Token。
  - **Header 格式**: `Authorization: Bearer <your_token>`
  - **Token 获取**: 插件首次运行后在 `plugins/ServerSee/token.txt` 文件中生成。

---

## 1. 公共接口 (无需 Token)

### 1.1 获取服务器状态
- **Endpoint**: `GET /status`
- **功能**: 获取服务器元数据、版本、在线人数等信息。
- **响应示例**:
  ```json
  {
    "online": true,
    "motd": "A Minecraft Server",
    "version": "1.20.1 (MC: 1.20.1)",
    "bukkit_version": "1.20.1-R0.1-SNAPSHOT",
    "players": 5,
    "max_players": 20,
    "gamemode": "SURVIVAL",
    "plugins": ["ServerSee v1.0", "Essentials v2.20"]
  }
  ```

### 1.2 获取实时性能指标
- **Endpoint**: `GET /metrics`
- **功能**: 获取当前的 TPS、MSPT、CPU 和内存使用率。
- **响应示例**:
  ```json
  {
    "tps_5s": 19.95,
    "tps_1m": 20.0,
    "mspt": 12.5,
    "cpu_process": 5.2,
    "cpu_system": 15.8,
    "mem_used": 1024,
    "mem_total": 1536,
    "mem_max": 2048
  }
  ```

### 1.3 获取历史性能数据
- **Endpoint**: `GET /history`
- **参数**: `limit` (可选，默认 60)
- **功能**: 获取历史性能波动数据。

### 1.4 连通性测试
- **Endpoint**: `GET /ping`
- **功能**: 测试 API 是否在线。
- **响应**: `pong`

---

## 2. 管理员接口 (需要 Token 验证)

### 2.1 执行控制台命令
- **Endpoint**: `POST /admin/command`
- **参数 (Form Data)**: `command`
- **功能**: 以控制台身份执行指令。

### 2.2 重启服务器
- **Endpoint**: `POST /admin/restart`
- **功能**: 延迟 1 秒后执行配置的重启命令。
- **说明**: 默认执行 `restart` 命令。你可以在 `config.yml` 中通过 `restart-command` 自定义该命令（例如使用特定的重启脚本指令）。

### 2.3 关闭服务器
- **Endpoint**: `POST /admin/shutdown`
- **功能**: 延迟 1 秒后关闭服务器。

### 2.4 白名单管理
- **GET /admin/whitelist**: 获取列表。
- **POST /admin/whitelist/toggle**: 开关白名单 (参数: `enabled`).
- **POST /admin/whitelist/add**: 添加玩家 (参数: `name`).
- **POST /admin/whitelist/remove**: 移除玩家 (参数: `name`).

---

## 错误处理
- **401 Unauthorized**: Token 缺失或错误。
- **400 Bad Request**: 参数缺失。

---
**安全提示**: 请妥善保管 Token，建议仅允许可信 IP 访问 API 端口。
