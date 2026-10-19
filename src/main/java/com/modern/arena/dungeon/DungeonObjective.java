package com.modern.arena.dungeon;

/** Obiettivo di una stanza dungeon. */
public enum DungeonObjective {
    CLEAR_MOBS,
    BOSS;

    public static DungeonObjective parse(String raw) {
        if (raw != null && raw.equalsIgnoreCase("boss")) {
            return BOSS;
        }
        return CLEAR_MOBS;
    }
}
