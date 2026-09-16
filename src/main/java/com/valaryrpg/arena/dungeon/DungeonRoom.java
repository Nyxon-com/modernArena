package com.valaryrpg.arena.dungeon;

import com.valaryrpg.arena.arena.Point;
import com.valaryrpg.arena.arena.SpawnerDefinition;

import java.util.List;

/** Stanza cachata da modules/dungeons.yml. */
public final class DungeonRoom {

    public final String id;
    public final DungeonObjective objective;
    public final Point spawn;
    public final DungeonIntro intro;
    public final DungeonWall wall;
    public final String bossMob;
    public final List<SpawnerDefinition> spawners;
    public final List<String> commands;

    public DungeonRoom(
            String id,
            DungeonObjective objective,
            Point spawn,
            DungeonIntro intro,
            DungeonWall wall,
            String bossMob,
            List<SpawnerDefinition> spawners,
            List<String> commands
    ) {
        this.id = id;
        this.objective = objective == null ? DungeonObjective.CLEAR_MOBS : objective;
        this.spawn = spawn;
        this.intro = intro;
        this.wall = wall;
        this.bossMob = bossMob == null ? "" : bossMob;
        this.spawners = List.copyOf(spawners);
        this.commands = commands == null ? List.of() : List.copyOf(commands);
    }

    public boolean matchesMob(String mythicName) {
        if (mythicName == null || mythicName.isBlank()) {
            return false;
        }
        for (SpawnerDefinition spawner : this.spawners) {
            if (spawner.mob != null && !spawner.mob.isBlank() && spawner.mob.equalsIgnoreCase(mythicName)) {
                return true;
            }
        }
        return this.bossMob != null && !this.bossMob.isBlank() && this.bossMob.equalsIgnoreCase(mythicName);
    }

    public boolean isBoss(String mythicName) {
        if (this.objective != DungeonObjective.BOSS || mythicName == null || mythicName.isBlank()) {
            return false;
        }
        if (!this.bossMob.isBlank()) {
            return this.bossMob.equalsIgnoreCase(mythicName);
        }
        return matchesMob(mythicName);
    }

    public int expectedMobs() {
        int total = 0;
        for (SpawnerDefinition spawner : this.spawners) {
            total += Math.max(0, spawner.maxMobs);
        }
        return total;
    }
}
