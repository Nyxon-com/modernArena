package com.valaryrpg.arena.arena;

import java.util.List;

/** Boss cachato da modules/arenas.yml. */
public final class ArenaDefinition {

    public final String id;
    public final String displayName;
    public final String template;
    public final String permission;
    public final int maxPartySize;
    public final int durationSeconds;
    public final int maxBosses;
    public final int entryCooldownSeconds;
    public final int betweenCooldownSeconds;
    public final HologramSettings hologram;
    public final Point spawn;
    public final List<SpawnerDefinition> spawners;

    public ArenaDefinition(
            String id,
            String displayName,
            String template,
            String permission,
            int maxPartySize,
            int durationSeconds,
            int maxBosses,
            int entryCooldownSeconds,
            int betweenCooldownSeconds,
            HologramSettings hologram,
            Point spawn,
            List<SpawnerDefinition> spawners
    ) {
        this.id = id;
        this.displayName = displayName;
        this.template = template;
        this.permission = permission;
        this.maxPartySize = maxPartySize;
        this.durationSeconds = durationSeconds;
        this.maxBosses = maxBosses;
        this.entryCooldownSeconds = entryCooldownSeconds;
        this.betweenCooldownSeconds = betweenCooldownSeconds;
        this.hologram = hologram;
        this.spawn = spawn;
        this.spawners = List.copyOf(spawners);
    }
}
