package com.modern.arena.arena;

import com.modern.arena.party.Party;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitTask;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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
  private BukkitTask hologramTask;
  private TextDisplay hologram;
  private String hologramFingerprint = "";
  private boolean closingAfterWipe;
  private boolean spawnersHalted;
  private long durationDeadline;
  private int bossesKilled;
  private String previousTopDamager = "";
  private double previousTopDamage;
  private final Map<UUID, Map<UUID, Double>> damageByMob = new HashMap<>();

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

  public boolean spawnersHalted() {
    return this.spawnersHalted;
  }

  public void spawnersHalted(boolean spawnersHalted) {
    this.spawnersHalted = spawnersHalted;
  }

  public int bossesKilled() {
    return this.bossesKilled;
  }

  public void bossesKilled(int bossesKilled) {
    this.bossesKilled = bossesKilled;
  }

  public String previousTopDamager() {
    return this.previousTopDamager;
  }

  public void previousTopDamager(String previousTopDamager) {
    this.previousTopDamager = previousTopDamager == null ? "" : previousTopDamager;
  }

  public double previousTopDamage() {
    return this.previousTopDamage;
  }

  public void previousTopDamage(double previousTopDamage) {
    this.previousTopDamage = previousTopDamage;
  }

  public Map<UUID, Map<UUID, Double>> damageByMob() {
    return this.damageByMob;
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

  public long durationDeadline() {
    return this.durationDeadline;
  }

  public void durationDeadline(long durationDeadline) {
    this.durationDeadline = durationDeadline;
  }
}
