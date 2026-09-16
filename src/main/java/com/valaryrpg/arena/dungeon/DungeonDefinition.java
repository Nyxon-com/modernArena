package com.valaryrpg.arena.dungeon;

import com.valaryrpg.arena.arena.HologramSettings;
import com.valaryrpg.arena.arena.Point;

import java.util.List;

/** Dungeon cachato da modules/dungeons.yml. */
public final class DungeonDefinition {

    public final String id;
    public final String displayName;
    public final String template;
    public final String permission;
    public final int maxPartySize;
    public final int durationSeconds;
    public final int entryCooldownSeconds;
    public final int betweenCooldownSeconds;
    public final HologramSettings hologram;
    public final List<DungeonRoom> rooms;

    public DungeonDefinition(
            String id,
            String displayName,
            String template,
            String permission,
            int maxPartySize,
            int durationSeconds,
            int entryCooldownSeconds,
            int betweenCooldownSeconds,
            HologramSettings hologram,
            List<DungeonRoom> rooms
    ) {
        this.id = id;
        this.displayName = displayName;
        this.template = template;
        this.permission = permission;
        this.maxPartySize = maxPartySize;
        this.durationSeconds = durationSeconds;
        this.entryCooldownSeconds = entryCooldownSeconds;
        this.betweenCooldownSeconds = betweenCooldownSeconds;
        this.hologram = hologram;
        this.rooms = List.copyOf(rooms);
    }

    public DungeonRoom room(int index) {
        if (index < 0 || index >= this.rooms.size()) {
            return null;
        }
        return this.rooms.get(index);
    }

    public Point entrySpawn() {
        for (DungeonRoom room : this.rooms) {
            if (room.spawn != null) {
                return room.spawn;
            }
        }
        return new Point(0.5, 64, 0.5, 0, 0);
    }
}
