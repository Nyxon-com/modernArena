//made with love by nyxon

package com.modern.arena.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class ConsoleCommands {

  private static final String CONSOLE_PREFIX = "[console] ";

  public void run(Player player, List<String> lines, Map<String, String> placeholders) {
    run(player.getName(), player.getUniqueId(), player, lines, placeholders);
  }

  public void runOffline(String playerName, UUID uuid, List<String> lines, Map<String, String> placeholders) {
    run(playerName, uuid, null, lines, placeholders);
  }

  private void run(
      String playerName,
      UUID uuid,
      Player online,
      List<String> lines,
      Map<String, String> placeholders
  ) {
    if (lines == null || lines.isEmpty() || playerName == null || playerName.isBlank()) {
      return;
    }
    for (String line : lines) {
      if (line == null || line.isBlank()) {
        continue;
      }
      String parsed = apply(line.trim(), playerName, uuid, placeholders);
      if (parsed.toLowerCase(Locale.ROOT).startsWith(CONSOLE_PREFIX)) {
        String command = parsed.substring(CONSOLE_PREFIX.length()).trim();
        if (!command.isEmpty()) {
          Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        }
        continue;
      }
      if (online != null) {
        online.performCommand(parsed.startsWith("/") ? parsed.substring(1) : parsed);
      }
    }
  }

  private String apply(String text, String playerName, UUID uuid, Map<String, String> placeholders) {
    String result = text
        .replace("{player}", playerName)
        .replace("{name}", playerName)
        .replace("{uuid}", uuid == null ? "" : uuid.toString());
    if (placeholders != null) {
      for (Map.Entry<String, String> entry : placeholders.entrySet()) {
        result = result.replace("{" + entry.getKey() + "}", entry.getValue());
      }
    }
    return result;
  }
}
