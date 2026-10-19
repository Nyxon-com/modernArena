package com.modern.arena.party;

import com.modern.arena.ModernArenaPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Create/invite/accept/leave/kick/disband e timeout inviti. */
public final class PartyService {

    private final ModernArenaPlugin plugin;
    private final Map<UUID, Party> byPlayer = new HashMap<>();
    private final Map<UUID, Party> byId = new HashMap<>();
    private final Map<UUID, Invite> invites = new HashMap<>();
    private RunnableListener onDisband = party -> {
    };
    private MemberRemovedListener onMemberRemoved = (party, member) -> {
    };

    public PartyService(ModernArenaPlugin plugin) {
        this.plugin = plugin;
    }

    public void onDisband(RunnableListener listener) {
        this.onDisband = listener;
    }

    public void onMemberRemoved(MemberRemovedListener listener) {
        this.onMemberRemoved = listener;
    }

    public Party partyOf(UUID uuid) {
        return this.byPlayer.get(uuid);
    }

    public Party partyOf(Player player) {
        return partyOf(player.getUniqueId());
    }

    public int maxSize() {
        return this.plugin.configs().party().getInt("settings.max-size", 4);
    }

    public int inviteTimeoutSeconds() {
        return this.plugin.configs().party().getInt("settings.invite-timeout-seconds", 60);
    }

    public Party create(Player leader) {
        Party existing = partyOf(leader);
        if (existing != null) {
            return null;
        }
        Party party = new Party(leader.getUniqueId());
        this.byPlayer.put(leader.getUniqueId(), party);
        this.byId.put(party.id(), party);
        return party;
    }

    public boolean invite(Player leader, Player target) {
        Party party = partyOf(leader);
        if (party == null || !party.isLeader(leader.getUniqueId())) {
            return false;
        }
        Invite invite = new Invite(party.id());
        Invite previous = this.invites.put(target.getUniqueId(), invite);
        if (previous != null && previous.task != null) {
            previous.task.cancel();
        }
        long ticks = Math.max(1, inviteTimeoutSeconds()) * 20L;
        invite.task = this.plugin.tasks().later(() -> expire(target.getUniqueId(), party.id()), ticks);
        return true;
    }

    public Party accept(Player player) {
        Invite invite = this.invites.remove(player.getUniqueId());
        if (invite == null) {
            return null;
        }
        if (invite.task != null) {
            invite.task.cancel();
        }
        if (partyOf(player) != null) {
            return null;
        }
        Party party = this.byId.get(invite.partyId);
        if (party == null || party.size() >= maxSize()) {
            return null;
        }
        party.members().add(player.getUniqueId());
        this.byPlayer.put(player.getUniqueId(), party);
        return party;
    }

    public LeaveResult leave(Player player) {
        Party party = partyOf(player);
        if (party == null) {
            return LeaveResult.NONE;
        }
        return removeMember(party, player.getUniqueId());
    }

    public LeaveResult kick(Player leader, UUID target) {
        Party party = partyOf(leader);
        if (party == null || !party.isLeader(leader.getUniqueId()) || !party.contains(target)) {
            return LeaveResult.NONE;
        }
        return removeMember(party, target);
    }

    public Party disband(Player leader) {
        Party party = partyOf(leader);
        if (party == null || !party.isLeader(leader.getUniqueId())) {
            return null;
        }
        dissolve(party);
        return party;
    }

    public void dissolve(Party party) {
        for (Player member : onlineMembers(party)) {
            this.plugin.messages().send(member, "party.disbanded");
        }
        for (UUID member : new ArrayList<>(party.members())) {
            this.byPlayer.remove(member);
        }
        this.byId.remove(party.id());
        clearInvites(party.id());
        this.onDisband.run(party);
        party.members().clear();
    }

    public List<Player> onlineMembers(Party party) {
        List<Player> online = new ArrayList<>();
        for (UUID uuid : party.members()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                online.add(player);
            }
        }
        return online;
    }

    public void broadcast(Party party, String path, Map<String, String> placeholders) {
        for (Player member : onlineMembers(party)) {
            this.plugin.messages().send(member, path, placeholders);
        }
    }

    private LeaveResult removeMember(Party party, UUID uuid) {
        party.members().remove(uuid);
        this.byPlayer.remove(uuid);
        this.onMemberRemoved.run(party, uuid);
        if (party.members().isEmpty()) {
            dissolve(party);
            return LeaveResult.DISBANDED;
        }
        if (party.isLeader(uuid)) {
            UUID next = party.members().iterator().next();
            party.leader(next);
            return LeaveResult.LEADER_PASSED;
        }
        return LeaveResult.LEFT;
    }

    private void expire(UUID target, UUID partyId) {
        Invite invite = this.invites.get(target);
        if (invite == null || !invite.partyId.equals(partyId)) {
            return;
        }
        this.invites.remove(target);
        Player player = Bukkit.getPlayer(target);
        if (player != null) {
            this.plugin.messages().send(player, "party.invite-expired");
        }
    }

    private void clearInvites(UUID partyId) {
        Iterator<Map.Entry<UUID, Invite>> iterator = this.invites.entrySet().iterator();
        while (iterator.hasNext()) {
            Invite invite = iterator.next().getValue();
            if (invite.partyId.equals(partyId)) {
                if (invite.task != null) {
                    invite.task.cancel();
                }
                iterator.remove();
            }
        }
    }

    public void shutdown() {
        for (Invite invite : this.invites.values()) {
            if (invite.task != null) {
                invite.task.cancel();
            }
        }
        this.invites.clear();
        this.byPlayer.clear();
        for (Party party : this.byId.values()) {
            party.members().clear();
        }
        this.byId.clear();
        this.onDisband = party -> {
        };
        this.onMemberRemoved = (party, member) -> {
        };
    }

    public enum LeaveResult {
        NONE, LEFT, LEADER_PASSED, DISBANDED
    }

    @FunctionalInterface
    public interface RunnableListener {
        void run(Party party);
    }

    @FunctionalInterface
    public interface MemberRemovedListener {
        void run(Party party, UUID member);
    }

    private static final class Invite {
        private final UUID partyId;
        private BukkitTask task;

        private Invite(UUID partyId) {
            this.partyId = partyId;
        }
    }
}
