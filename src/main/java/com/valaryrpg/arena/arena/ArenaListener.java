package com.valaryrpg.arena.arena;

import com.valaryrpg.arena.command.CommandCooldowns;
import com.valaryrpg.arena.milestone.KillTracker;
import com.valaryrpg.arena.party.PartyService;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.bukkit.events.MythicMobDeathEvent;
import io.lumine.mythic.bukkit.events.MythicPostReloadedEvent;
import io.lumine.mythic.core.mobs.ActiveMob;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.ArrayList;
import java.util.UUID;

/** Quit, leave mondo, death/respawn e kill Mythic. Niente PlayerMoveEvent. */
public final class ArenaListener implements Listener {

    private final ArenaService arenas;
    private final KillTracker kills;
    private final PartyService parties;
    private final CommandCooldowns cooldowns;

    public ArenaListener(
            ArenaService arenas,
            KillTracker kills,
            PartyService parties,
            CommandCooldowns cooldowns
    ) {
        this.arenas = arenas;
        this.kills = kills;
        this.parties = parties;
        this.cooldowns = cooldowns;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        this.kills.evict(player.getUniqueId());
        this.cooldowns.evict(player.getUniqueId());
        this.arenas.quit(player);
        this.parties.leave(player);
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
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim)) {
            return;
        }
        ArenaInstance instance = this.arenas.instanceOf(victim.getWorld());
        if (instance == null || instance.state() != SlotState.ACTIVE) {
            return;
        }
        Player attacker = attacker(event.getDamager());
        if (attacker == null || !instance.players().contains(attacker.getUniqueId())) {
            return;
        }
        ActiveMob active = MythicBukkit.inst().getMobManager().getActiveMob(victim.getUniqueId()).orElse(null);
        if (active == null) {
            return;
        }
        String mythicName = active.getType() == null ? "" : active.getType().getInternalName();
        if (!this.arenas.catalog().tracksMob(this.arenas.catalog().boss(instance.bossId()), mythicName)) {
            return;
        }
        this.arenas.recordDamage(instance, victim.getUniqueId(), attacker, event.getFinalDamage());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMythicDeath(MythicMobDeathEvent event) {
        ActiveMob mob = event.getMob();
        Entity entity = mob == null || mob.getEntity() == null ? null : mob.getEntity().getBukkitEntity();
        ArenaInstance instance = entity == null ? null : this.arenas.instanceOf(entity.getWorld());
        Player killer = killer(event, entity);
        if (instance == null && killer != null) {
            instance = this.arenas.instanceOf(killer);
        }
        if (instance == null || instance.state() != SlotState.ACTIVE) {
            return;
        }
        String mythicName = event.getMobType().getInternalName();
        boolean tracked = this.arenas.catalog().tracksMob(this.arenas.catalog().boss(instance.bossId()), mythicName);
        String key = this.kills.keyFor(mythicName, instance.bossId(), tracked);
        if (tracked) {
            if (entity != null) {
                this.arenas.trackedBossKilled(instance, entity.getUniqueId());
            }
            for (UUID uuid : new ArrayList<>(instance.players())) {
                Player member = Bukkit.getPlayer(uuid);
                if (member != null) {
                    this.kills.record(member, key);
                }
            }
            return;
        }
        if (killer != null && instance.players().contains(killer.getUniqueId())) {
            this.kills.record(killer, key);
        }
    }

    private Player attacker(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        if (damager instanceof Tameable tameable && tameable.getOwner() instanceof Player player) {
            return player;
        }
        return null;
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
