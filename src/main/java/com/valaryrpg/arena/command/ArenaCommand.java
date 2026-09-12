package com.valaryrpg.arena.command;

import com.valaryrpg.arena.ValaryArenaPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** /arena start|list|leave|reload. Nomi e permessi da config.yml. */
public final class ArenaCommand implements TabExecutor {

    private final ValaryArenaPlugin plugin;
    private final Map<UUID, Long> cooldown = new ConcurrentHashMap<>();

    public ArenaCommand(ValaryArenaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            this.plugin.messages().send(sender, "errors.player-only");
            return true;
        }
        if (!player.hasPermission(this.plugin.configs().permission("arena", "valary.arena"))) {
            deny(player);
            return true;
        }
        if (args.length == 0) {
            this.plugin.messages().send(player, "arena.help");
            return true;
        }
        if (cooling(player)) {
            this.plugin.messages().send(player, "errors.cooldown");
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals(this.plugin.configs().command("arena-start", "start"))) {
            if (!player.hasPermission(this.plugin.configs().permission("arena-start", "valary.arena.start"))) {
                deny(player);
                return true;
            }
            if (args.length < 2) {
                this.plugin.messages().send(player, "arena.help");
                return true;
            }
            this.plugin.arenas().start(player, args[1]);
            return true;
        }
        if (sub.equals("list") || sub.equals(this.plugin.configs().command("arena-list", "list"))) {
            if (!player.hasPermission(this.plugin.configs().permission("arena-list", "valary.arena.list"))) {
                deny(player);
                return true;
            }
            list(player);
            return true;
        }
        if (sub.equals(this.plugin.configs().command("arena-leave", "leave"))) {
            if (!player.hasPermission(this.plugin.configs().permission("arena-leave", "valary.arena.leave"))) {
                deny(player);
                return true;
            }
            if (this.plugin.arenas().instanceOf(player) == null) {
                this.plugin.messages().send(player, "errors.not-in-arena");
                return true;
            }
            this.plugin.arenas().leave(player, true);
            return true;
        }
        if (sub.equals(this.plugin.configs().command("arena-reload", "reload"))) {
            if (!player.hasPermission(this.plugin.configs().permission("arena-reload", "valary.arena.reload"))) {
                deny(player);
                return true;
            }
            if (this.plugin.reloadAll()) {
                this.plugin.messages().send(player, "arena.reloaded");
            } else {
                this.plugin.messages().send(player, "arena.reload-failed");
            }
            return true;
        }
        this.plugin.messages().send(player, "errors.unknown-command");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            suggest(out, args[0], this.plugin.configs().command("arena-start", "start"));
            suggest(out, args[0], this.plugin.configs().command("arena-list", "list"));
            suggest(out, args[0], this.plugin.configs().command("arena-leave", "leave"));
            suggest(out, args[0], this.plugin.configs().command("arena-reload", "reload"));
            return out;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase(this.plugin.configs().command("arena-start", "start"))) {
            for (String id : this.plugin.arenas().catalog().bossIds()) {
                suggest(out, args[1], id);
            }
        }
        return out;
    }

    private void list(Player player) {
        boolean found = false;
        for (var instance : this.plugin.arenas().activeInstances()) {
            var definition = this.plugin.arenas().catalog().boss(instance.bossId());
            if (definition == null) {
                continue;
            }
            if (definition.permission != null
                    && !definition.permission.isEmpty()
                    && !player.hasPermission(definition.permission)) {
                continue;
            }
            if (!found) {
                this.plugin.messages().send(player, "arena.list-header");
                found = true;
            }
            this.plugin.messages().send(player, "arena.list-entry", Map.of(
                    "boss", definition.id,
                    "display", definition.displayName,
                    "world", instance.worldName(),
                    "slot", Integer.toString(instance.slot()),
                    "players", Integer.toString(instance.players().size())
            ));
        }
        this.plugin.messages().send(player, found ? "arena.list-footer" : "arena.list-empty");
    }

    private boolean cooling(Player player) {
        long now = System.currentTimeMillis();
        long wait = this.plugin.configs().root().getLong("delays.command-cooldown-millis", 250L);
        Long last = this.cooldown.get(player.getUniqueId());
        if (last != null && now - last < wait) {
            return true;
        }
        this.cooldown.put(player.getUniqueId(), now);
        return false;
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
