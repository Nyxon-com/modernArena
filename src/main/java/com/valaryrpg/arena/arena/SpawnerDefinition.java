package com.valaryrpg.arena.arena;

/** Spawner Mythic cachato da modules/arenas.yml. */
public final class SpawnerDefinition {

    public final String id;
    public final String mob;
    public final Point point;
    public final int cooldownSeconds;
    public final int maxMobs;
    public final int spawnRadius;

    public SpawnerDefinition(String id, String mob, Point point, int cooldownSeconds, int maxMobs, int spawnRadius) {
        this.id = id;
        this.mob = mob;
        this.point = point;
        this.cooldownSeconds = cooldownSeconds;
        this.maxMobs = maxMobs;
        this.spawnRadius = spawnRadius;
    }
}
