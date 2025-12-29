package cn.lemwood.serversee.api;

import cn.lemwood.serversee.ServerSee;
import cn.lemwood.serversee.auth.TokenManager;
import cn.lemwood.serversee.database.DatabaseManager;
import cn.lemwood.serversee.metrics.SparkManager;
import io.javalin.Javalin;
import io.javalin.http.HttpStatus;
import io.javalin.http.UnauthorizedResponse;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.CachedServerIcon;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ApiServer {
    private final int port;
    private final SparkManager sparkManager;
    private final DatabaseManager databaseManager;
    private final TokenManager tokenManager;
    private Javalin app;

    public ApiServer(int port, SparkManager sparkManager, DatabaseManager databaseManager, TokenManager tokenManager) {
        this.port = port;
        this.sparkManager = sparkManager;
        this.databaseManager = databaseManager;
        this.tokenManager = tokenManager;
    }

    public void start() {
        ClassLoader oldClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(ApiServer.class.getClassLoader());

        app = Javalin.create(config -> {
            config.showJavalinBanner = false;
        }).start(port);

        setupRoutes();

        Thread.currentThread().setContextClassLoader(oldClassLoader);
    }

    private void setupRoutes() {
        // 鉴权中间件
        app.before("/admin/*", ctx -> {
            String token = ctx.header("Authorization");
            if (token != null && token.startsWith("Bearer ")) {
                token = token.substring(7);
            }
            
            if (!tokenManager.validate(token)) {
                throw new UnauthorizedResponse("未授权的访问，请提供有效的 Token");
            }
        });

        app.get("/status", ctx -> {
            Map<String, Object> status = new HashMap<>();
            status.put("online", true);
            status.put("motd", Bukkit.getMotd());
            status.put("version", Bukkit.getVersion());
            status.put("bukkit_version", Bukkit.getBukkitVersion());
            status.put("players", Bukkit.getOnlinePlayers().size());
            status.put("max_players", Bukkit.getMaxPlayers());
            status.put("gamemode", Bukkit.getDefaultGameMode().toString());
            
            // 获取服务器图标 (Base64)
            String iconBase64 = getServerIconBase64();
            if (iconBase64 != null) {
                status.put("icon", "data:image/png;base64," + iconBase64);
            }

            // 是否输出插件列表
            if (ServerSee.getInstance().getConfig().getBoolean("show-plugins", false)) {
                List<String> plugins = new ArrayList<>();
                for (Plugin p : Bukkit.getPluginManager().getPlugins()) {
                    plugins.add(p.getName() + " v" + p.getDescription().getVersion());
                }
                status.put("plugins", plugins);
            }

            ctx.json(status);
        });

        app.get("/metrics", ctx -> {
            Map<String, Object> metrics = new HashMap<>();
            metrics.put("tps_5s", sparkManager.getTps5s());
            metrics.put("tps_1m", sparkManager.getTps1m());
            metrics.put("mspt", sparkManager.getMspt());
            metrics.put("cpu_process", sparkManager.getCpuProcess());
            metrics.put("cpu_system", sparkManager.getCpuSystem());
            
            // JVM 内存信息 (服务器内存)
            metrics.put("mem_used", sparkManager.getMemoryUsed());
            metrics.put("mem_total", sparkManager.getMemoryTotal());
            metrics.put("mem_max", sparkManager.getMemoryMax());

            // 主机内存信息 (Host Memory)
            metrics.put("host_mem_used", sparkManager.getHostMemoryUsed());
            metrics.put("host_mem_total", sparkManager.getHostMemoryTotal());

            // 磁盘信息 (Disk Info)
            metrics.put("disk_used", sparkManager.getDiskUsed());
            metrics.put("disk_total", sparkManager.getDiskTotal());

            ctx.json(metrics);
        });

        app.get("/history", ctx -> {
            int limit = 60; // 默认返回最近60条记录
            if (ctx.queryParam("limit") != null) {
                try {
                    limit = Integer.parseInt(ctx.queryParam("limit"));
                } catch (NumberFormatException ignored) {}
            }
            ctx.json(databaseManager.getRecentMetrics(limit));
        });

        app.get("/ping", ctx -> ctx.result("pong"));

        // OP 级别功能 (受保护)
        
        // 执行命令
        app.post("/admin/command", ctx -> {
            String command = ctx.formParam("command");
            if (command == null || command.isEmpty()) {
                ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", "未提供命令"));
                return;
            }
            
            // 在主线程执行命令
            Bukkit.getScheduler().runTask(ServerSee.getInstance(), () -> {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            });
            
            ctx.json(Map.of("message", "命令已发送至控制台", "command", command));
        });

        // 重启服务器
        app.post("/admin/restart", ctx -> {
            String restartCommand = ServerSee.getInstance().getConfig().getString("restart-command", "restart");
            ctx.json(Map.of("message", "服务器正在重启...", "command", restartCommand));
            Bukkit.getScheduler().runTaskLater(ServerSee.getInstance(), () -> {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), restartCommand);
            }, 20L);
        });

        // 关闭服务器
        app.post("/admin/shutdown", ctx -> {
            ctx.json(Map.of("message", "服务器正在关闭..."));
            Bukkit.getScheduler().runTaskLater(ServerSee.getInstance(), () -> {
                Bukkit.shutdown();
            }, 20L);
        });

        // 白名单管理
        app.get("/admin/whitelist", ctx -> {
            ctx.json(Map.of(
                "enabled", Bukkit.hasWhitelist(),
                "players", Bukkit.getWhitelistedPlayers().stream().map(p -> p.getName()).toList()
            ));
        });

        app.post("/admin/whitelist/toggle", ctx -> {
            boolean enabled = Boolean.parseBoolean(ctx.formParam("enabled"));
            Bukkit.setWhitelist(enabled);
            ctx.json(Map.of("message", "白名单状态已更新", "enabled", enabled));
        });

        app.post("/admin/whitelist/add", ctx -> {
            String name = ctx.formParam("name");
            if (name != null) {
                Bukkit.getOfflinePlayer(name).setWhitelisted(true);
                ctx.json(Map.of("message", "玩家已加入白名单", "name", name));
            } else {
                ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", "未提供玩家名"));
            }
        });

        app.post("/admin/whitelist/remove", ctx -> {
            String name = ctx.formParam("name");
            if (name != null) {
                Bukkit.getOfflinePlayer(name).setWhitelisted(false);
                ctx.json(Map.of("message", "玩家已移出白名单", "name", name));
            } else {
                ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", "未提供玩家名"));
            }
        });
    }

    private String getServerIconBase64() {
        try {
            File iconFile = new File("server-icon.png");
            if (iconFile.exists()) {
                BufferedImage image = ImageIO.read(iconFile);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(image, "png", baos);
                return Base64.getEncoder().encodeToString(baos.toByteArray());
            }
        } catch (Exception e) {
            ServerSee.getInstance().getLogger().warning("无法读取服务器图标: " + e.getMessage());
        }
        return null;
    }

    public void stop() {
        if (app != null) {
            app.stop();
        }
    }
}
