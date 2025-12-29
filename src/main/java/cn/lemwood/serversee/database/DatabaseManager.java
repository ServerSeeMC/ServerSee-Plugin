package cn.lemwood.serversee.database;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.io.File;

public class DatabaseManager {
    private final String url;

    public DatabaseManager(File dataFolder) {
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        this.url = "jdbc:sqlite:" + new File(dataFolder, "data.db").getAbsolutePath();
        initialize();
    }

    private void initialize() {
        String sql = "CREATE TABLE IF NOT EXISTS metrics (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP," +
                "tps REAL," +
                "mspt REAL," +
                "cpu_process REAL," +
                "cpu_system REAL," +
                "memory_used REAL," +
                "memory_max REAL" +
                ");";
        try (Connection conn = DriverManager.getConnection(url);
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void saveMetrics(double tps, double mspt, double cpuProcess, double cpuSystem, double memUsed, double memMax) {
        String sql = "INSERT INTO metrics(tps, mspt, cpu_process, cpu_system, memory_used, memory_max) VALUES(?,?,?,?,?,?)";
        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setDouble(1, tps);
            pstmt.setDouble(2, mspt);
            pstmt.setDouble(3, cpuProcess);
            pstmt.setDouble(4, cpuSystem);
            pstmt.setDouble(5, memUsed);
            pstmt.setDouble(6, memMax);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public List<Map<String, Object>> getRecentTps(int limit) {
        List<Map<String, Object>> results = new ArrayList<>();
        String sql = "SELECT timestamp, tps FROM metrics ORDER BY timestamp DESC LIMIT ?";
        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, limit);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                Map<String, Object> map = new HashMap<>();
                map.put("timestamp", rs.getString("timestamp"));
                map.put("tps", rs.getDouble("tps"));
                results.add(map);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return results;
    }

    public List<Map<String, Object>> getRecentMetrics(int limit) {
        List<Map<String, Object>> results = new ArrayList<>();
        String sql = "SELECT * FROM metrics ORDER BY timestamp DESC LIMIT ?";
        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, limit);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                Map<String, Object> map = new HashMap<>();
                map.put("timestamp", rs.getString("timestamp"));
                map.put("tps", rs.getDouble("tps"));
                map.put("mspt", rs.getDouble("mspt"));
                map.put("cpu_process", rs.getDouble("cpu_process"));
                map.put("cpu_system", rs.getDouble("cpu_system"));
                map.put("memory_used", rs.getDouble("memory_used"));
                map.put("memory_max", rs.getDouble("memory_max"));
                results.add(map);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return results;
    }
}
