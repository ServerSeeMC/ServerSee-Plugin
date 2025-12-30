package cn.lemwood.serversee;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class UpdateChecker {
    private final JavaPlugin plugin;
    private final String repo = "ServerSeeMC/ServerSee-Plugin";
    private final String currentVersion;

    public UpdateChecker(JavaPlugin plugin) {
        this.plugin = plugin;
        this.currentVersion = plugin.getDescription().getVersion();
    }

    public void check() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                HttpURLConnection connection = (HttpURLConnection) new URL("https://api.github.com/repos/" + repo + "/releases").openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("User-Agent", "ServerSee-Plugin-UpdateChecker");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);

                if (connection.getResponseCode() == 200) {
                    try (InputStreamReader reader = new InputStreamReader(connection.getInputStream())) {
                        JsonArray releases = JsonParser.parseReader(reader).getAsJsonArray();
                        if (releases.size() > 0) {
                            JsonElement latestRelease = releases.get(0);
                            String latestTag = latestRelease.getAsJsonObject().get("tag_name").getAsString();
                            
                            // 去掉 'v' 前缀进行比较
                            String latestVersion = latestTag.startsWith("v") ? latestTag.substring(1) : latestTag;
                            
                            if (isNewer(latestVersion, currentVersion)) {
                                plugin.getLogger().info("========================================");
                                plugin.getLogger().info("检测到新版本: v" + latestVersion);
                                plugin.getLogger().info("当前版本: v" + currentVersion);
                                plugin.getLogger().info("下载地址: https://github.com/" + repo + "/releases/latest");
                                plugin.getLogger().info("========================================");
                            }
                        }
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("检查更新失败: " + e.getMessage());
            }
        });
    }

    private boolean isNewer(String latest, String current) {
        // 简单的版本号比较，这里可以根据实际情况改进
        // 如果相同则不更新
        if (latest.equals(current)) return false;
        
        String[] latestParts = latest.split("[-.]");
        String[] currentParts = current.split("[-.]");
        
        int length = Math.max(latestParts.length, currentParts.length);
        for (int i = 0; i < length; i++) {
            if (i >= latestParts.length) return false;
            if (i >= currentParts.length) return true;
            
            try {
                int latestNum = Integer.parseInt(latestParts[i]);
                int currentNum = Integer.parseInt(currentParts[i]);
                if (latestNum > currentNum) return true;
                if (latestNum < currentNum) return false;
            } catch (NumberFormatException e) {
                // 如果不是数字，按字符串比较
                int res = latestParts[i].compareTo(currentParts[i]);
                if (res > 0) return true;
                if (res < 0) return false;
            }
        }
        return false;
    }
}
