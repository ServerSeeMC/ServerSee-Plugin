package cn.lemwood.serversee.metrics;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.LinkedList;

public class TickMonitor implements Runnable {
    private static final int WINDOW_SIZE_5S = 100; // 5 seconds (20 ticks per second)
    private static final int WINDOW_SIZE_1M = 1200; // 1 minute

    private final LinkedList<Long> tickDurations = new LinkedList<>();
    private final LinkedList<Long> tickTimestamps = new LinkedList<>();
    
    private long lastTickTime = 0;
    private double currentTps5s = 20.0;
    private double currentTps1m = 20.0;
    private double currentMspt = 0.0;

    public void start(Plugin plugin) {
        Bukkit.getScheduler().runTaskTimer(plugin, this, 1L, 1L);
    }

    @Override
    public void run() {
        long now = System.nanoTime();
        if (lastTickTime != 0) {
            long duration = now - lastTickTime;
            // Convert to milliseconds
            double durationMs = duration / 1_000_000.0;
            
            tickDurations.addLast(duration);
            tickTimestamps.addLast(now);

            // Keep windows within size
            while (tickDurations.size() > WINDOW_SIZE_1M) {
                tickDurations.removeFirst();
                tickTimestamps.removeFirst();
            }

            updateMetrics();
        }
        lastTickTime = now;
    }

    private void updateMetrics() {
        if (tickDurations.isEmpty()) return;

        // Calculate MSPT (1m window)
        double totalDurationMs = 0;
        for (Long d : tickDurations) {
            totalDurationMs += d / 1_000_000.0;
        }
        currentMspt = totalDurationMs / tickDurations.size();

        // Calculate TPS (5s window)
        int size5s = Math.min(tickDurations.size(), WINDOW_SIZE_5S);
        if (size5s > 1) {
            long timeEnd = tickTimestamps.get(tickTimestamps.size() - 1);
            long timeStart = tickTimestamps.get(tickTimestamps.size() - size5s);
            double seconds = (timeEnd - timeStart) / 1_000_000_000.0;
            if (seconds > 0) {
                currentTps5s = Math.min(20.0, (size5s - 1) / seconds);
            }
        }

        // Calculate TPS (1m window)
        int size1m = tickDurations.size();
        if (size1m > 1) {
            long timeEnd = tickTimestamps.get(tickTimestamps.size() - 1);
            long timeStart = tickTimestamps.get(0);
            double seconds = (timeEnd - timeStart) / 1_000_000_000.0;
            if (seconds > 0) {
                currentTps1m = Math.min(20.0, (size1m - 1) / seconds);
            }
        }
    }

    public double getTps5s() {
        return currentTps5s;
    }

    public double getTps1m() {
        return currentTps1m;
    }

    public double getMspt() {
        return currentMspt;
    }
}
