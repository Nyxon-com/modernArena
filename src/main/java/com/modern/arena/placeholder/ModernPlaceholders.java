package com.modern.arena.placeholder;

import com.modern.arena.ModernArenaPlugin;
import com.modern.arena.arena.ArenaDefinition;
import com.modern.arena.arena.ArenaInstance;
import com.modern.arena.dungeon.DungeonDefinition;
import com.modern.arena.dungeon.DungeonInstance;
import com.modern.arena.dungeon.DungeonRoom;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class ModernPlaceholders extends PlaceholderExpansion {

  private final ModernArenaPlugin plugin;

  public ModernPlaceholders(ModernArenaPlugin plugin) {
    this.plugin = plugin;
  }

  @Override
  public @NotNull String getIdentifier() {
    return "valary";
  }

  @Override
  public @NotNull String getAuthor() {
    return "Nyxon-com";
  }

  @Override
  public @NotNull String getVersion() {
    return this.plugin.getPluginMeta().getVersion();
  }

  @Override
  public boolean persist() {
    return true;
  }

  @Override
  public String onRequest(OfflinePlayer offline, @NotNull String params) {
    if (!(offline instanceof Player player)) {
      return "";
    }
    String key = params.toLowerCase();
    DungeonInstance dungeon = this.plugin.dungeons().instanceOf(player);
    if (dungeon != null) {
      return dungeonValue(player, dungeon, key);
    }
    ArenaInstance arena = this.plugin.arenas().instanceOf(player);
    if (arena != null) {
      return arenaValue(arena, key);
    }
    return "";
  }

  private String dungeonValue(Player player, DungeonInstance instance, String key) {
    DungeonDefinition definition = this.plugin.dungeons().catalog().dungeon(instance.dungeonId());
    DungeonRoom room = definition == null ? null : definition.room(instance.roomIndex());
    if (key.equals("type")) {
      return "dungeon";
    }
    if (key.equals("id") || key.equals("dungeon")) {
      return instance.dungeonId();
    }
    if (key.equals("name")) {
      return definition == null || definition.displayName == null || definition.displayName.isBlank()
          ? instance.dungeonId() : definition.displayName;
    }
    if (key.equals("room")) {
      return room == null ? "" : room.displayName;
    }
    if (key.equals("wave")) {
      return Integer.toString(instance.waveIndex());
    }
    if (key.equals("waves")) {
      return Integer.toString(room == null ? 0 : room.waves.size());
    }
    if (key.equals("remaining")) {
      return Integer.toString(instance.cachedRemaining());
    }
    if (key.equals("mobs")) {
      return Integer.toString(room == null ? 0 : room.expectedMobs());
    }
    if (key.equals("players")) {
      return Integer.toString(instance.players().size());
    }
    if (key.equals("slot")) {
      return Integer.toString(instance.slot());
    }
    if (key.equals("time") || key.equals("time_formatted")) {
      return formatTime(instance.durationDeadline());
    }
    if (key.equals("time_seconds")) {
      return Long.toString(secondsLeft(instance.durationDeadline()));
    }
    if (key.startsWith("remaining_")) {
      String roomId = key.substring("remaining_".length());
      if (definition == null) {
        return "0";
      }
      for (int index = 0; index < definition.rooms.size(); index++) {
        DungeonRoom listed = definition.rooms.get(index);
        if (listed.id.equalsIgnoreCase(roomId)) {
          if (index == instance.roomIndex()) {
            return Integer.toString(instance.cachedRemaining());
          }
          return Integer.toString(this.plugin.dungeons().rooms().remainingAt(
              instance,
              definition,
              player.getWorld(),
              index
          ));
        }
      }
      return "0";
    }
    return "";
  }

  private String arenaValue(ArenaInstance instance, String key) {
    ArenaDefinition definition = this.plugin.arenas().catalog().boss(instance.bossId());
    if (key.equals("type")) {
      return "arena";
    }
    if (key.equals("id") || key.equals("boss")) {
      return instance.bossId();
    }
    if (key.equals("name")) {
      return definition == null || definition.displayName == null || definition.displayName.isBlank()
          ? instance.bossId() : definition.displayName;
    }
    if (key.equals("room") || key.equals("wave") || key.equals("waves")) {
      return "";
    }
    if (key.equals("remaining") || key.equals("mobs")) {
      return Integer.toString(instance.bossesKilled());
    }
    if (key.equals("players")) {
      return Integer.toString(instance.players().size());
    }
    if (key.equals("slot")) {
      return Integer.toString(instance.slot());
    }
    if (key.equals("time") || key.equals("time_formatted")) {
      return formatTime(instance.durationDeadline());
    }
    if (key.equals("time_seconds")) {
      return Long.toString(secondsLeft(instance.durationDeadline()));
    }
    return "";
  }

  private String formatTime(long deadline) {
    long seconds = secondsLeft(deadline);
    if (deadline <= 0L) {
      return this.plugin.messages().raw("hologram.unlimited");
    }
    long minutes = seconds / 60L;
    long rest = seconds % 60L;
    return minutes + ":" + (rest < 10 ? "0" : "") + rest;
  }

  private static long secondsLeft(long deadline) {
    if (deadline <= 0L) {
      return 0L;
    }
    return Math.max(0L, (deadline - System.currentTimeMillis()) / 1000L);
  }
}
