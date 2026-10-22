//made with love by nyxon

package com.modern.arena.command;

import com.modern.arena.ModernArenaPlugin;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class DungeonCommand implements TabExecutor {

  private final ModernArenaPlugin plugin;

  public DungeonCommand(ModernArenaPlugin plugin) {
    this.plugin = plugin;
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (args.length == 0) {
      if (sender instanceof Player player && !hasDungeon(player)) {
        deny(player);
        return true;
      }
      this.plugin.messages().send(sender, "dungeon.help");
      return true;
    }
    String sub = args[0].toLowerCase(Locale.ROOT);
    if (sub.equals(this.plugin.configs().command("dungeon-start", "start"))) {
      return start(sender, args);
    }
    if (args.length >= 2 && sub.equals(this.plugin.configs().command("dungeon-spawn", "spawn"))) {
      if (sender instanceof Player staff
        && !staff.hasPermission(this.plugin.configs().permission("dungeon-spawn-other", "valary.dungeon.spawn.other"))) {
        deny(staff);
        return true;
      }
      Player target = Bukkit.getPlayerExact(args[1]);
      if (target != null) {
        this.plugin.dungeons().teleportSpawn(target);
      }
      return true;
    }        if (!(sender instanceof Player player)) {
      this.plugin.messages().send(sender, "errors.player-only");
      return true;
    }
    if (!hasDungeon(player)) {
      deny(player);
      return true;
    }
    if (this.plugin.cooldowns().cooling(player, "dungeon")) {
      this.plugin.messages().send(player, "errors.cooldown");
      return true;
    }
    if (sub.equals("list") || sub.equals(this.plugin.configs().command("dungeon-list", "list"))) {
      if (!player.hasPermission(this.plugin.configs().permission("dungeon-list", "valary.dungeon.list"))) {
        deny(player);
        return true;
      }
      list(player);
      return true;
    }
    if (sub.equals(this.plugin.configs().command("dungeon-leave", "leave"))) {
      if (!player.hasPermission(this.plugin.configs().permission("dungeon-leave", "valary.dungeon.leave"))) {
        deny(player);
        return true;
      }
      if (this.plugin.dungeons().instanceOf(player) == null) {
        this.plugin.messages().send(player, "errors.not-in-dungeon");
        return true;
      }
      this.plugin.dungeons().leave(player, true);
      return true;
    }
    if (sub.equals(this.plugin.configs().command("dungeon-spawn", "spawn"))) {
      if (!player.hasPermission(this.plugin.configs().permission("dungeon-spawn", "valary.dungeon.spawn"))) {
        deny(player);
        return true;
      }
      this.plugin.dungeons().teleportSpawn(player);
      return true;
    }
    this.plugin.messages().send(player, "errors.unknown-command");
    return true;
  }

  @Override
  public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
    List<String> out = new ArrayList<>();
    if (args.length == 1) {
      suggest(out, args[0], this.plugin.configs().command("dungeon-start", "start"));
      suggest(out, args[0], this.plugin.configs().command("dungeon-list", "list"));
      suggest(out, args[0], this.plugin.configs().command("dungeon-leave", "leave"));
      suggest(out, args[0], this.plugin.configs().command("dungeon-spawn", "spawn"));
      return out;
    }
    if (!args[0].equalsIgnoreCase(this.plugin.configs().command("dungeon-start", "start"))) {
      return out;
    }
    if (args.length == 2) {
      for (String id : this.plugin.dungeons().catalog().dungeonIds()) {
        suggest(out, args[1], id);
      }
      return out;
    }
    if (args.length == 3 && canStartOther(sender)) {
      for (Player online : Bukkit.getOnlinePlayers()) {
        suggest(out, args[2], online.getName());
      }
    }
    return out;
  }

  private boolean start(CommandSender sender, String[] args) {
    boolean other = args.length >= 3;
    if (sender instanceof Player player) {
      String perm = other
          ? this.plugin.configs().permission("dungeon-start-other", "valary.dungeon.start.other")
          : this.plugin.configs().permission("dungeon-start", "valary.dungeon.start");
      if (!player.hasPermission(perm)) {
        deny(player);
        return true;
      }
      if (!other && !hasDungeon(player)) {
        deny(player);
        return true;
      }
      if (this.plugin.cooldowns().cooling(player, "dungeon")) {
        this.plugin.messages().send(player, "errors.cooldown");
        return true;
      }
    }
    if (args.length < 2 || (!(sender instanceof Player) && !other)) {
      this.plugin.messages().send(sender, "dungeon.usage-start");
      return true;
    }
    Player target;
    if (other) {
      target = Bukkit.getPlayerExact(args[2]);
      if (target == null) {
        this.plugin.messages().send(sender, "errors.player-offline", Map.of("player", args[2]));
        return true;
      }
    } else {
      target = (Player) sender;
    }
    this.plugin.dungeons().start(target, args[1], sender);
    return true;
  }

  private void list(Player player) {
    boolean found = false;
    for (var instance : this.plugin.dungeons().activeInstances()) {
      var definition = this.plugin.dungeons().catalog().dungeon(instance.dungeonId());
      if (definition == null) {
        continue;
      }
      if (definition.permission != null
          && !definition.permission.isEmpty()
          && !player.hasPermission(definition.permission)) {
        continue;
      }
      if (!found) {
        this.plugin.messages().send(player, "dungeon.list-header");
        found = true;
      }
      this.plugin.messages().send(player, "dungeon.list-entry", this.plugin.dungeons().placeholders(instance, definition));
    }
    this.plugin.messages().send(player, found ? "dungeon.list-footer" : "dungeon.list-empty");
  }

  private boolean hasDungeon(Player player) {
    return player.hasPermission(this.plugin.configs().permission("dungeon", "valary.dungeon"));
  }

  private boolean canStartOther(CommandSender sender) {
    return !(sender instanceof Player player)
        || player.hasPermission(this.plugin.configs().permission(
        "dungeon-start-other",
        "valary.dungeon.start.other"
    ));
  }

  private void deny(Player player) {
    this.plugin.messages().send(player, "errors.no-permission");
    this.plugin.sounds().play(player, "command-denied");
  }

  private void suggest(List<String> out, String typed, String value) {
    if (value.toLowerCase(Locale.ROOT).startsWith(typed.toLowerCase(Locale.ROOT))) {
      out.add(value);
    }
  }
}
