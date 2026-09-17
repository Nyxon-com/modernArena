package com.valaryrpg.arena.dungeon;

/** Quando spawnare un pack Mythic (niente spawner Mythic). */
public enum DungeonPackTrigger {
    IMMEDIATE,
    RADIUS,
    WAVE;

    public static DungeonPackTrigger parse(String raw) {
        if (raw == null) {
            return IMMEDIATE;
        }
        if (raw.equalsIgnoreCase("radius")) {
            return RADIUS;
        }
        if (raw.equalsIgnoreCase("wave")) {
            return WAVE;
        }
        return IMMEDIATE;
    }
}
