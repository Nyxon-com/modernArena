package com.valaryrpg.arena.command;

import com.valaryrpg.arena.ValaryArenaPlugin;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Cooldown comandi condiviso, ripulito al quit e al disable. */
public final class CommandCooldowns {

    private final ValaryArenaPlugin plugin;
    private final Map<UUID, Map<String, Long>> lastUse = new HashMap<>();

    public CommandCooldowns(ValaryArenaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean cooling(Player player, String command) {
        long wait = this.plugin.configs().root().getLong("delays.command-cooldown-millis", 250L);
        if (wait <= 0L) {
            return false;
        }
        long now = System.currentTimeMillis();
        Map<String, Long> commands = this.lastUse.computeIfAbsent(
                player.getUniqueId(),
                ignored -> new HashMap<>()
        );
        Long last = commands.get(command);
        if (last != null && now - last < wait) {
            return true;
        }
        commands.put(command, now);
        return false;
    }

    public void evict(UUID uuid) {
        this.lastUse.remove(uuid);
    }

    public void clear() {
        this.lastUse.clear();
    }
}
