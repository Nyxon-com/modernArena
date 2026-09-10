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
    public final Point spawn;
    public final List<SpawnerDefinition> spawners;

    public ArenaDefinition(
            String id,
            String displayName,
            String template,
            String permission,
            int maxPartySize,
            int durationSeconds,
            Point spawn,
            List<SpawnerDefinition> spawners
    ) {
        this.id = id;
        this.displayName = displayName;
        this.template = template;
        this.permission = permission;
        this.maxPartySize = maxPartySize;
        this.durationSeconds = durationSeconds;
        this.spawn = spawn;
        this.spawners = List.copyOf(spawners);
    }
}
