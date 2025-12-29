package cn.lemwood.serversee.api;

import cn.lemwood.serversee.ServerSee;
import cn.lemwood.serversee.auth.TokenManager;
import cn.lemwood.serversee.database.DatabaseManager;
import cn.lemwood.serversee.metrics.SparkManager;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class ApiServer extends WebSocketServer {
    private final SparkManager sparkManager;
    private final DatabaseManager databaseManager;
    private final TokenManager tokenManager;
    private final Gson gson = new Gson();
    
    private final Set<WebSocket> authenticatedSessions = ConcurrentHashMap.newKeySet();
    
    // 缓存
    private String cachedIconBase64 = null;
    private long lastIconUpdate = 0;
    private static final long ICON_CACHE_MS = TimeUnit.MINUTES.toMillis(10);
    
    // 简易速率限制
    private final Map<String, Integer> rateLimitMap = new ConcurrentHashMap<>();
    private static final int MAX_REQUESTS_PER_MINUTE = 120; // 提高限制，因为 WebSocket 连发较多

    public ApiServer(int port, SparkManager sparkManager, DatabaseManager databaseManager, TokenManager tokenManager) {
        super(new InetSocketAddress(port));
        this.sparkManager = sparkManager;
        this.databaseManager = databaseManager;
        this.tokenManager = tokenManager;
        
        // 每分钟清理一次速率限制
        Bukkit.getScheduler().runTaskTimerAsynchronously(ServerSee.getInstance(), rateLimitMap::clear, 1200L, 1200L);
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        String ip = conn.getRemoteSocketAddress().getAddress().getHostAddress();
        int requests = rateLimitMap.getOrDefault(ip, 0);
        if (requests >= MAX_REQUESTS_PER_MINUTE) {
            conn.close(429, "Too Many Requests");
            return;
        }
        rateLimitMap.put(ip, requests + 1);
        ServerSee.getInstance().getLogger().info("新的 WS 连接: " + ip);
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        authenticatedSessions.remove(conn);
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        try {
            JsonObject request = JsonParser.parseString(message).getAsJsonObject();
            String type = request.has("type") ? request.get("type").getAsString() : "request";
            String action = request.has("action") ? request.get("action").getAsString() : "";
            String requestId = request.has("id") ? request.get("id").getAsString() : null;
            String token = request.has("token") ? request.get("token").getAsString() : null;

            // 处理心跳
            if ("ping".equals(action)) {
                sendResponse(conn, requestId, true, "pong", null);
                return;
            }

            // 鉴权检查 (部分 action 需要鉴权)
            boolean isAuthRequired = action.startsWith("admin/") || "metrics".equals(action) || "history".equals(action);
            if (isAuthRequired) {
                if (token == null || !tokenManager.validate(token)) {
                    sendResponse(conn, requestId, false, "Unauthorized", null);
                    return;
                }
                authenticatedSessions.add(conn);
            }

            handleAction(conn, action, requestId, request.getAsJsonObject("data"), token);
        } catch (Exception e) {
            sendResponse(conn, null, false, "Invalid Request: " + e.getMessage(), null);
        }
    }

    private void handleAction(WebSocket conn, String action, String requestId, JsonObject data, String token) {
        switch (action) {
            case "status":
                handleStatus(conn, requestId);
                break;
            case "metrics":
                handleMetrics(conn, requestId);
                break;
            case "history":
                handleHistory(conn, requestId, data);
                break;
            case "admin/command":
                handleCommand(conn, requestId, data, token);
                break;
            case "admin/restart":
                handleRestart(conn, requestId);
                break;
            case "admin/shutdown":
                handleShutdown(conn, requestId);
                break;
            case "admin/whitelist":
                handleWhitelist(conn, requestId);
                break;
            case "admin/whitelist/toggle":
                handleWhitelistToggle(conn, requestId, data);
                break;
            case "admin/whitelist/add":
                handleWhitelistAdd(conn, requestId, data);
                break;
            case "admin/whitelist/remove":
                handleWhitelistRemove(conn, requestId, data);
                break;
            case "admin/logs/subscribe":
                handleLogsSubscribe(conn, requestId);
                break;
            default:
                sendResponse(conn, requestId, false, "Unknown action: " + action, null);
                break;
        }
    }

    private void handleStatus(WebSocket conn, String requestId) {
        Map<String, Object> status = new HashMap<>();
        status.put("online", true);
        status.put("motd", Bukkit.getMotd());
        status.put("motd_plain", Bukkit.getMotd().replaceAll("§[0-9a-fk-or]", ""));
        status.put("version", Bukkit.getVersion());
        status.put("bukkit_version", Bukkit.getBukkitVersion());
        status.put("players", Bukkit.getOnlinePlayers().size());
        status.put("max_players", Bukkit.getMaxPlayers());
        status.put("gamemode", Bukkit.getDefaultGameMode().toString());
        
        String iconBase64 = getServerIconBase64();
        if (iconBase64 != null) {
            status.put("icon", "data:image/png;base64," + iconBase64);
        }

        if (ServerSee.getInstance().getConfig().getBoolean("show-plugins", false)) {
            List<String> plugins = new ArrayList<>();
            for (Plugin p : Bukkit.getPluginManager().getPlugins()) {
                plugins.add(p.getName() + " v" + p.getDescription().getVersion());
            }
            status.put("plugins", plugins);
        }
        sendResponse(conn, requestId, true, null, status);
    }

    private void handleMetrics(WebSocket conn, String requestId) {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("tps_5s", sparkManager.getTps5s());
        metrics.put("tps_1m", sparkManager.getTps1m());
        metrics.put("mspt", sparkManager.getMspt());
        metrics.put("cpu_process", sparkManager.getCpuProcess());
        metrics.put("cpu_system", sparkManager.getCpuSystem());
        metrics.put("mem_used", sparkManager.getMemoryUsed());
        metrics.put("mem_total", sparkManager.getMemoryTotal());
        metrics.put("mem_max", sparkManager.getMemoryMax());
        metrics.put("host_mem_used", sparkManager.getHostMemoryUsed());
        metrics.put("host_mem_total", sparkManager.getHostMemoryTotal());
        metrics.put("disk_used", sparkManager.getDiskUsed());
        metrics.put("disk_total", sparkManager.getDiskTotal());
        sendResponse(conn, requestId, true, null, metrics);
    }

    private void handleHistory(WebSocket conn, String requestId, JsonObject data) {
        int limit = 60;
        if (data != null && data.has("limit")) {
            limit = data.get("limit").getAsInt();
        }
        sendResponse(conn, requestId, true, null, databaseManager.getRecentMetrics(limit));
    }

    private void handleCommand(WebSocket conn, String requestId, JsonObject data, String token) {
        if (data == null || !data.has("command")) {
            sendResponse(conn, requestId, false, "Missing command", null);
            return;
        }
        String command = data.get("command").getAsString();
        String ip = conn.getRemoteSocketAddress().getAddress().getHostAddress();
        String tokenSnippet = (token != null && token.length() > 8) ? token.substring(0, 8) + "..." : "none";
        ServerSee.getInstance().getLogger().info(String.format("[Audit] IP %s 使用 Token (%s) 执行了命令: %s", ip, tokenSnippet, command));
        
        Bukkit.getScheduler().runTask(ServerSee.getInstance(), () -> {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        });
        sendResponse(conn, requestId, true, "Command sent", Map.of("command", command));
    }

    private void handleRestart(WebSocket conn, String requestId) {
        String restartCommand = ServerSee.getInstance().getConfig().getString("restart-command", "restart");
        sendResponse(conn, requestId, true, "Server restarting", Map.of("command", restartCommand));
        Bukkit.getScheduler().runTaskLater(ServerSee.getInstance(), () -> {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), restartCommand);
        }, 20L);
    }

    private void handleShutdown(WebSocket conn, String requestId) {
        sendResponse(conn, requestId, true, "Server shutting down", null);
        Bukkit.getScheduler().runTaskLater(ServerSee.getInstance(), Bukkit::shutdown, 20L);
    }

    private void handleWhitelist(WebSocket conn, String requestId) {
        sendResponse(conn, requestId, true, null, Map.of(
            "enabled", Bukkit.hasWhitelist(),
            "players", Bukkit.getWhitelistedPlayers().stream().map(OfflinePlayer::getName).toList()
        ));
    }

    private void handleWhitelistToggle(WebSocket conn, String requestId, JsonObject data) {
        boolean enabled = data != null && data.has("enabled") && data.get("enabled").getAsBoolean();
        Bukkit.setWhitelist(enabled);
        sendResponse(conn, requestId, true, "Whitelist status updated", Map.of("enabled", enabled));
    }

    private void handleWhitelistAdd(WebSocket conn, String requestId, JsonObject data) {
        if (data == null || !data.has("name")) {
            sendResponse(conn, requestId, false, "Missing player name", null);
            return;
        }
        String name = data.get("name").getAsString();
        Bukkit.getOfflinePlayer(name).setWhitelisted(true);
        sendResponse(conn, requestId, true, "Player added to whitelist", Map.of("name", name));
    }

    private void handleWhitelistRemove(WebSocket conn, String requestId, JsonObject data) {
        if (data == null || !data.has("name")) {
            sendResponse(conn, requestId, false, "Missing player name", null);
            return;
        }
        String name = data.get("name").getAsString();
        Bukkit.getOfflinePlayer(name).setWhitelisted(false);
        sendResponse(conn, requestId, true, "Player removed from whitelist", Map.of("name", name));
    }

    private void handleLogsSubscribe(WebSocket conn, String requestId) {
        authenticatedSessions.add(conn);
        int historyLines = ServerSee.getInstance().getConfig().getInt("log-history-lines", 50);
        List<String> latestLogs = readLatestLogs(historyLines);
        for (String log : latestLogs) {
            broadcastPush(conn, "log", log);
        }
        sendResponse(conn, requestId, true, "Subscribed to logs", null);
    }

    private void sendResponse(WebSocket conn, String requestId, boolean success, String message, Object data) {
        JsonObject response = new JsonObject();
        response.addProperty("id", requestId);
        response.addProperty("type", "response");
        response.addProperty("success", success);
        if (message != null) response.addProperty("message", message);
        if (data != null) response.add("data", gson.toJsonTree(data));
        conn.send(gson.toJson(response));
    }

    private void broadcastPush(WebSocket conn, String action, Object data) {
        JsonObject push = new JsonObject();
        push.addProperty("type", "push");
        push.addProperty("action", action);
        push.add("data", gson.toJsonTree(data));
        conn.send(gson.toJson(push));
    }

    public void broadcastLog(String message) {
        for (WebSocket session : authenticatedSessions) {
            if (session.isOpen()) {
                broadcastPush(session, "log", message);
            }
        }
    }

    private List<String> readLatestLogs(int lines) {
        List<String> result = new ArrayList<>();
        File logFile = new File("logs" + File.separator + "latest.log");
        if (!logFile.exists()) return result;

        try (RandomAccessFile raf = new RandomAccessFile(logFile, "r")) {
            long length = raf.length();
            if (length == 0) return result;

            long pos = length - 1;
            int count = 0;
            ByteArrayOutputStream bos = new ByteArrayOutputStream();

            while (pos >= 0 && count < lines) {
                raf.seek(pos);
                int b = raf.read();
                if (b == '\n') {
                    if (bos.size() > 0) {
                        byte[] bytes = bos.toByteArray();
                        // 翻转字节数组，因为我们是倒着读的
                        for (int i = 0; i < bytes.length / 2; i++) {
                            byte temp = bytes[i];
                            bytes[i] = bytes[bytes.length - 1 - i];
                            bytes[bytes.length - 1 - i] = temp;
                        }
                        result.add(0, new String(bytes, StandardCharsets.UTF_8).trim());
                        bos.reset();
                        count++;
                    }
                } else if (b != '\r') {
                    bos.write(b);
                }
                pos--;
            }
            if (bos.size() > 0 && count < lines) {
                byte[] bytes = bos.toByteArray();
                for (int i = 0; i < bytes.length / 2; i++) {
                    byte temp = bytes[i];
                    bytes[i] = bytes[bytes.length - 1 - i];
                    bytes[bytes.length - 1 - i] = temp;
                }
                result.add(0, new String(bytes, StandardCharsets.UTF_8).trim());
            }
        } catch (IOException e) {
            ServerSee.getInstance().getLogger().warning("无法读取日志文件: " + e.getMessage());
        }
        return result;
    }

    private String getServerIconBase64() {
        long now = System.currentTimeMillis();
        if (cachedIconBase64 != null && now - lastIconUpdate < ICON_CACHE_MS) {
            return cachedIconBase64;
        }

        try {
            File iconFile = new File("server-icon.png");
            if (iconFile.exists()) {
                BufferedImage image = ImageIO.read(iconFile);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(image, "png", baos);
                cachedIconBase64 = Base64.getEncoder().encodeToString(baos.toByteArray());
                lastIconUpdate = now;
                return cachedIconBase64;
            }
        } catch (Exception e) {
            ServerSee.getInstance().getLogger().warning("无法读取服务器图标: " + e.getMessage());
        }
        return null;
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        if (conn != null) authenticatedSessions.remove(conn);
    }

    @Override
    public void onStart() {
        ServerSee.getInstance().getLogger().info("WebSocket API 服务已启动，监听端口: " + getPort());
    }
}
