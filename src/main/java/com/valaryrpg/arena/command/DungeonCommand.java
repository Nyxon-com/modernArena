package com.valaryrpg.arena.command;

import com.valaryrpg.arena.ValaryArenaPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /dungeon start|list|leave. Nomi e permessi da config.yml. */
public final class DungeonCommand implements TabExecutor {

    private final ValaryArenaPlugin plugin;

    public DungeonCommand(ValaryArenaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            this.plugin.messages().send(sender, "errors.player-only");
            return true;
        }
        if (!player.hasPermission(this.plugin.configs().permission("dungeon", "valary.dungeon"))) {
            deny(player);
            return true;
        }
        if (args.length == 0) {
            this.plugin.messages().send(player, "dungeon.help");
            return true;
        }
        if (this.plugin.cooldowns().cooling(player, "dungeon")) {
            this.plugin.messages().send(player, "errors.cooldown");
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals(this.plugin.configs().command("dungeon-start", "start"))) {
            if (!player.hasPermission(this.plugin.configs().permission("dungeon-start", "valary.dungeon.start"))) {
                deny(player);
                return true;
            }
            if (args.length < 2) {
                this.plugin.messages().send(player, "dungeon.help");
                return true;
            }
            this.plugin.dungeons().start(player, args[1]);
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
            return out;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase(this.plugin.configs().command("dungeon-start", "start"))) {
            for (String id : this.plugin.dungeons().catalog().dungeonIds()) {
                suggest(out, args[1], id);
            }
        }
        return out;
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
