package com.valaryrpg.arena.milestone;

import com.valaryrpg.arena.ValaryArenaPlugin;
import com.valaryrpg.arena.storage.KillRepository;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.SQLException;
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
            for (MilestoneDefinition milestone : milestones) {
                if (kills == milestone.threshold
                        && this.repository.claim(uuid, mobId, milestone.threshold)) {
                    this.plugin.tasks().sync(() -> reward(uuid, mobId, kills, milestone));
                    return;
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

    private static final class PlayerCache {
        private final ConcurrentHashMap<String, Integer> kills = new ConcurrentHashMap<>();
    }
}
