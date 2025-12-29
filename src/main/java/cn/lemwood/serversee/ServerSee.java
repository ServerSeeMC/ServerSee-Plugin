package cn.lemwood.serversee;

import org.bukkit.plugin.java.JavaPlugin;
import cn.lemwood.serversee.api.ApiServer;
import cn.lemwood.serversee.auth.TokenManager;
import cn.lemwood.serversee.database.DatabaseManager;
import cn.lemwood.serversee.metrics.SparkManager;

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

            getLogger().info("ServerSee API 已启动，监听端口: " + port);
        });

        getLogger().info("ServerSee 插件已加载，等待初始化...");
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
            apiServer.stop();
        }
        getLogger().info("ServerSee 已禁用。");
    }

    public static ServerSee getInstance() {
        return instance;
    }
}
