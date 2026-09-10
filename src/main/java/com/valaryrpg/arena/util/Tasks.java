package com.valaryrpg.arena.util;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/** Scheduler unico: copy/SQL async, load/TP/Mythic sync. */
public final class Tasks {

    private final JavaPlugin plugin;

    public Tasks(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void sync(Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
            return;
        }
        Bukkit.getScheduler().runTask(this.plugin, task);
    }

    public void async(Runnable task) {
        Bukkit.getScheduler().runTaskAsynchronously(this.plugin, task);
    }

    public BukkitTask later(Runnable task, long ticks) {
        return Bukkit.getScheduler().runTaskLater(this.plugin, task, ticks);
    }

    public BukkitTask laterAsync(Runnable task, long ticks) {
        return Bukkit.getScheduler().runTaskLaterAsynchronously(this.plugin, task, ticks);
    }

    public BukkitTask repeat(Runnable task, long delay, long period) {
        return Bukkit.getScheduler().runTaskTimer(this.plugin, task, delay, period);
    }
}
