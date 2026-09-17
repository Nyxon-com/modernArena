package com.valaryrpg.arena.dungeon;

import java.util.LinkedHashMap;
import java.util.Map;

/** Una ondata: id posizione → quanti mob spawnano da quel punto. */
public final class DungeonWave {

    public final Map<String, Integer> spawns;

    public DungeonWave(Map<String, Integer> spawns) {
        this.spawns = Map.copyOf(spawns == null ? Map.of() : new LinkedHashMap<>(spawns));
    }

    public int total() {
        int total = 0;
        for (int amount : this.spawns.values()) {
            total += Math.max(0, amount);
        }
        return total;
    }
}
