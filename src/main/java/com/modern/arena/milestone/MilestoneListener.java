package com.modern.arena.milestone;

import com.modern.arena.ModernArenaPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Catch-up milestone al join + cleanup cache. */
public final class MilestoneListener implements Listener {

    private final ModernArenaPlugin plugin;
    private final KillTracker kills;

    public MilestoneListener(ModernArenaPlugin plugin, KillTracker kills) {
        this.plugin = plugin;
        this.kills = kills;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        // Delay: aspetta che il player sia pienamente online prima di dare le reward.
        this.plugin.getServer().getScheduler().runTaskLater(
                this.plugin,
                () -> this.kills.catchUp(event.getPlayer()),
                40L
        );
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        this.kills.evict(event.getPlayer().getUniqueId());
    }
}
