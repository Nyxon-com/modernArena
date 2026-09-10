package com.valaryrpg.arena.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Esegue reward YAML, incluso prefisso [console]. */
public final class ConsoleCommands {

    private static final String CONSOLE_PREFIX = "[console] ";

    public void run(Player player, List<String> lines, Map<String, String> placeholders) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        for (String line : lines) {
            if (line == null || line.isBlank()) {
                continue;
            }
            String parsed = apply(line.trim(), player, placeholders);
            if (parsed.toLowerCase(Locale.ROOT).startsWith(CONSOLE_PREFIX)) {
                String command = parsed.substring(CONSOLE_PREFIX.length()).trim();
                if (!command.isEmpty()) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                }
                continue;
            }
            player.performCommand(parsed.startsWith("/") ? parsed.substring(1) : parsed);
        }
    }

    private String apply(String text, Player player, Map<String, String> placeholders) {
        String result = text
                .replace("{player}", player.getName())
                .replace("{name}", player.getName())
                .replace("{uuid}", player.getUniqueId().toString());
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                result = result.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }
        return result;
    }
}
