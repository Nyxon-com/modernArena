//made with love by nyxon

package com.modern.arena.command;

import com.modern.arena.ModernArenaPlugin;
import com.modern.arena.milestone.MilestoneGui;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;

public final class MilestoneCommand implements TabExecutor {

  private final ModernArenaPlugin plugin;
  private final MilestoneGui gui;

  public MilestoneCommand(ModernArenaPlugin plugin, MilestoneGui gui) {
    this.plugin = plugin;
    this.gui = gui;
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (!(sender instanceof Player player)) {
      this.plugin.messages().send(sender, "errors.player-only");
      return true;
    }
    if (!player.hasPermission(this.plugin.configs().permission("arena-milestones", "valary.arena.milestones"))) {
      this.plugin.messages().send(player, "errors.no-permission");
      this.plugin.sounds().play(player, "command-denied");
      return true;
    }

    this.plugin.kills().catchUp(player, () -> this.gui.open(player));
    return true;
  }

  @Override
  public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
    return List.of();
  }
}
