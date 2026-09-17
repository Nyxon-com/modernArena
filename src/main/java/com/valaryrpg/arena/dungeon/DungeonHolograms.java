package com.valaryrpg.arena.dungeon;

import com.valaryrpg.arena.ValaryArenaPlugin;
import com.valaryrpg.arena.arena.SlotState;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

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
        Location location = spawnLocation(definition, world);
        TextDisplay display = world.spawn(location, TextDisplay.class, entity -> {
            style(entity);
            entity.setTeleportDuration(0);
            pulse(entity, 1f, 0f, 1);
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

    public void flyToDoor(DungeonInstance instance, DungeonDefinition definition, DungeonRoom room) {
        if (!definition.hologram.followDoor || room == null || room.wall == null) {
            return;
        }
        TextDisplay display = instance.hologram();
        if (display == null || display.isDead()) {
            return;
        }
        cancelFly(instance);
        int ticks = definition.hologram.flyTicks;
        Location target = room.wall.center(display.getWorld());
        display.setTeleportDuration(ticks);
        pulse(display, 1.14f, 0.9f, ticks);
        display.teleport(target);
        instance.hologramFlyTask(this.plugin.tasks().later(
                () -> settle(instance, Math.max(1, ticks / 2)),
                ticks
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
        if (room != null && room.objective == DungeonObjective.CLEAR_MOBS && remaining == 0) {
            this.rooms.tryAdvance(instance, definition, display.getWorld());
            room = definition.room(instance.roomIndex());
            remaining = this.rooms.remaining(instance, definition, display.getWorld());
        }
        String fingerprint = (room == null ? "" : room.id) + "|" + remaining + "|" + instance.waveIndex();
        if (fingerprint.equals(instance.hologramFingerprint())) {
            return;
        }
        instance.hologramFingerprint(fingerprint);
        display.text(lines(instance, definition, remaining));
    }

    public void remove(DungeonInstance instance) {
        cancelFly(instance);
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

    private void settle(DungeonInstance instance, int ticks) {
        TextDisplay display = instance.hologram();
        if (display == null || display.isDead()) {
            return;
        }
        display.setTeleportDuration(ticks);
        pulse(display, 1f, 0f, ticks);
    }

    private void cancelFly(DungeonInstance instance) {
        if (instance.hologramFlyTask() != null) {
            instance.hologramFlyTask().cancel();
            instance.hologramFlyTask(null);
        }
    }

    private static void style(TextDisplay entity) {
        entity.setBillboard(Display.Billboard.CENTER);
        entity.setAlignment(TextDisplay.TextAlignment.CENTER);
        entity.setDefaultBackground(false);
        entity.setShadowed(true);
        entity.setSeeThrough(true);
        entity.setPersistent(false);
        entity.setGravity(false);
        entity.setInvulnerable(true);
        entity.setBrightness(new Display.Brightness(15, 15));
        entity.setViewRange(1.2f);
        entity.setLineWidth(220);
    }

    private static void pulse(TextDisplay display, float scale, float lift, int ticks) {
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(Math.max(1, ticks));
        display.setTransformation(new Transformation(
                new Vector3f(0f, lift, 0f),
                new Quaternionf(),
                new Vector3f(scale, scale, scale),
                new Quaternionf()
        ));
    }

    private Component lines(DungeonInstance instance, DungeonDefinition definition, int remaining) {
        DungeonRoom room = definition.room(instance.roomIndex());
        Map<String, String> placeholders = Map.of(
                "dungeon", definition.displayName == null || definition.displayName.isEmpty()
                        ? definition.id : definition.displayName,
                "room", room == null ? "" : room.id,
                "remaining", Integer.toString(remaining),
                "wave", Integer.toString(instance.waveIndex()),
                "waves", Integer.toString(room == null ? 0 : room.waves.size())
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

    private static Location spawnLocation(DungeonDefinition definition, World world) {
        Location spawn = definition.entrySpawn().to(world);
        Location location = spawn.clone().add(0, definition.hologram.offsetY, 0);
        double forward = definition.hologram.spawnForward;
        if (forward <= 0D) {
            return location;
        }
        Vector direction = spawn.getDirection();
        direction.setY(0);
        if (direction.lengthSquared() < 1.0E-6) {
            direction = new Vector(0, 0, 1);
        }
        return location.add(direction.normalize().multiply(forward));
    }
}
