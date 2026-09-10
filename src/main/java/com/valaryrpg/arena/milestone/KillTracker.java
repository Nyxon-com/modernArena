package com.valaryrpg.arena.milestone;

import com.valaryrpg.arena.ValaryArenaPlugin;
import com.valaryrpg.arena.storage.KillRepository;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Kill SQL async + cache contatori online + reward milestone. */
public final class KillTracker {

    private final ValaryArenaPlugin plugin;
    private final KillRepository repository;
    private final MilestoneCatalog catalog;
    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, ConcurrentHashMap<String, Integer>> online = new ConcurrentHashMap<>();

    public KillTracker(ValaryArenaPlugin plugin, KillRepository repository, MilestoneCatalog catalog) {
        this.plugin = plugin;
        this.repository = repository;
        this.catalog = catalog;
    }

    public void record(Player player, String mobId) {
        List<MilestoneDefinition> milestones = this.catalog.of(mobId);
        UUID uuid = player.getUniqueId();
        String lockKey = uuid + ":" + mobId;
        Object lock = this.locks.computeIfAbsent(lockKey, key -> new Object());
        this.plugin.tasks().async(() -> {
            synchronized (lock) {
                try {
                    int kills = this.repository.increment(uuid, mobId);
                    this.online.computeIfAbsent(uuid, key -> new ConcurrentHashMap<>()).put(mobId, kills);
                    if (milestones.isEmpty()) {
                        return;
                    }
                    Set<Integer> claimed = this.repository.claimed(uuid, mobId);
                    List<MilestoneDefinition> hit = new ArrayList<>();
                    for (MilestoneDefinition milestone : milestones) {
                        if (kills >= milestone.threshold && !claimed.contains(milestone.threshold)) {
                            this.repository.claim(uuid, mobId, milestone.threshold);
                            hit.add(milestone);
                        }
                    }
                    if (!hit.isEmpty()) {
                        this.plugin.tasks().sync(() -> reward(uuid, mobId, kills, hit));
                    }
                } catch (SQLException exception) {
                    this.plugin.getLogger().warning("Kill SQL fallita: " + exception.getMessage());
                }
            }
        });
    }

    public void evict(UUID uuid) {
        this.online.remove(uuid);
    }

    private void reward(UUID uuid, String mobId, int kills, List<MilestoneDefinition> hit) {
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
        for (MilestoneDefinition milestone : hit) {
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
    }
}
