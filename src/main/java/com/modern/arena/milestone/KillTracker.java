//made with love by nyxon

package com.modern.arena.milestone;

import com.modern.arena.ModernArenaPlugin;
import com.modern.arena.arena.ArenaDefinition;
import com.modern.arena.storage.KillRepository;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class KillTracker {

  private final ModernArenaPlugin plugin;
  private final KillRepository repository;
  private final MilestoneCatalog catalog;
  private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<UUID, PlayerCache> online = new ConcurrentHashMap<>();
  private final AtomicBoolean accepting = new AtomicBoolean(true);
  private final Object operationsMonitor = new Object();
  private int activeOperations;

  public KillTracker(ModernArenaPlugin plugin, KillRepository repository, MilestoneCatalog catalog) {
    this.plugin = plugin;
    this.repository = repository;
    this.catalog = catalog;
  }

  public void record(Player player, String mobId) {
    if (!this.accepting.get()) {
      return;
    }
    List<MilestoneDefinition> milestones = this.catalog.of(mobId);
    UUID uuid = player.getUniqueId();
    PlayerCache cache = this.online.computeIfAbsent(uuid, ignored -> new PlayerCache());
    this.plugin.tasks().async(() -> {
      if (!beginOperation()) {
        return;
      }
      String lockKey = uuid + ":" + mobId;
      Object lock = this.locks.computeIfAbsent(lockKey, key -> new Object());
      try {
        synchronized (lock) {
          record(uuid, mobId, milestones, cache);
        }
      } finally {
        this.locks.remove(lockKey, lock);
        endOperation();
      }
    });
  }

  public String keyFor(String mythicName, String bossId, boolean trackedBoss) {
    if (!this.catalog.of(mythicName).isEmpty()) {
      return this.catalog.canonical(mythicName);
    }
    if (trackedBoss && bossId != null && !this.catalog.of(bossId).isEmpty()) {
      return this.catalog.canonical(bossId);
    }
    return trackedBoss && bossId != null ? bossId : mythicName;
  }

  public Collection<String> mobIds() {
    return this.catalog.mobIds();
  }

  public boolean knownMob(String id) {
    if (id == null || id.isBlank()) {
      return false;
    }
    if (!this.catalog.of(id).isEmpty()) {
      return true;
    }
    for (String boss : this.plugin.arenas().catalog().bossIds()) {
      if (boss.equalsIgnoreCase(id)) {
        return true;
      }
    }
    return false;
  }

  public void showKills(Player viewer, UUID target, String targetName, String mobFilter) {
    this.plugin.tasks().async(() -> {
      try {
        Map<String, Integer> kills = this.repository.list(target);
        this.plugin.tasks().sync(() -> sendKills(viewer, targetName, mobFilter, kills));
      } catch (SQLException exception) {
        this.plugin.getLogger().warning("Lettura kill fallita: " + exception.getMessage());
        this.plugin.tasks().sync(() -> this.plugin.messages().send(viewer, "errors.stats-failed"));
      }
    });
  }

  public void showMilestones(Player viewer, UUID target, String targetName) {
    this.plugin.tasks().async(() -> {
      try {
        Map<String, Integer> kills = this.repository.list(target);
        this.plugin.tasks().sync(() -> sendMilestones(viewer, targetName, kills));
      } catch (SQLException exception) {
        this.plugin.getLogger().warning("Lettura milestone fallita: " + exception.getMessage());
        this.plugin.tasks().sync(() -> this.plugin.messages().send(viewer, "errors.stats-failed"));
      }
    });
  }

  public void applyClaimRevision() {
    int revision = this.catalog.claimRevision();
    Path file = this.plugin.getDataFolder().toPath().resolve("milestone-claim-revision.txt");
    int stored = 0;
    try {
      if (Files.isRegularFile(file)) {
        String raw = Files.readString(file).trim();
        if (!raw.isEmpty()) {
          stored = Integer.parseInt(raw);
        }
      }
    } catch (IOException | NumberFormatException ignored) {
      stored = 0;
    }
    if (stored == revision) {
      return;
    }
    try {
      int wiped = this.repository.clearAllClaims();
      Files.createDirectories(file.getParent());
      Files.writeString(file, Integer.toString(revision));
      this.plugin.getLogger().info(
          "Milestone claim-revision " + revision + ": azzerati " + wiped + " claim (catch-up al join)."
      );
    } catch (SQLException | IOException exception) {
      this.plugin.getLogger().warning("Reset claim milestone fallito: " + exception.getMessage());
    }
  }

  public void catchUp(Player player) {
    catchUp(player, null);
  }

  public void catchUp(Player player, Runnable after) {
    if (player == null || !player.isOnline() || !this.accepting.get()) {
      if (after != null) {
        this.plugin.tasks().sync(after);
      }
      return;
    }
    UUID uuid = player.getUniqueId();
    this.plugin.tasks().async(() -> {
      if (!beginOperation()) {
        if (after != null) {
          this.plugin.tasks().sync(after);
        }
        return;
      }
      try {
        Map<String, Integer> kills = this.repository.list(uuid);
        this.plugin.tasks().sync(() -> {
          grantCatchUp(uuid, kills);
          if (after != null) {
            after.run();
          }
        });
      } catch (SQLException exception) {
        this.plugin.getLogger().warning("Catch-up milestone fallito: " + exception.getMessage());
        if (after != null) {
          this.plugin.tasks().sync(after);
        }
      } finally {
        endOperation();
      }
    });
  }

  private void grantCatchUp(UUID uuid, Map<String, Integer> kills) {
    Player online = Bukkit.getPlayer(uuid);
    String name = resolveName(uuid, online);
    if (name == null || name.isBlank()) {
      this.plugin.getLogger().warning("Catch-up milestone: nome sconosciuto per " + uuid);
      return;
    }
    int granted = 0;
    for (String mobId : this.catalog.mobIds()) {
      String canonical = this.catalog.canonical(mobId);
      int count = countOf(kills, canonical);
      if (count <= 0) {
        continue;
      }
      for (MilestoneDefinition milestone : this.catalog.of(canonical)) {
        if (count < milestone.threshold) {
          continue;
        }
        try {
          if (!this.repository.claim(uuid, canonical, milestone.threshold)) {
            continue;
          }
        } catch (SQLException exception) {
          this.plugin.getLogger().warning("Claim milestone fallito: " + exception.getMessage());
          continue;
        }
        if (online != null && online.isOnline()) {
          reward(online, canonical, count, milestone);
        } else {
          rewardOffline(name, uuid, canonical, count, milestone);
        }
        granted++;
      }
    }
    if (granted > 0) {
      this.plugin.getLogger().info("Catch-up milestone: " + name + " → " + granted + " reward.");
      if (online != null && online.isOnline()) {
        this.plugin.messages().sendRaw(
            online,
            "{prefix}<green>Hai ricevuto <white>" + granted + "</white> ricompense milestone arretrate.",
            Map.of()
        );
      }
    }
  }

  private static String resolveName(UUID uuid, Player online) {
    if (online != null) {
      return online.getName();
    }
    OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
    String name = offline.getName();
    return name == null || name.isBlank() ? null : name;
  }

  public void catchUpOnline() {
    for (Player player : Bukkit.getOnlinePlayers()) {
      catchUp(player);
    }
  }

  public void catchUpAll() {
    this.plugin.tasks().async(() -> {
      if (!beginOperation()) {
        return;
      }
      try {
        Set<UUID> players = this.repository.listPlayers();
        int total = 0;
        for (UUID uuid : players) {
          Map<String, Integer> kills = this.repository.list(uuid);
          boolean relevant = false;
          for (String mobId : this.catalog.mobIds()) {
            if (countOf(kills, this.catalog.canonical(mobId)) > 0) {
              relevant = true;
              break;
            }
          }
          if (!relevant) {
            continue;
          }
          total++;
          UUID id = uuid;
          Map<String, Integer> snapshot = kills;
          this.plugin.tasks().sync(() -> grantCatchUp(id, snapshot));
        }
        int finalTotal = total;
        this.plugin.tasks().sync(() -> this.plugin.getLogger().info(
            "Catch-up milestone globale: elaborati " + finalTotal + " player con kill boss."
        ));
      } catch (SQLException exception) {
        this.plugin.getLogger().warning("Catch-up globale fallito: " + exception.getMessage());
      } finally {
        endOperation();
      }
    });
  }

  public void withKills(UUID uuid, Consumer<Map<String, Integer>> consumer) {
    this.plugin.tasks().async(() -> {
      Map<String, Integer> kills;
      try {
        kills = this.repository.list(uuid);
      } catch (SQLException exception) {
        this.plugin.getLogger().warning("Lettura kill fallita: " + exception.getMessage());
        kills = Map.of();
      }
      Map<String, Integer> snapshot = kills;
      this.plugin.tasks().sync(() -> consumer.accept(snapshot));
    });
  }

  public MilestoneCatalog catalog() {
    return this.catalog;
  }

  public void shutdown(long timeoutMillis) {
    long deadline = System.currentTimeMillis() + Math.max(0L, timeoutMillis);
    synchronized (this.operationsMonitor) {
      this.accepting.set(false);
      while (this.activeOperations > 0) {
        long remaining = deadline - System.currentTimeMillis();
        if (remaining <= 0L) {
          break;
        }
        try {
          this.operationsMonitor.wait(remaining);
        } catch (InterruptedException exception) {
          Thread.currentThread().interrupt();
          break;
        }
      }
    }
    this.locks.clear();
    this.online.clear();
  }

  public void evict(UUID uuid) {
    this.online.remove(uuid);
  }

  private void record(
      UUID uuid,
      String mobId,
      List<MilestoneDefinition> milestones,
      PlayerCache cache
  ) {
    try {
      int kills = this.repository.increment(uuid, mobId);
      cache.kills.put(mobId, kills);
      if (milestones.isEmpty()) {
        return;
      }
      int next = nextThreshold(milestones, kills);
      int target = next > 0 ? next : lastThreshold(milestones);
      this.plugin.tasks().sync(() -> progress(uuid, mobId, kills, target));
      for (MilestoneDefinition milestone : milestones) {
        if (kills >= milestone.threshold) {
          this.plugin.tasks().sync(() -> grantIfUnclaimed(uuid, mobId, kills, milestone));
        }
      }
    } catch (SQLException exception) {
      this.plugin.getLogger().warning("Kill SQL fallita: " + exception.getMessage());
    }
  }

  private boolean beginOperation() {
    synchronized (this.operationsMonitor) {
      if (!this.accepting.get()) {
        return false;
      }
      this.activeOperations++;
      return true;
    }
  }

  private void endOperation() {
    synchronized (this.operationsMonitor) {
      this.activeOperations--;
      if (this.activeOperations == 0) {
        this.operationsMonitor.notifyAll();
      }
    }
  }

  private void progress(UUID uuid, String mobId, int kills, int threshold) {
    Player player = Bukkit.getPlayer(uuid);
    if (player == null || !player.isOnline()) {
      return;
    }
    this.plugin.messages().send(player, "milestone.progress", Map.of(
        "player", player.getName(),
        "mob", label(mobId),
        "kills", Integer.toString(kills),
        "threshold", Integer.toString(Math.max(1, threshold))
    ));
  }

  private void grantIfUnclaimed(UUID uuid, String mobId, int kills, MilestoneDefinition milestone) {
    Player player = Bukkit.getPlayer(uuid);
    if (player == null || !player.isOnline()) {
      return;
    }
    try {
      if (!this.repository.claim(uuid, mobId, milestone.threshold)) {
        return;
      }
    } catch (SQLException exception) {
      this.plugin.getLogger().warning("Claim milestone fallito: " + exception.getMessage());
      return;
    }
    reward(player, mobId, kills, milestone);
  }

  private void reward(Player player, String mobId, int kills, MilestoneDefinition milestone) {
    Map<String, String> placeholders = Map.of(
        "player", player.getName(),
        "name", player.getName(),
        "uuid", player.getUniqueId().toString(),
        "mob", mobId,
        "kills", Integer.toString(kills)
    );
    Map<String, String> withThreshold = new java.util.HashMap<>(placeholders);
    withThreshold.put("threshold", Integer.toString(milestone.threshold));
    if (milestone.message == null || milestone.message.isEmpty()) {
      this.plugin.messages().send(player, "milestone.reached", withThreshold);
    } else {
      this.plugin.messages().sendRaw(player, milestone.message, withThreshold);
    }
    if (milestone.sound != null && !milestone.sound.isEmpty()) {
      this.plugin.sounds().play(player, milestone.sound);
    }
    List<String> commands = new ArrayList<>(milestone.commands);
    if (!milestone.randomCommands.isEmpty()) {
      commands.add(milestone.randomCommands.get(
          ThreadLocalRandom.current().nextInt(milestone.randomCommands.size())
      ));
    }
    this.plugin.console().run(player, commands, withThreshold);
  }

  private void rewardOffline(
      String playerName,
      UUID uuid,
      String mobId,
      int kills,
      MilestoneDefinition milestone
  ) {
    Map<String, String> withThreshold = new java.util.HashMap<>();
    withThreshold.put("player", playerName);
    withThreshold.put("name", playerName);
    withThreshold.put("uuid", uuid.toString());
    withThreshold.put("mob", mobId);
    withThreshold.put("kills", Integer.toString(kills));
    withThreshold.put("threshold", Integer.toString(milestone.threshold));
    List<String> commands = new ArrayList<>(milestone.commands);
    if (!milestone.randomCommands.isEmpty()) {
      commands.add(milestone.randomCommands.get(
          ThreadLocalRandom.current().nextInt(milestone.randomCommands.size())
      ));
    }
    this.plugin.console().runOffline(playerName, uuid, commands, withThreshold);
  }

  private void sendKills(Player viewer, String targetName, String mobFilter, Map<String, Integer> kills) {
    if (mobFilter != null && !mobFilter.isBlank()) {
      int count = countOf(kills, mobFilter);
      this.plugin.messages().send(viewer, "arena.stats-mob", Map.of(
          "player", targetName,
          "mob", label(mobFilter),
          "kills", Integer.toString(count)
      ));
      return;
    }
    if (kills.isEmpty()) {
      this.plugin.messages().send(viewer, "arena.stats-empty", Map.of("player", targetName));
      return;
    }
    this.plugin.messages().send(viewer, "arena.stats-header", Map.of("player", targetName));
    for (Map.Entry<String, Integer> entry : kills.entrySet()) {
      this.plugin.messages().send(viewer, "arena.stats-entry", Map.of(
          "player", targetName,
          "mob", label(entry.getKey()),
          "kills", Integer.toString(entry.getValue())
      ));
    }
    this.plugin.messages().send(viewer, "arena.stats-footer");
  }

  private void sendMilestones(Player viewer, String targetName, Map<String, Integer> kills) {
    if (this.catalog.mobIds().isEmpty()) {
      this.plugin.messages().send(viewer, "milestone.stats-none");
      return;
    }
    this.plugin.messages().send(viewer, "milestone.stats-header", Map.of("player", targetName));
    for (String mobId : this.catalog.mobIds()) {
      int count = countOf(kills, mobId);
      List<MilestoneDefinition> milestones = this.catalog.of(mobId);
      int next = nextThreshold(milestones, count);
      Map<String, String> placeholders = Map.of(
          "player", targetName,
          "mob", label(mobId),
          "kills", Integer.toString(count),
          "next", next > 0 ? Integer.toString(next) : "-",
          "threshold", next > 0 ? Integer.toString(next) : "-"
      );
      this.plugin.messages().send(
          viewer,
          next > 0 ? "milestone.stats-entry" : "milestone.stats-entry-done",
          placeholders
      );
    }
    this.plugin.messages().send(viewer, "milestone.stats-footer");
  }

  private int countOf(Map<String, Integer> kills, String mobId) {
    Integer exact = kills.get(mobId);
    if (exact != null) {
      return exact;
    }
    String canonical = this.catalog.canonical(mobId);
    Integer byCanonical = kills.get(canonical);
    if (byCanonical != null) {
      return byCanonical;
    }
    for (Map.Entry<String, Integer> entry : kills.entrySet()) {
      if (entry.getKey().equalsIgnoreCase(mobId) || entry.getKey().equalsIgnoreCase(canonical)) {
        return entry.getValue();
      }
    }
    return 0;
  }

  private int nextThreshold(List<MilestoneDefinition> milestones, int kills) {
    for (MilestoneDefinition milestone : milestones) {
      if (kills < milestone.threshold) {
        return milestone.threshold;
      }
    }
    return -1;
  }

  private int lastThreshold(List<MilestoneDefinition> milestones) {
    if (milestones == null || milestones.isEmpty()) {
      return 1;
    }
    return milestones.getLast().threshold;
  }

  private String label(String mobId) {
    String canonical = this.catalog.canonical(mobId);
    ArenaDefinition definition = this.plugin.arenas().catalog().boss(canonical);
    if (definition == null) {
      definition = this.plugin.arenas().catalog().boss(mobId);
    }
    if (definition == null) {
      for (ArenaDefinition candidate : this.plugin.arenas().catalog().bosses()) {
        if (candidate.id.equalsIgnoreCase(mobId) || candidate.id.equalsIgnoreCase(canonical)) {
          definition = candidate;
          break;
        }
      }
    }
    if (definition != null && definition.displayName != null && !definition.displayName.isEmpty()) {
      return definition.displayName;
    }
    return canonical.isEmpty() ? mobId : canonical;
  }

  private static final class PlayerCache {
    private final ConcurrentHashMap<String, Integer> kills = new ConcurrentHashMap<>();
  }
}
