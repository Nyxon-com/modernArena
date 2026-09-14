package com.valaryrpg.arena.command;

import com.valaryrpg.arena.ValaryArenaPlugin;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** /arena start|list|leave|stats|milestones|reload. Nomi e permessi da config.yml. */
public final class ArenaCommand implements TabExecutor {

    private final ValaryArenaPlugin plugin;

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
        if (this.plugin.cooldowns().cooling(player, "arena")) {
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
        if (sub.equals("stats") || sub.equals(this.plugin.configs().command("arena-stats", "stats"))) {
            if (!player.hasPermission(this.plugin.configs().permission("arena-stats", "valary.arena.stats"))) {
                deny(player);
                return true;
            }
            return stats(player, args);
        }
        if (sub.equals("milestones") || sub.equals(this.plugin.configs().command("arena-milestones", "milestones"))) {
            if (!player.hasPermission(this.plugin.configs().permission("arena-milestones", "valary.arena.milestones"))) {
                deny(player);
                return true;
            }
            return milestones(player, args);
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
            suggest(out, args[0], this.plugin.configs().command("arena-stats", "stats"));
            suggest(out, args[0], this.plugin.configs().command("arena-milestones", "milestones"));
            suggest(out, args[0], this.plugin.configs().command("arena-reload", "reload"));
            return out;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase(this.plugin.configs().command("arena-start", "start"))) {
            for (String id : this.plugin.arenas().catalog().bossIds()) {
                suggest(out, args[1], id);
            }
            return out;
        }
        if (args.length >= 2 && (args[0].equalsIgnoreCase("stats")
                || args[0].equalsIgnoreCase(this.plugin.configs().command("arena-stats", "stats")))) {
            if (args.length == 2) {
                suggestPlayers(out, args[1]);
                suggestMobs(out, args[1]);
            } else if (args.length == 3) {
                suggestMobs(out, args[2]);
            }
            return out;
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("milestones")
                || args[0].equalsIgnoreCase(this.plugin.configs().command("arena-milestones", "milestones")))) {
            suggestPlayers(out, args[1]);
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

    private boolean stats(Player player, String[] args) {
        if (args.length == 1) {
            this.plugin.kills().showKills(player, player.getUniqueId(), player.getName(), null);
            return true;
        }
        String first = args[1];
        Player other = Bukkit.getPlayerExact(first);
        if (args.length == 2) {
            if (this.plugin.kills().knownMob(first) && other == null) {
                this.plugin.kills().showKills(player, player.getUniqueId(), player.getName(), first);
                return true;
            }
            return showOther(player, other, first, null);
        }
        return showOther(player, other, first, args[2]);
    }

    private boolean milestones(Player player, String[] args) {
        if (args.length == 1) {
            this.plugin.kills().showMilestones(player, player.getUniqueId(), player.getName());
            return true;
        }
        Player other = Bukkit.getPlayerExact(args[1]);
        if (other == null) {
            this.plugin.messages().send(player, "errors.target-offline", Map.of("target", args[1]));
            return true;
        }
        if (!canViewOther(player, other)) {
            deny(player);
            return true;
        }
        this.plugin.kills().showMilestones(player, other.getUniqueId(), other.getName());
        return true;
    }

    private boolean showOther(Player player, Player other, String targetName, String mob) {
        if (other == null) {
            this.plugin.messages().send(player, "errors.target-offline", Map.of("target", targetName));
            return true;
        }
        if (!canViewOther(player, other)) {
            deny(player);
            return true;
        }
        this.plugin.kills().showKills(player, other.getUniqueId(), other.getName(), mob);
        return true;
    }

    private boolean canViewOther(Player viewer, Player target) {
        return viewer.getUniqueId().equals(target.getUniqueId())
                || viewer.hasPermission(this.plugin.configs().permission("arena-stats-other", "valary.arena.stats.other"));
    }

    private void suggestPlayers(List<String> out, String typed) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            suggest(out, typed, online.getName());
        }
    }

    private void suggestMobs(List<String> out, String typed) {
        for (String id : this.plugin.arenas().catalog().bossIds()) {
            suggest(out, typed, id);
        }
        if (this.plugin.kills() == null) {
            return;
        }
        for (String id : this.plugin.kills().mobIds()) {
            suggest(out, typed, id);
        }
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
