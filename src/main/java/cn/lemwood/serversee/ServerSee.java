package cn.lemwood.serversee;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import cn.lemwood.serversee.api.ApiServer;
import cn.lemwood.serversee.auth.TokenManager;
import cn.lemwood.serversee.database.DatabaseManager;
import cn.lemwood.serversee.metrics.SparkManager;

import java.util.logging.Handler;
import java.util.logging.LogRecord;

public class ServerSee extends JavaPlugin {
    private static ServerSee instance;
    private SparkManager sparkManager;
    private ApiServer apiServer;
    private DatabaseManager databaseManager;
    private TokenManager tokenManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        // 初始化 Token 管理器
        tokenManager = new TokenManager(getDataFolder(), getLogger());

        // 初始化数据库
        databaseManager = new DatabaseManager(getDataFolder());

        // 延迟初始化，确保内置 spark 已就绪
        getServer().getScheduler().runTask(this, () -> {
            sparkManager = new SparkManager();
            if (!sparkManager.initialize()) {
                getLogger().severe("未能找到 Spark API，API 功能将不可用。");
                return;
            }

            // 启动 API 服务器
            int port = getConfig().getInt("api-port", 8080);
            apiServer = new ApiServer(port, sparkManager, databaseManager, tokenManager);
            apiServer.start();

            // 启动异步采集任务
            startCollectionTask();
            
            // 设置日志捕获
            setupLogCapture();

            getLogger().info("ServerSee API 已启动，监听端口: " + port);
        });

        getLogger().info("ServerSee 插件已加载，等待初始化...");
    }

    private void setupLogCapture() {
        if (apiServer == null) return;
        
        Handler logHandler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (apiServer != null) {
                    // 获取格式化后的消息
                    String message = record.getMessage();
                    Object[] params = record.getParameters();
                    if (params != null && params.length > 0) {
                        try {
                            message = java.text.MessageFormat.format(message, params);
                        } catch (Exception ignored) {}
                    }
                    
                    String formatted = String.format("[%s] %s", record.getLevel().getName(), message);
                    apiServer.broadcastLog(formatted);
                }
            }

            @Override
            public void flush() {}

            @Override
            public void close() throws SecurityException {}
        };
        
        // 捕获根日志记录器的日志，以获取更多信息
        getServer().getLogger().addHandler(logHandler);
        Bukkit.getLogger().addHandler(logHandler);
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
