package com.valaryrpg.arena.command;

import com.valaryrpg.arena.ValaryArenaPlugin;
import com.valaryrpg.arena.party.Party;
import com.valaryrpg.arena.party.PartyService;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** /party create|invite|accept|leave|kick|disband. */
public final class PartyCommand implements TabExecutor {

    private final ValaryArenaPlugin plugin;

    public PartyCommand(ValaryArenaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            this.plugin.messages().send(sender, "errors.player-only");
            return true;
        }
        if (!player.hasPermission(this.plugin.configs().permission("party", "valary.party"))) {
            deny(player);
            return true;
        }
        if (args.length == 0) {
            this.plugin.messages().send(player, "party.help");
            return true;
        }
        if (this.plugin.cooldowns().cooling(player, "party")) {
            this.plugin.messages().send(player, "errors.cooldown");
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals(cmd("party-create", "create"))) {
            return create(player);
        }
        if (sub.equals(cmd("party-invite", "invite"))) {
            return invite(player, args);
        }
        if (sub.equals(cmd("party-accept", "accept"))) {
            return accept(player);
        }
        if (sub.equals(cmd("party-leave", "leave"))) {
            return leave(player);
        }
        if (sub.equals(cmd("party-kick", "kick"))) {
            return kick(player, args);
        }
        if (sub.equals(cmd("party-disband", "disband"))) {
            return disband(player);
        }
        this.plugin.messages().send(player, "errors.unknown-command");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            suggest(out, args[0], cmd("party-create", "create"));
            suggest(out, args[0], cmd("party-invite", "invite"));
            suggest(out, args[0], cmd("party-accept", "accept"));
            suggest(out, args[0], cmd("party-leave", "leave"));
            suggest(out, args[0], cmd("party-kick", "kick"));
            suggest(out, args[0], cmd("party-disband", "disband"));
            return out;
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase(cmd("party-invite", "invite"))
                || args[0].equalsIgnoreCase(cmd("party-kick", "kick")))) {
            Party party = sender instanceof Player player ? this.plugin.parties().partyOf(player) : null;
            for (Player online : Bukkit.getOnlinePlayers()) {
                boolean invite = args[0].equalsIgnoreCase(cmd("party-invite", "invite"));
                boolean validInvite = invite
                        && !online.equals(sender)
                        && this.plugin.parties().partyOf(online) == null;
                boolean validKick = !invite
                        && party != null
                        && party.contains(online.getUniqueId())
                        && !party.isLeader(online.getUniqueId());
                if (validInvite || validKick) {
                    suggest(out, args[1], online.getName());
                }
            }
        }
        return out;
    }

    private boolean create(Player player) {
        if (!player.hasPermission(perm("party-create", "valary.party.create"))) {
            deny(player);
            return true;
        }
        if (this.plugin.parties().partyOf(player) != null) {
            this.plugin.messages().send(player, "errors.already-in-party");
            return true;
        }
        this.plugin.parties().create(player);
        this.plugin.messages().send(player, "party.created");
        return true;
    }

    private boolean invite(Player player, String[] args) {
        if (!player.hasPermission(perm("party-invite", "valary.party.invite"))) {
            deny(player);
            return true;
        }
        Party party = this.plugin.parties().partyOf(player);
        if (party == null) {
            this.plugin.messages().send(player, "errors.no-party");
            return true;
        }
        if (!party.isLeader(player.getUniqueId())) {
            this.plugin.messages().send(player, "errors.not-leader");
            return true;
        }
        if (args.length < 2) {
            this.plugin.messages().send(player, "party.usage-invite");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            this.plugin.messages().send(player, "errors.target-offline", Map.of("target", args[1]));
            return true;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            this.plugin.messages().send(player, "errors.invite-self");
            return true;
        }
        if (this.plugin.parties().partyOf(target) != null) {
            this.plugin.messages().send(player, "errors.target-in-party", Map.of("target", target.getName()));
            return true;
        }
        if (party.size() >= this.plugin.parties().maxSize()) {
            this.plugin.messages().send(player, "errors.party-full", Map.of("max", Integer.toString(this.plugin.parties().maxSize())));
            return true;
        }
        if (this.plugin.arenas().busy(this.plugin.parties().partyOf(target))) {
            this.plugin.messages().send(player, "errors.player-busy", Map.of("target", target.getName()));
            return true;
        }
        this.plugin.parties().invite(player, target);
        this.plugin.messages().send(player, "party.invited", Map.of("target", target.getName()));
        this.plugin.messages().send(target, "party.invite-received", Map.of("player", player.getName()));
        this.plugin.sounds().play(target, "party-invite");
        return true;
    }

    private boolean accept(Player player) {
        if (!player.hasPermission(perm("party-accept", "valary.party.accept"))) {
            deny(player);
            return true;
        }
        if (this.plugin.parties().partyOf(player) != null) {
            this.plugin.messages().send(player, "errors.already-in-party");
            return true;
        }
        Party party = this.plugin.parties().accept(player);
        if (party == null) {
            this.plugin.messages().send(player, "errors.no-invite");
            return true;
        }
        Player leader = Bukkit.getPlayer(party.leader());
        String leaderName = leader == null ? "?" : leader.getName();
        this.plugin.messages().send(player, "party.accepted", Map.of("leader", leaderName));
        this.plugin.parties().broadcast(party, "party.member-joined", Map.of("player", player.getName()));
        return true;
    }

    private boolean leave(Player player) {
        if (!player.hasPermission(perm("party-leave", "valary.party.leave"))) {
            deny(player);
            return true;
        }
        Party party = this.plugin.parties().partyOf(player);
        if (party == null) {
            this.plugin.messages().send(player, "errors.not-in-party");
            return true;
        }
        if (this.plugin.arenas().instanceOf(player) != null) {
            this.plugin.arenas().leave(player, true);
        }
        PartyService.LeaveResult result = this.plugin.parties().leave(player);
        if (result == PartyService.LeaveResult.NONE) {
            this.plugin.messages().send(player, "errors.not-in-party");
            return true;
        }
        this.plugin.messages().send(player, "party.left");
        if (result == PartyService.LeaveResult.DISBANDED) {
            return true;
        }
        this.plugin.parties().broadcast(party, "party.member-left", Map.of("player", player.getName()));
        if (result == PartyService.LeaveResult.LEADER_PASSED) {
            Player next = Bukkit.getPlayer(party.leader());
            this.plugin.parties().broadcast(party, "party.leader-passed", Map.of(
                    "player", next == null ? "?" : next.getName()
            ));
        }
        return true;
    }

    private boolean kick(Player player, String[] args) {
        if (!player.hasPermission(perm("party-kick", "valary.party.kick"))) {
            deny(player);
            return true;
        }
        Party party = this.plugin.parties().partyOf(player);
        if (party == null) {
            this.plugin.messages().send(player, "errors.no-party");
            return true;
        }
        if (!party.isLeader(player.getUniqueId())) {
            this.plugin.messages().send(player, "errors.not-leader");
            return true;
        }
        if (args.length < 2) {
            this.plugin.messages().send(player, "party.usage-kick");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        UUID targetId = target == null ? null : target.getUniqueId();
        if (targetId == null || !party.contains(targetId)) {
            this.plugin.messages().send(player, "errors.not-member", Map.of("target", args[1]));
            return true;
        }
        if (targetId.equals(player.getUniqueId())) {
            this.plugin.messages().send(player, "errors.cannot-kick-self");
            return true;
        }
        if (this.plugin.arenas().instanceOf(target) != null) {
            this.plugin.arenas().leave(target, true);
        }
        this.plugin.parties().kick(player, targetId);
        this.plugin.messages().send(target, "party.kicked");
        this.plugin.parties().broadcast(party, "party.member-kicked", Map.of("target", target.getName()));
        return true;
    }

    private boolean disband(Player player) {
        if (!player.hasPermission(perm("party-disband", "valary.party.disband"))) {
            deny(player);
            return true;
        }
        Party party = this.plugin.parties().disband(player);
        if (party == null) {
            Party current = this.plugin.parties().partyOf(player);
            if (current == null) {
                this.plugin.messages().send(player, "errors.no-party");
            } else {
                this.plugin.messages().send(player, "errors.not-leader");
            }
            return true;
        }
        return true;
    }

    private String cmd(String key, String fallback) {
        return this.plugin.configs().command(key, fallback);
    }

    private String perm(String key, String fallback) {
        return this.plugin.configs().permission(key, fallback);
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
