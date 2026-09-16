package com.valaryrpg.arena.dungeon;

import com.valaryrpg.arena.arena.SlotState;
import com.valaryrpg.arena.milestone.KillTracker;
import io.lumine.mythic.bukkit.events.MythicMobDeathEvent;
import io.lumine.mythic.bukkit.events.MythicPostReloadedEvent;
import io.lumine.mythic.core.mobs.ActiveMob;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.ArrayList;
import java.util.UUID;

/** Quit, leave mondo, death/respawn e kill Mythic nel dungeon. */
public final class DungeonListener implements Listener {

    private final DungeonService dungeons;
    private final KillTracker kills;

    public DungeonListener(DungeonService dungeons, KillTracker kills) {
        this.dungeons = dungeons;
        this.kills = kills;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        this.dungeons.quit(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        DungeonInstance instance = this.dungeons.instanceOf(player);
        if (instance == null) {
            return;
        }
        if (instance.loadedWorldName() == null
                || !instance.loadedWorldName().equals(player.getWorld().getName())) {
            this.dungeons.leave(player, false);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (this.dungeons.instanceOf(player) == null) {
            return;
        }
        this.dungeons.eliminate(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        var location = this.dungeons.eliminatedRespawn(event.getPlayer());
        if (location != null) {
            event.setRespawnLocation(location);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMythicReload(MythicPostReloadedEvent event) {
        this.dungeons.refreshMythicSpawners();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMythicDeath(MythicMobDeathEvent event) {
        ActiveMob mob = event.getMob();
        Entity entity = mob == null || mob.getEntity() == null ? null : mob.getEntity().getBukkitEntity();
        DungeonInstance instance = entity == null ? null : this.dungeons.instanceOf(entity.getWorld());
        Player killer = killer(event, entity);
        if (instance == null && killer != null) {
            instance = this.dungeons.instanceOf(killer);
        }
        if (instance == null || instance.state() != SlotState.ACTIVE) {
            return;
        }
        String mythicName = event.getMobType().getInternalName();
        DungeonDefinition definition = this.dungeons.catalog().dungeon(instance.dungeonId());
        DungeonRoom room = definition == null ? null : definition.room(instance.roomIndex());
        boolean boss = room != null && room.isBoss(mythicName);
        String key = this.kills.keyFor(mythicName, instance.dungeonId(), boss);
        if (boss) {
            for (UUID uuid : new ArrayList<>(instance.players())) {
                Player member = Bukkit.getPlayer(uuid);
                if (member != null) {
                    this.kills.record(member, key);
                }
            }
        } else if (killer != null && instance.players().contains(killer.getUniqueId())) {
            this.kills.record(killer, key);
        }
        this.dungeons.onMobDeath(instance, mythicName);
    }

    private Player killer(MythicMobDeathEvent event, Entity entity) {
        if (event.getKiller() instanceof Player player) {
            return player;
        }
        if (entity instanceof LivingEntity living && living.getKiller() instanceof Player player) {
            return player;
        }
        return null;
    }
}
