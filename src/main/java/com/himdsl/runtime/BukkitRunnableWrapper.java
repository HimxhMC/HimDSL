package com.himdsl.runtime;

import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class BukkitRunnableWrapper {
    private final Plugin plugin;
    private final Map<String, BukkitTask> runningTasks = new HashMap<>();

    public BukkitRunnableWrapper(Plugin plugin) { this.plugin = plugin; }

    /**
     * 调度任务
     * @param funcName 函数名（用于 stop）
     * @param runnable 要执行的代码
     * @param schedule 定时表达式（如 "5s"），null 表示只执行一次
     * @param isAsync 是否异步（总是 true 因为 @BukkitRunnable）
     * @return stop 函数 (Runnable)
     */
    public Object schedule(String funcName, Runnable runnable, String schedule, boolean isAsync) {
        long period = 0;
        boolean repeat = schedule != null && !schedule.isEmpty();
        if (repeat) {
            period = parseTime(schedule);
            if (period <= 0) period = 1;
        }
        BukkitTask task;
        if (repeat) {
            task = new BukkitRunnable() {
                @Override
                public void run() { runnable.run(); }
            }.runTaskTimer(plugin, 0, period);
        } else {
            if (isAsync) {
                task = new BukkitRunnable() {
                    @Override
                    public void run() { runnable.run(); }
                }.runTaskAsynchronously(plugin);
            } else {
                task = new BukkitRunnable() {
                    @Override
                    public void run() { runnable.run(); }
                }.runTask(plugin);
            }
        }
        runningTasks.put(funcName, task);
        return (Runnable) () -> {
            BukkitTask t = runningTasks.remove(funcName);
            if (t != null) t.cancel();
        };
    }

    private long parseTime(String timeStr) {
        timeStr = timeStr.trim();
        if (timeStr.endsWith("s")) {
            return Long.parseLong(timeStr.substring(0, timeStr.length()-1)) * 20;
        } else if (timeStr.endsWith("t")) {
            return Long.parseLong(timeStr.substring(0, timeStr.length()-1));
        } else if (timeStr.endsWith("ms")) {
            return Long.parseLong(timeStr.substring(0, timeStr.length()-2)) / 50;
        } else {
            return Long.parseLong(timeStr);
        }
    }
}