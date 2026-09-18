package com.valaryrpg.arena.dungeon;

import com.valaryrpg.arena.arena.SlotState;
import com.valaryrpg.arena.party.Party;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.Location;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

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
    private BukkitTask hologramFlyTask;
    private TextDisplay hologram;
    private String hologramFingerprint = "";
    private boolean closingAfterWipe;
    private boolean completing;
    private int roomIndex;
    private int deathsInRoom;
    private int spawnedInRoom;
    private int waveIndex;
    private long waveAt;
    private boolean lastMinionAnnounced;
    private boolean waveArmed;
    private boolean combatStarted;
    private UUID glowing;
    private final Map<String, Integer> packFires = new HashMap<>();
    private final Map<UUID, String> roomLiving = new HashMap<>();
    private final Map<UUID, Location> livingAt = new HashMap<>();
    private final Set<UUID> waveLiving = new HashSet<>();
    private final Map<UUID, String> leashed = new HashMap<>();
    private BukkitTask packTask;
    private long durationDeadline;
    private final AtomicInteger cachedRemaining = new AtomicInteger();
    private final List<DungeonPendingDoor> pendingDoors = new ArrayList<>();

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

    public BukkitTask hologramFlyTask() {
        return this.hologramFlyTask;
    }

    public void hologramFlyTask(BukkitTask hologramFlyTask) {
        this.hologramFlyTask = hologramFlyTask;
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

    public int spawnedInRoom() {
        return this.spawnedInRoom;
    }

    public void spawnedInRoom(int spawnedInRoom) {
        this.spawnedInRoom = spawnedInRoom;
    }

    public Map<String, Integer> packFires() {
        return this.packFires;
    }

    public Map<UUID, String> roomLiving() {
        return this.roomLiving;
    }

    public Map<UUID, Location> livingAt() {
        return this.livingAt;
    }

    public Set<UUID> waveLiving() {
        return this.waveLiving;
    }

    public int waveIndex() {
        return this.waveIndex;
    }

    public void waveIndex(int waveIndex) {
        this.waveIndex = Math.max(0, waveIndex);
    }

    public long waveAt() {
        return this.waveAt;
    }

    public void waveAt(long waveAt) {
        this.waveAt = waveAt;
    }

    public boolean lastMinionAnnounced() {
        return this.lastMinionAnnounced;
    }

    public void lastMinionAnnounced(boolean lastMinionAnnounced) {
        this.lastMinionAnnounced = lastMinionAnnounced;
    }

    public boolean waveArmed() {
        return this.waveArmed;
    }

    public void waveArmed(boolean waveArmed) {
        this.waveArmed = waveArmed;
    }

    public boolean combatStarted() {
        return this.combatStarted;
    }

    public void combatStarted(boolean combatStarted) {
        this.combatStarted = combatStarted;
    }

    public UUID glowing() {
        return this.glowing;
    }

    public void glowing(UUID glowing) {
        this.glowing = glowing;
    }

    public Map<UUID, String> leashed() {
        return this.leashed;
    }

    public BukkitTask packTask() {
        return this.packTask;
    }

    public void packTask(BukkitTask packTask) {
        this.packTask = packTask;
    }

    public List<DungeonPendingDoor> pendingDoors() {
        return this.pendingDoors;
    }

    public boolean empty() {
        return this.players.isEmpty();
    }

    public long durationDeadline() {
        return this.durationDeadline;
    }

    public void durationDeadline(long durationDeadline) {
        this.durationDeadline = durationDeadline;
    }

    public int cachedRemaining() {
        return this.cachedRemaining.get();
    }

    public void cachedRemaining(int remaining) {
        this.cachedRemaining.set(Math.max(0, remaining));
    }
}
