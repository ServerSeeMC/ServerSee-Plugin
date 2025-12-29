package cn.lemwood.serversee.metrics;

import me.lucko.spark.api.Spark;
import me.lucko.spark.api.SparkProvider;
import me.lucko.spark.api.statistic.StatisticWindow;
import me.lucko.spark.api.statistic.misc.DoubleAverageInfo;
import me.lucko.spark.api.statistic.types.GenericStatistic;
import oshi.SystemInfo;
import oshi.hardware.GlobalMemory;
import oshi.software.os.FileSystem;
import oshi.software.os.OSFileStore;

import java.io.File;

public class SparkManager {
    private Spark spark;
    private final SystemInfo systemInfo;

    public SparkManager() {
        this.systemInfo = new SystemInfo();
    }

    public boolean initialize() {
        try {
            this.spark = SparkProvider.get();
            return this.spark != null;
        } catch (IllegalStateException e) {
            return false;
        }
    }

    public double getTps5s() {
        return getTps(StatisticWindow.TicksPerSecond.SECONDS_5);
    }

    public double getTps1m() {
        return getTps(StatisticWindow.TicksPerSecond.MINUTES_1);
    }

    public double getMspt() {
        if (spark == null) return 0.0;
        GenericStatistic<DoubleAverageInfo, StatisticWindow.MillisPerTick> mspt = spark.mspt();
        return mspt != null ? mspt.poll(StatisticWindow.MillisPerTick.MINUTES_1).mean() : 0.0;
    }

    public double getCpuProcess() {
        if (spark == null || spark.cpuProcess() == null) return 0.0;
        return spark.cpuProcess().poll(StatisticWindow.CpuUsage.MINUTES_1) * 100.0;
    }

    public double getCpuSystem() {
        if (spark == null || spark.cpuSystem() == null) return 0.0;
        return spark.cpuSystem().poll(StatisticWindow.CpuUsage.MINUTES_1) * 100.0;
    }

    private double getTps(StatisticWindow.TicksPerSecond window) {
        if (spark == null || spark.tps() == null) return 0.0;
        return spark.tps().poll(window);
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

    // Host Memory (System Memory)
    public double getHostMemoryTotal() {
        try {
            return systemInfo.getHardware().getMemory().getTotal() / 1024.0 / 1024.0;
        } catch (Throwable e) {
            return 0.0;
        }
    }

    public double getHostMemoryUsed() {
        try {
            GlobalMemory memory = systemInfo.getHardware().getMemory();
            return (memory.getTotal() - memory.getAvailable()) / 1024.0 / 1024.0;
        } catch (Throwable e) {
            return 0.0;
        }
    }

    // Disk Usage (Current Partition)
    public double getDiskTotal() {
        try {
            File file = new File(".");
            return file.getTotalSpace() / 1024.0 / 1024.0 / 1024.0; // GB
        } catch (Throwable e) {
            return 0.0;
        }
    }

    public double getDiskUsed() {
        try {
            File file = new File(".");
            return (file.getTotalSpace() - file.getFreeSpace()) / 1024.0 / 1024.0 / 1024.0; // GB
        } catch (Throwable e) {
            return 0.0;
        }
    }

    public Spark getSpark() {
        return spark;
    }
}
