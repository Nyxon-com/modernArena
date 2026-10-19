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

/** /arena start|list|leave|stats|milestones|reload. Start da console: boss + player. */
public final class ArenaCommand implements TabExecutor {

    private final ModernArenaPlugin plugin;

    public ArenaCommand(ModernArenaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player player && !hasArena(player)) {
                deny(player);
                return true;
            }
            this.plugin.messages().send(sender, "arena.help");
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals(this.plugin.configs().command("arena-start", "start"))) {
            return start(sender, args);
        }
        if (!(sender instanceof Player player)) {
            this.plugin.messages().send(sender, "errors.player-only");
            return true;
        }
        if (!hasArena(player)) {
            deny(player);
            return true;
        }
        if (this.plugin.cooldowns().cooling(player, "arena")) {
            this.plugin.messages().send(player, "errors.cooldown");
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
        if (sub.equals("catchup")) {
            if (!player.hasPermission(this.plugin.configs().permission("arena-reload", "valary.arena.reload"))) {
                deny(player);
                return true;
            }
            if (args.length >= 2 && args[1].equalsIgnoreCase("all")) {
                this.plugin.kills().catchUpAll();
                this.plugin.messages().sendRaw(player, "{prefix}<gray>Catch-up milestone avviato per <white>tutti</white> i player.", Map.of());
                return true;
            }
            Player target = args.length >= 2 ? Bukkit.getPlayerExact(args[1]) : player;
            if (target == null) {
                this.plugin.messages().send(player, "errors.target-offline", Map.of("target", args[1]));
                return true;
            }
            this.plugin.kills().catchUp(target);
            this.plugin.messages().sendRaw(player, "{prefix}<gray>Catch-up milestone avviato per <white>" + target.getName() + "</white>.", Map.of());
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
            suggest(out, args[0], this.plugin.configs().command("arena-stats", "stats"));
            suggest(out, args[0], this.plugin.configs().command("arena-milestones", "milestones"));
            suggest(out, args[0], this.plugin.configs().command("arena-reload", "reload"));
            return out;
        }
        if (args[0].equalsIgnoreCase(this.plugin.configs().command("arena-start", "start"))) {
            if (args.length == 2) {
                for (String id : this.plugin.arenas().catalog().bossIds()) {
                    suggest(out, args[1], id);
                }
                return out;
            }
            if (args.length == 3 && canStartOther(sender)) {
                suggestPlayers(out, args[2]);
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

    private boolean start(CommandSender sender, String[] args) {
        boolean other = args.length >= 3;
        if (sender instanceof Player player) {
            String perm = other
                    ? this.plugin.configs().permission("arena-start-other", "valary.arena.start.other")
                    : this.plugin.configs().permission("arena-start", "valary.arena.start");
            if (!player.hasPermission(perm)) {
                deny(player);
                return true;
            }
            if (!other && !hasArena(player)) {
                deny(player);
                return true;
            }
            if (this.plugin.cooldowns().cooling(player, "arena")) {
                this.plugin.messages().send(player, "errors.cooldown");
                return true;
            }
        }
        if (args.length < 2 || (!(sender instanceof Player) && !other)) {
            this.plugin.messages().send(sender, "arena.usage-start");
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
        this.plugin.arenas().start(target, args[1]);
        if (other && sender != target) {
            this.plugin.messages().send(sender, "arena.started-for", Map.of(
                    "boss", args[1],
                    "player", target.getName()
            ));
        }
        return true;
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
            if (this.plugin.milestoneGui() != null) {
                this.plugin.kills().catchUp(player, () -> this.plugin.milestoneGui().open(player));
            } else {
                this.plugin.kills().catchUp(player);
                this.plugin.kills().showMilestones(player, player.getUniqueId(), player.getName());
            }
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

    private boolean hasArena(Player player) {
        return player.hasPermission(this.plugin.configs().permission("arena", "valary.arena"));
    }

    private boolean canStartOther(CommandSender sender) {
        return !(sender instanceof Player player)
                || player.hasPermission(this.plugin.configs().permission(
                "arena-start-other",
                "valary.arena.start.other"
        ));
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
