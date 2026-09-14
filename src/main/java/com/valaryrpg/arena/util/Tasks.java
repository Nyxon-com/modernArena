package com.valaryrpg.arena.util;

import org.bukkit.Bukkit;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.atomic.AtomicBoolean;

/** Scheduler unico: copy/SQL async, load/TP/Mythic sync. */
public final class Tasks {

    private final JavaPlugin plugin;
    private final AtomicBoolean accepting = new AtomicBoolean(true);

    public Tasks(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean sync(Runnable task) {
        if (!available()) {
            return false;
        }
        if (Bukkit.isPrimaryThread()) {
            task.run();
            return true;
        }
        try {
            Bukkit.getScheduler().runTask(this.plugin, guarded(task));
            return true;
        } catch (IllegalPluginAccessException ignored) {
            return false;
        }
    }

    public boolean async(Runnable task) {
        if (!available()) {
            return false;
        }
        try {
            Bukkit.getScheduler().runTaskAsynchronously(this.plugin, guarded(task));
            return true;
        } catch (IllegalPluginAccessException ignored) {
            return false;
        }
    }

    public BukkitTask later(Runnable task, long ticks) {
        if (!available()) {
            return null;
        }
        try {
            return Bukkit.getScheduler().runTaskLater(this.plugin, guarded(task), ticks);
        } catch (IllegalPluginAccessException ignored) {
            return null;
        }
    }

    public void shutdown() {
        if (this.accepting.compareAndSet(true, false)) {
            Bukkit.getScheduler().cancelTasks(this.plugin);
        }
    }

    private boolean available() {
        return this.accepting.get() && this.plugin.isEnabled();
    }

    private Runnable guarded(Runnable task) {
        return () -> {
            if (available()) {
                task.run();
            }
        };
    }
}
