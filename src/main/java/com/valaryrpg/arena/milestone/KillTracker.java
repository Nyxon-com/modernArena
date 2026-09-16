package com.valaryrpg.arena.milestone;

import com.valaryrpg.arena.ValaryArenaPlugin;
import com.valaryrpg.arena.arena.ArenaDefinition;
import com.valaryrpg.arena.storage.KillRepository;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** Kill SQL async + cache contatori online + reward milestone. */
public final class KillTracker {

    private final ValaryArenaPlugin plugin;
    private final KillRepository repository;
    private final MilestoneCatalog catalog;
    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, PlayerCache> online = new ConcurrentHashMap<>();
    private final AtomicBoolean accepting = new AtomicBoolean(true);
    private final Object operationsMonitor = new Object();
    private int activeOperations;

    public KillTracker(ValaryArenaPlugin plugin, KillRepository repository, MilestoneCatalog catalog) {
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
                if (kills >= milestone.threshold
                        && this.repository.claim(uuid, mobId, milestone.threshold)) {
                    this.plugin.tasks().sync(() -> reward(uuid, mobId, kills, milestone));
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

    private void reward(UUID uuid, String mobId, int kills, MilestoneDefinition milestone) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null || !player.isOnline()) {
            return;
        }
        Map<String, String> placeholders = Map.of(
                "player", player.getName(),
                "name", player.getName(),
                "uuid", uuid.toString(),
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
        this.plugin.console().run(player, milestone.commands, withThreshold);
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
