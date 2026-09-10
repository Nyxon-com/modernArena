package com.valaryrpg.arena.arena;

import com.valaryrpg.arena.party.Party;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Istanza viva: slot, mondo, party, spawners registrati. */
public final class ArenaInstance {

    private final String bossId;
    private final int slot;
    private final String worldName;
    private final Party party;
    private final Set<UUID> players = ConcurrentHashMap.newKeySet();
    private final List<String> spawnerNames = new ArrayList<>();
    private SlotState state = SlotState.COPYING;
    private BukkitTask durationTask;

    public ArenaInstance(String bossId, int slot, String worldName, Party party) {
        this.bossId = bossId;
        this.slot = slot;
        this.worldName = worldName;
        this.party = party;
    }

    public String bossId() {
        return this.bossId;
    }

    public int slot() {
        return this.slot;
    }

    public String worldName() {
        return this.worldName;
    }

    public Party party() {
        return this.party;
    }

    public Set<UUID> players() {
        return this.players;
    }

    public List<String> spawnerNames() {
        return this.spawnerNames;
    }

    public SlotState state() {
        return this.state;
    }

    public void state(SlotState state) {
        this.state = state;
    }

    public BukkitTask durationTask() {
        return this.durationTask;
    }

    public void durationTask(BukkitTask durationTask) {
        this.durationTask = durationTask;
    }

    public boolean empty() {
        return this.players.isEmpty();
    }
}
