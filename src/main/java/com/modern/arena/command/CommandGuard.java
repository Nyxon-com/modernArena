package com.modern.arena.command;

import com.modern.arena.ModernArenaPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Blocca comandi YAML dentro arena o dungeon. Leave e /party restano. */
public final class CommandGuard implements Listener {

    private final ModernArenaPlugin plugin;

    public CommandGuard(ModernArenaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (this.plugin.arenas().instanceOf(player) == null && this.plugin.dungeons().instanceOf(player) == null) {
            return;
        }
        if (player.hasPermission(this.plugin.configs().permission("command-bypass", "valary.arena.command.bypass"))) {
            return;
        }
        String token = firstToken(event.getMessage());
        if (token.isEmpty() || allowed(token)) {
            return;
        }
        Set<String> blocked = blocked();
        if (!blocked.contains(token)) {
            return;
        }
        event.setCancelled(true);
        this.plugin.messages().send(player, "errors.command-blocked");
        this.plugin.sounds().play(player, "command-denied");
    }

    private boolean allowed(String token) {
        String arena = this.plugin.configs().command("arena", "arena").toLowerCase(Locale.ROOT);
        String dungeon = this.plugin.configs().command("dungeon", "dungeon").toLowerCase(Locale.ROOT);
        String party = this.plugin.configs().command("party", "party").toLowerCase(Locale.ROOT);
        String milestones = this.plugin.configs().command("milestones", "milestones").toLowerCase(Locale.ROOT);
        return token.equals(arena)
                || token.equals(dungeon)
                || token.equals(party)
                || token.equals(milestones)
                || token.equals("milestone");
    }

    private Set<String> blocked() {
        Set<String> blocked = new HashSet<>();
        for (String line : this.plugin.configs().root().getStringList("blocked-commands")) {
            if (line == null || line.isBlank()) {
                continue;
            }
            blocked.add(firstToken(line));
        }
        return blocked;
    }

    private static String firstToken(String message) {
        String raw = message == null ? "" : message.trim();
        if (raw.startsWith("/")) {
            raw = raw.substring(1);
        }
        int space = raw.indexOf(' ');
        if (space >= 0) {
            raw = raw.substring(0, space);
        }
        int colon = raw.indexOf(':');
        if (colon >= 0 && colon < raw.length() - 1) {
            raw = raw.substring(colon + 1);
        }
        return raw.toLowerCase(Locale.ROOT);
    }
}
