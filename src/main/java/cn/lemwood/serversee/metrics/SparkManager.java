package cn.lemwood.serversee.metrics;

import oshi.SystemInfo;
import oshi.hardware.GlobalMemory;

import java.io.File;
import java.lang.management.ManagementFactory;
import com.sun.management.OperatingSystemMXBean;

public class SparkManager {
    private final TickMonitor tickMonitor;
    private final SystemInfo systemInfo;
    private final OperatingSystemMXBean osBean;
    
    // 缓存硬件指标
    private long lastHardwareUpdate = 0;
    private double cachedHostMemTotal = 0;
    private double cachedHostMemUsed = 0;
    private double cachedDiskTotal = 0;
    private double cachedDiskUsed = 0;
    private static final long HARDWARE_CACHE_MS = 10000; // 10秒缓存

    public SparkManager(TickMonitor tickMonitor) {
        this.tickMonitor = tickMonitor;
        this.systemInfo = new SystemInfo();
        this.osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
    }

    public boolean initialize() {
        // 始终返回 true，因为我们不再依赖外部 Spark 插件
        return true;
    }

    public double getTps5s() {
        return tickMonitor != null ? tickMonitor.getTps5s() : 0.0;
    }

    public double getTps1m() {
        return tickMonitor != null ? tickMonitor.getTps1m() : 0.0;
    }

    public double getMspt() {
        return tickMonitor != null ? tickMonitor.getMspt() : 0.0;
    }

    public double getCpuProcess() {
        if (osBean == null) return 0.0;
        double load = osBean.getProcessCpuLoad();
        return (load < 0) ? 0.0 : load * 100.0;
    }

    public double getCpuSystem() {
        if (osBean == null) return 0.0;
        double load = osBean.getSystemCpuLoad();
        return (load < 0) ? 0.0 : load * 100.0;
    }

    // JVM Memory (Server Memory)
    public double getMemoryUsed() {
        Runtime runtime = Runtime.getRuntime();
        return (runtime.totalMemory() - runtime.freeMemory()) / 1024.0 / 1024.0;
    }

    public double getMemoryTotal() {
        return Runtime.getRuntime().totalMemory() / 1024.0 / 1024.0;
    }

    public double getMemoryMax() {
        return Runtime.getRuntime().maxMemory() / 1024.0 / 1024.0;
    }

    private void updateHardwareMetricsIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - lastHardwareUpdate < HARDWARE_CACHE_MS) return;

        try {
            GlobalMemory memory = systemInfo.getHardware().getMemory();
            cachedHostMemTotal = memory.getTotal() / 1024.0 / 1024.0;
            cachedHostMemUsed = (memory.getTotal() - memory.getAvailable()) / 1024.0 / 1024.0;

            File file = new File(".");
            cachedDiskTotal = file.getTotalSpace() / 1024.0 / 1024.0 / 1024.0;
            cachedDiskUsed = (file.getTotalSpace() - file.getFreeSpace()) / 1024.0 / 1024.0 / 1024.0;

            lastHardwareUpdate = now;
        } catch (Throwable ignored) {}
    }

    // Host Memory (System Memory)
    public double getHostMemoryTotal() {
        updateHardwareMetricsIfNeeded();
        return cachedHostMemTotal;
    }

    public double getHostMemoryUsed() {
        updateHardwareMetricsIfNeeded();
        return cachedHostMemUsed;
    }

    // Disk Usage (Current Partition)
    public double getDiskTotal() {
        updateHardwareMetricsIfNeeded();
        return cachedDiskTotal;
    }

    public double getDiskUsed() {
        updateHardwareMetricsIfNeeded();
        return cachedDiskUsed;
    }
}
