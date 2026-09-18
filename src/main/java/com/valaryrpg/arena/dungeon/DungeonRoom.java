package com.valaryrpg.arena.dungeon;

import com.valaryrpg.arena.arena.Point;

import java.util.List;

/** Stanza cachata da modules/dungeons.yml. */
public final class DungeonRoom {

    public final String id;
    public final String displayName;
    public final DungeonObjective objective;
    public final Point spawn;
    public final DungeonIntro intro;
    public final DungeonIntro outro;
    public final DungeonWall wall;
    public final String bossMob;
    public final List<DungeonPack> packs;
    public final List<DungeonWave> waves;
    public final List<String> commands;

    public DungeonRoom(
            String id,
            String displayName,
            DungeonObjective objective,
            Point spawn,
            DungeonIntro intro,
            DungeonIntro outro,
            DungeonWall wall,
            String bossMob,
            List<DungeonPack> packs,
            List<DungeonWave> waves,
            List<String> commands
    ) {
        this.id = id;
        this.displayName = displayName == null || displayName.isBlank() ? id : displayName;
        this.objective = objective == null ? DungeonObjective.CLEAR_MOBS : objective;
        this.spawn = spawn;
        this.intro = intro;
        this.outro = outro;
        this.wall = wall;
        this.bossMob = bossMob == null ? "" : bossMob;
        this.packs = List.copyOf(packs);
        this.waves = waves == null ? List.of() : List.copyOf(waves);
        this.commands = commands == null ? List.of() : List.copyOf(commands);
    }

    public boolean matchesMob(String mythicName) {
        if (mythicName == null || mythicName.isBlank()) {
            return false;
        }
        for (DungeonPack pack : this.packs) {
            if (pack.matches(mythicName)) {
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
        if (!this.waves.isEmpty()) {
            for (DungeonWave wave : this.waves) {
                total += wave.total();
            }
            for (DungeonPack pack : this.packs) {
                if (pack.trigger != DungeonPackTrigger.WAVE) {
                    total += pack.expected();
                }
            }
            return total;
        }
        for (DungeonPack pack : this.packs) {
            total += pack.expected();
        }
        return total;
    }

    public boolean hasRadiusPacks() {
        for (DungeonPack pack : this.packs) {
            if (pack.trigger == DungeonPackTrigger.RADIUS) {
                return true;
            }
        }
        return false;
    }

    public boolean hasLeash() {
        for (DungeonPack pack : this.packs) {
            if (pack.leashed()) {
                return true;
            }
        }
        return false;
    }

    public boolean hasWaves() {
        return !this.waves.isEmpty();
    }

    public DungeonPack pack(String id) {
        if (id == null) {
            return null;
        }
        for (DungeonPack pack : this.packs) {
            if (pack.id.equals(id)) {
                return pack;
            }
        }
        return null;
    }
}
