package cn.lemwood.serversee;

import cn.lemwood.serversee.api.ApiServer;
import cn.lemwood.serversee.api.JULHandler;
import cn.lemwood.serversee.api.LogAppender;
import cn.lemwood.serversee.auth.TokenManager;
import cn.lemwood.serversee.database.DatabaseManager;
import cn.lemwood.serversee.metrics.SparkManager;
import cn.lemwood.serversee.metrics.TickMonitor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Logger;

public class ServerSee extends JavaPlugin {
    private static ServerSee instance;
    private SparkManager sparkManager;
    private ApiServer apiServer;
    private DatabaseManager databaseManager;
    private TokenManager tokenManager;
    private LogAppender logAppender;
    private JULHandler julHandler;
    private TickMonitor tickMonitor;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        // 初始化 Token 管理器
        tokenManager = new TokenManager(getDataFolder(), getLogger());

        // 初始化数据库
        databaseManager = new DatabaseManager(getDataFolder());

        // 初始化 Tick 监控器
        tickMonitor = new TickMonitor();
        tickMonitor.start(this);

        // 延迟初始化 API 服务器
        getServer().getScheduler().runTask(this, () -> {
            sparkManager = new SparkManager(tickMonitor);
            
            // 启动 API 服务器
            int port = getConfig().getInt("api-port", 8080);
            apiServer = new ApiServer(port, sparkManager, databaseManager, tokenManager);
            apiServer.start();

            // 启动异步采集任务
            startCollectionTask();
            
            // 设置日志捕获 (双重方案)
            setupLogCapture();

            getLogger().info("ServerSee API 已启动，监听端口: " + port);
        });

        getLogger().info("ServerSee 插件已加载，等待初始化...");
    }

    private void setupLogCapture() {
        if (apiServer == null) return;
        
        // 方案 1: Log4j2 (针对 Paper/Spigot 1.12+ 核心)
        try {
            Class.forName("org.apache.logging.log4j.core.LoggerContext");
            LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
            Configuration config = ctx.getConfiguration();
            
            logAppender = LogAppender.createAppender("ServerSeeAppender", null, null, apiServer);
            logAppender.start();
            
            config.addAppender(logAppender);
            config.getRootLogger().addAppender(logAppender, null, null);
            ctx.updateLoggers();
            getLogger().info("已启用 Log4j2 日志捕获。");
        } catch (NoClassDefFoundError | ClassNotFoundException e) {
            getLogger().info("未检测到 Log4j2，尝试使用 JUL 捕获日志。");
        } catch (Exception e) {
            getLogger().warning("设置 Log4j2 捕获时出错: " + e.getMessage());
        }

        // 方案 2: java.util.logging (作为备份或旧版本核心)
        try {
            julHandler = new JULHandler(apiServer);
            Logger rootLogger = Logger.getLogger("");
            rootLogger.addHandler(julHandler);
            getLogger().info("已启用 JUL 日志捕获。");
        } catch (Exception e) {
            getLogger().warning("无法设置 JUL 日志捕获: " + e.getMessage());
        }
    }

    private void startCollectionTask() {
        int interval = getConfig().getInt("collection-interval", 60);
        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            if (sparkManager != null) {
                databaseManager.saveMetrics(
                        sparkManager.getTps5s(),
                        sparkManager.getMspt(),
                        sparkManager.getCpuProcess(),
                        sparkManager.getCpuSystem(),
                        sparkManager.getMemoryUsed(),
                        sparkManager.getMemoryMax()
                );
            }
        }, 20L * interval, 20L * interval);
    }

    @Override
    public void onDisable() {
        // 停止 Log4j2 捕获
        if (logAppender != null) {
            try {
                LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
                Configuration config = ctx.getConfiguration();
                logAppender.stop();
                config.getRootLogger().removeAppender("ServerSeeAppender");
                ctx.updateLoggers();
            } catch (Exception ignored) {}
        }

        // 停止 JUL 捕获
        if (julHandler != null) {
            try {
                Logger rootLogger = Logger.getLogger("");
                rootLogger.removeHandler(julHandler);
            } catch (Exception ignored) {}
        }
        
        if (apiServer != null) {
            try {
                apiServer.stop(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        if (databaseManager != null) {
            databaseManager.close();
        }
        getLogger().info("ServerSee 已禁用。");
    }

    public static ServerSee getInstance() {
        return instance;
    }
}
