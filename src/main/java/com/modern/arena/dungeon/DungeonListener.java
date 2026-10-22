//made with love by nyxon

package com.modern.arena.dungeon;

import com.modern.arena.arena.SlotState;
import com.modern.arena.milestone.KillTracker;
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
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;

import java.util.ArrayList;
import java.util.UUID;

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

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void onPlayerPvP(EntityDamageByEntityEvent event) {
    if (!(event.getEntity() instanceof Player victim)) {
      return;
    }
    DungeonInstance instance = this.dungeons.instanceOf(victim.getWorld());
    if (instance == null || instance.state() != SlotState.ACTIVE) {
      return;
    }
    Player attacker = attacker(event.getDamager());
    if (attacker != null && !attacker.getUniqueId().equals(victim.getUniqueId())) {
      event.setCancelled(true);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onMythicDeath(MythicMobDeathEvent event) {
    ActiveMob mob = event.getMob();
    Entity entity = mob == null || mob.getEntity() == null ? null : mob.getEntity().getBukkitEntity();
    String mythicName = event.getMobType() == null ? null : event.getMobType().getInternalName();
    handleMobDeath(entity, mythicName, killer(event, entity), true);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onEntitiesUnload(EntitiesUnloadEvent event) {
    DungeonInstance instance = this.dungeons.instanceOf(event.getWorld());
    if (instance == null || instance.state() != SlotState.ACTIVE) {
      return;
    }
    for (Entity entity : event.getEntities()) {
      UUID id = entity.getUniqueId();
      if (!instance.roomLiving().containsKey(id) && !instance.leashed().containsKey(id)) {
        continue;
      }
      var at = instance.livingAt().get(id);
      if (at != null && at.getWorld() != null) {
        at.getChunk().addPluginChunkTicket(this.dungeons.plugin());
      } else {
        entity.getChunk().addPluginChunkTicket(this.dungeons.plugin());
      }
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onEntitiesLoad(EntitiesLoadEvent event) {
    DungeonInstance instance = this.dungeons.instanceOf(event.getWorld());
    if (instance == null || instance.state() != SlotState.ACTIVE) {
      return;
    }
    for (Entity entity : event.getEntities()) {
      UUID id = entity.getUniqueId();
      if (!instance.roomLiving().containsKey(id) && !instance.leashed().containsKey(id)) {
        continue;
      }
      instance.livingAt().put(id, entity.getLocation().clone());
      entity.getChunk().addPluginChunkTicket(this.dungeons.plugin());
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onEntityDeath(EntityDeathEvent event) {
    LivingEntity entity = event.getEntity();
    DungeonInstance instance = this.dungeons.instanceOf(entity.getWorld());
    if (instance == null || instance.state() != SlotState.ACTIVE) {
      return;
    }
    if (!instance.roomLiving().containsKey(entity.getUniqueId())
        && !instance.leashed().containsKey(entity.getUniqueId())
        && !instance.waveLiving().contains(entity.getUniqueId())) {
      return;
    }
    ActiveMob mob = MythicBukkit.inst().getMobManager().getActiveMob(entity.getUniqueId()).orElse(null);
    String mythicName = mob != null && mob.getType() != null
        ? mob.getType().getInternalName()
        : entity.getType().name();
    Player killer = entity.getKiller();
    handleMobDeath(entity, mythicName, killer, false);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onEntityRemove(EntityRemoveEvent event) {
    if (event.getCause() == EntityRemoveEvent.Cause.DEATH) {
      return;
    }
    Entity entity = event.getEntity();
    if (!(entity instanceof LivingEntity)) {
      return;
    }
    DungeonInstance instance = this.dungeons.instanceOf(entity.getWorld());
    if (instance == null || instance.state() != SlotState.ACTIVE) {
      return;
    }
    UUID id = entity.getUniqueId();
    if (!instance.roomLiving().containsKey(id)
        && !instance.leashed().containsKey(id)
        && !instance.waveLiving().contains(id)) {
      return;
    }
    ActiveMob mob = MythicBukkit.inst().getMobManager().getActiveMob(id).orElse(null);
    String mythicName = mob != null && mob.getType() != null
        ? mob.getType().getInternalName()
        : entity.getType().name();
    this.dungeons.onMobRemoved(instance, mythicName, id, false);
  }

  private void handleMobDeath(Entity entity, String mythicName, Player killer, boolean fromMythicEvent) {
    DungeonInstance instance = entity == null ? null : this.dungeons.instanceOf(entity.getWorld());
    if (instance == null && killer != null) {
      instance = this.dungeons.instanceOf(killer);
    }
    if (instance == null || instance.state() != SlotState.ACTIVE || mythicName == null || mythicName.isBlank()) {
      return;
    }
    UUID id = entity == null ? null : entity.getUniqueId();

    if (id != null
        && !instance.roomLiving().containsKey(id)
        && !instance.leashed().containsKey(id)
        && !instance.waveLiving().contains(id)) {
      return;
    }
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
    this.dungeons.onMobDeath(instance, mythicName, id);
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
