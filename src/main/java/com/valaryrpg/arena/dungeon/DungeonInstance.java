package com.valaryrpg.arena.dungeon;

import com.valaryrpg.arena.arena.SlotState;
import com.valaryrpg.arena.party.Party;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitTask;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Istanza dungeon: slot, mondo, party, stanza corrente. */
public final class DungeonInstance {

    private final String dungeonId;
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
    private BukkitTask hologramTask;
    private TextDisplay hologram;
    private String hologramFingerprint = "";
    private boolean closingAfterWipe;
    private boolean completing;
    private int roomIndex;
    private int deathsInRoom;

    public DungeonInstance(String dungeonId, int slot, String worldName, Path folder, Party party) {
        this.dungeonId = dungeonId;
        this.slot = slot;
        this.worldName = worldName;
        this.folder = folder;
        this.party = party;
    }

    public String dungeonId() {
        return this.dungeonId;
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

    public BukkitTask hologramTask() {
        return this.hologramTask;
    }

    public void hologramTask(BukkitTask hologramTask) {
        this.hologramTask = hologramTask;
    }

    public TextDisplay hologram() {
        return this.hologram;
    }

    public void hologram(TextDisplay hologram) {
        this.hologram = hologram;
        if (hologram == null) {
            this.hologramFingerprint = "";
        }
    }

    public String hologramFingerprint() {
        return this.hologramFingerprint;
    }

    public void hologramFingerprint(String hologramFingerprint) {
        this.hologramFingerprint = hologramFingerprint == null ? "" : hologramFingerprint;
    }

    public boolean closingAfterWipe() {
        return this.closingAfterWipe;
    }

    public void closingAfterWipe(boolean closingAfterWipe) {
        this.closingAfterWipe = closingAfterWipe;
    }

    public boolean completing() {
        return this.completing;
    }

    public void completing(boolean completing) {
        this.completing = completing;
    }

    public int roomIndex() {
        return this.roomIndex;
    }

    public void roomIndex(int roomIndex) {
        this.roomIndex = roomIndex;
    }

    public int deathsInRoom() {
        return this.deathsInRoom;
    }

    public void deathsInRoom(int deathsInRoom) {
        this.deathsInRoom = deathsInRoom;
    }

    public boolean empty() {
        return this.players.isEmpty();
    }
}
