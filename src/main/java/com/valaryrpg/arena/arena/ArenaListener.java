package com.valaryrpg.arena.arena;

import com.valaryrpg.arena.milestone.KillTracker;
import io.lumine.mythic.bukkit.events.MythicMobDeathEvent;
import io.lumine.mythic.bukkit.events.MythicPostReloadedEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/** Quit, leave mondo, death/respawn e kill Mythic. Niente PlayerMoveEvent. */
public final class ArenaListener implements Listener {

    private final ArenaService arenas;
    private final KillTracker kills;

    public ArenaListener(ArenaService arenas, KillTracker kills) {
        this.arenas = arenas;
        this.kills = kills;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        this.kills.evict(player.getUniqueId());
        this.arenas.quit(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        ArenaInstance instance = this.arenas.instanceOf(player);
        if (instance == null) {
            return;
        }
        if (instance.loadedWorldName() == null
                || !instance.loadedWorldName().equals(player.getWorld().getName())) {
            this.arenas.leave(player, false);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (this.arenas.instanceOf(player) == null) {
            return;
        }
        this.arenas.eliminate(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        var location = this.arenas.eliminatedRespawn(event.getPlayer());
        if (location != null) {
            event.setRespawnLocation(location);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMythicReload(MythicPostReloadedEvent event) {
        this.arenas.refreshMythicSpawners();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMythicDeath(MythicMobDeathEvent event) {
        if (!(event.getKiller() instanceof Player player)) {
            return;
        }
        if (this.arenas.instanceOf(player) == null) {
            return;
        }
        String mobId = event.getMobType().getInternalName();
        this.kills.record(player, mobId);
    }
}
