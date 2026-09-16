package com.valaryrpg.arena.dungeon;

/** Quando spawnare un pack Mythic (niente spawner Mythic). */
public enum DungeonPackTrigger {
    IMMEDIATE,
    RADIUS;

    public static DungeonPackTrigger parse(String raw) {
        if (raw != null && raw.equalsIgnoreCase("radius")) {
            return RADIUS;
        }
        return IMMEDIATE;
    }
}
