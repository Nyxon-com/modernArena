package com.valaryrpg.arena.arena;

import com.valaryrpg.arena.party.Party;
import org.bukkit.scheduler.BukkitTask;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Istanza viva: slot, mondo, party, spawners registrati. */
public final class ArenaInstance {

    private final String bossId;
    private final int slot;
    private final String worldName;
    private final Path folder;
    private final Party party;
    private String loadedWorldName;
    private final Set<UUID> players = new HashSet<>();
    private final Set<UUID> eliminated = new HashSet<>();
    private final List<String> spawnerNames = new ArrayList<>();
    private SlotState state = SlotState.COPYING;
    private BukkitTask durationTask;
    private boolean closingAfterWipe;

    public ArenaInstance(String bossId, int slot, String worldName, Path folder, Party party) {
        this.bossId = bossId;
        this.slot = slot;
        this.worldName = worldName;
        this.folder = folder;
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

    public Path folder() {
        return this.folder;
    }

    public String loadedWorldName() {
        return this.loadedWorldName;
    }

    public void loadedWorldName(String loadedWorldName) {
        this.loadedWorldName = loadedWorldName;
    }

    public Party party() {
        return this.party;
    }

    public Set<UUID> players() {
        return this.players;
    }

    public Set<UUID> eliminated() {
        return this.eliminated;
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

    public boolean closingAfterWipe() {
        return this.closingAfterWipe;
    }

    public void closingAfterWipe(boolean closingAfterWipe) {
        this.closingAfterWipe = closingAfterWipe;
    }

    public boolean empty() {
        return this.players.isEmpty();
    }
}
