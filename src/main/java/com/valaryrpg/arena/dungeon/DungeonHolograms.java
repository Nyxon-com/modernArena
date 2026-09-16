package com.valaryrpg.arena.dungeon;

import com.valaryrpg.arena.ValaryArenaPlugin;
import com.valaryrpg.arena.arena.SlotState;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;

import java.util.List;
import java.util.Map;

/** Hologram TextDisplay: dungeon, stanza, mob rimasti. Solo su slot ACTIVE. */
public final class DungeonHolograms {

    private final ValaryArenaPlugin plugin;
    private final DungeonRooms rooms;

    public DungeonHolograms(ValaryArenaPlugin plugin, DungeonRooms rooms) {
        this.plugin = plugin;
        this.rooms = rooms;
    }

    public void spawn(DungeonInstance instance, DungeonDefinition definition, World world) {
        remove(instance);
        if (!definition.hologram.enabled) {
            return;
        }
        Location location = definition.hologram.point != null
                ? definition.hologram.point.to(world)
                : definition.entrySpawn().to(world).add(0, definition.hologram.offsetY, 0);
        TextDisplay display = world.spawn(location, TextDisplay.class, entity -> {
            entity.setBillboard(Display.Billboard.CENTER);
            entity.setAlignment(TextDisplay.TextAlignment.CENTER);
            entity.setDefaultBackground(false);
            entity.setShadowed(true);
            entity.setSeeThrough(false);
            entity.setPersistent(false);
            entity.setGravity(false);
            entity.setInvulnerable(true);
            entity.addScoreboardTag("valary_dungeon_holo");
        });
        instance.hologram(display);
        refresh(instance, definition);
        instance.hologramTask(this.plugin.tasks().repeat(
                () -> refresh(instance, definition),
                definition.hologram.updateTicks,
                definition.hologram.updateTicks
        ));
    }

    public void refresh(DungeonInstance instance, DungeonDefinition definition) {
        if (instance.state() != SlotState.ACTIVE) {
            return;
        }
        TextDisplay display = instance.hologram();
        if (display == null || display.isDead()) {
            return;
        }
        DungeonRoom room = definition.room(instance.roomIndex());
        int remaining = this.rooms.remaining(instance, definition, display.getWorld());
        String fingerprint = (room == null ? "" : room.id) + "|" + remaining;
        if (fingerprint.equals(instance.hologramFingerprint())) {
            return;
        }
        instance.hologramFingerprint(fingerprint);
        display.text(lines(instance, definition, remaining));
    }

    public void remove(DungeonInstance instance) {
        if (instance.hologramTask() != null) {
            instance.hologramTask().cancel();
            instance.hologramTask(null);
        }
        TextDisplay display = instance.hologram();
        if (display != null) {
            display.remove();
            instance.hologram(null);
        }
    }

    private Component lines(DungeonInstance instance, DungeonDefinition definition, int remaining) {
        DungeonRoom room = definition.room(instance.roomIndex());
        Map<String, String> placeholders = Map.of(
                "dungeon", definition.displayName == null || definition.displayName.isEmpty()
                        ? definition.id : definition.displayName,
                "room", room == null ? "" : room.id,
                "remaining", Integer.toString(remaining)
        );
        List<String> raw = this.plugin.messages().list("hologram.dungeon-lines");
        Component result = Component.empty();
        for (int index = 0; index < raw.size(); index++) {
            if (index > 0) {
                result = result.append(Component.newline());
            }
            result = result.append(this.plugin.messages().mini(raw.get(index), placeholders));
        }
        return result;
    }
}
