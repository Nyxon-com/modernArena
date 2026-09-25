package com.valaryrpg.arena.dungeon;

/** Quando spawnare un pack Mythic (niente spawner Mythic). */
public enum DungeonPackTrigger {
    IMMEDIATE,
    RADIUS,
    WAVE,
    DOOR;

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
        if (raw.equalsIgnoreCase("door")
                || raw.equalsIgnoreCase("door-close")
                || raw.equalsIgnoreCase("after-door")) {
            return DOOR;
        }
        return IMMEDIATE;
    }
}
