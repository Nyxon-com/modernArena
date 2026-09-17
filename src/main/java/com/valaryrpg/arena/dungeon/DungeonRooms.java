package com.valaryrpg.arena.dungeon;

import com.valaryrpg.arena.ValaryArenaPlugin;
import com.valaryrpg.arena.arena.MythicSpawners;
import com.valaryrpg.arena.arena.SlotState;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Map;

/** Stanza corrente: title, pack Mythic (no spawner), remaining, fill cubo. */
public final class DungeonRooms {

    private final ValaryArenaPlugin plugin;
    private final DungeonCatalog catalog;
    private final MythicSpawners mythic;
    private final DungeonService service;

    public DungeonRooms(
            ValaryArenaPlugin plugin,
            DungeonCatalog catalog,
            MythicSpawners mythic,
            DungeonService service
    ) {
        this.plugin = plugin;
        this.catalog = catalog;
        this.mythic = mythic;
        this.service = service;
    }

    public void start(DungeonInstance instance, DungeonDefinition definition, World world, int index) {
        if (instance.state() != SlotState.ACTIVE || instance.completing()) {
            return;
        }
        DungeonRoom room = definition.room(index);
        if (room == null) {
            this.service.complete(instance, definition);
            return;
        }
        instance.roomIndex(index);
        instance.deathsInRoom(0);
        instance.spawnedInRoom(0);
        instance.packFires().clear();
        stopPacks(instance);
        showIntro(instance, definition, room);
        for (DungeonPack pack : room.packs) {
            if (pack.trigger == DungeonPackTrigger.IMMEDIATE) {
                fire(instance, pack, world);
            }
        }
        if (room.hasRadiusPacks()) {
            int ticks = this.catalog.proximityCheckTicks();
            instance.packTask(this.plugin.tasks().repeat(
                    () -> tickRadius(instance, definition, world),
                    ticks,
                    ticks
            ));
        }
        for (Player member : this.plugin.parties().onlineMembers(instance.party())) {
            if (instance.players().contains(member.getUniqueId())) {
                this.plugin.sounds().play(member, "dungeon-room");
            }
        }
        if (room.objective == DungeonObjective.CLEAR_MOBS && room.expectedMobs() == 0) {
            clear(instance, definition, world, room);
        }
    }

    public void onMobDeath(DungeonInstance instance, DungeonDefinition definition, World world, String mythicName) {
        if (instance.state() != SlotState.ACTIVE || instance.completing()) {
            return;
        }
        DungeonRoom room = definition.room(instance.roomIndex());
        if (room == null || !room.matchesMob(mythicName)) {
            return;
        }
        instance.deathsInRoom(instance.deathsInRoom() + 1);
        if (room.objective == DungeonObjective.BOSS && room.isBoss(mythicName)) {
            this.service.complete(instance, definition);
            return;
        }
        if (room.objective == DungeonObjective.CLEAR_MOBS && cleared(instance, room, world)) {
            clear(instance, definition, world, room);
        }
    }

    public int remaining(DungeonInstance instance, DungeonDefinition definition, World world) {
        DungeonRoom room = definition.room(instance.roomIndex());
        if (room == null || world == null) {
            return 0;
        }
        int alive = alive(room, world);
        if (room.objective == DungeonObjective.BOSS) {
            return alive;
        }
        int pending = 0;
        for (DungeonPack pack : room.packs) {
            int fired = instance.packFires().getOrDefault(pack.id, 0);
            pending += Math.max(0, pack.times - fired) * pack.amount;
        }
        return pending + alive;
    }

    public void refreshSpawners(DungeonInstance instance, DungeonDefinition definition, World world) {
        // i pack non sono spawner Mythic: un reload non li riduplica
    }

    public void stopPacks(DungeonInstance instance) {
        if (instance.packTask() != null) {
            instance.packTask().cancel();
            instance.packTask(null);
        }
    }

    private void tickRadius(DungeonInstance instance, DungeonDefinition definition, World world) {
        if (instance.state() != SlotState.ACTIVE || instance.completing()) {
            return;
        }
        DungeonRoom room = definition.room(instance.roomIndex());
        if (room == null) {
            return;
        }
        for (DungeonPack pack : room.packs) {
            if (pack.trigger != DungeonPackTrigger.RADIUS) {
                continue;
            }
            int fired = instance.packFires().getOrDefault(pack.id, 0);
            if (fired >= pack.times) {
                continue;
            }
            if (this.mythic.countInWorld(world, pack::matches) > 0) {
                continue;
            }
            if (!near(instance, pack.point.to(world), pack.radius)) {
                continue;
            }
            fire(instance, pack, world);
        }
    }

    private boolean near(DungeonInstance instance, Location point, double radius) {
        double range = Math.max(1D, radius);
        double rangeSq = range * range;
        for (Player member : this.plugin.parties().onlineMembers(instance.party())) {
            if (!instance.players().contains(member.getUniqueId())) {
                continue;
            }
            if (!member.getWorld().equals(point.getWorld())) {
                continue;
            }
            if (member.getLocation().distanceSquared(point) <= rangeSq) {
                return true;
            }
        }
        return false;
    }

    private void fire(DungeonInstance instance, DungeonPack pack, World world) {
        int fired = instance.packFires().getOrDefault(pack.id, 0);
        if (fired >= pack.times) {
            return;
        }
        int spawned = this.mythic.spawn(pack.mob, pack.point.to(world), pack.amount, pack.scatter, pack.level);
        instance.packFires().put(pack.id, fired + 1);
        instance.spawnedInRoom(instance.spawnedInRoom() + spawned);
        if (spawned == 0) {
            this.plugin.getLogger().warning("Pack dungeon senza spawn: " + pack.mob + " (" + pack.id + ")");
        }
    }

    private boolean cleared(DungeonInstance instance, DungeonRoom room, World world) {
        for (DungeonPack pack : room.packs) {
            if (instance.packFires().getOrDefault(pack.id, 0) < pack.times) {
                return false;
            }
        }
        return instance.deathsInRoom() >= instance.spawnedInRoom() && alive(room, world) == 0;
    }

    private void clear(DungeonInstance instance, DungeonDefinition definition, World world, DungeonRoom room) {
        stopPacks(instance);
        if (room.wall != null) {
            fill(room.wall, world);
        }
        Map<String, String> placeholders = this.service.placeholders(instance, definition);
        celebrate(instance, definition, room);
        this.plugin.parties().broadcast(instance.party(), "dungeon.room-clear", placeholders);
        int next = instance.roomIndex() + 1;
        if (definition.room(next) == null) {
            this.service.complete(instance, definition, true);
            return;
        }
        start(instance, definition, world, next);
    }

    /** Title + comandi YAML a fine stanza, una volta per player vivo. */
    public void celebrate(DungeonInstance instance, DungeonDefinition definition, DungeonRoom room) {
        if (room == null) {
            return;
        }
        Map<String, String> placeholders = this.service.placeholders(instance, definition);
        for (Player member : this.plugin.parties().onlineMembers(instance.party())) {
            if (!instance.players().contains(member.getUniqueId())) {
                continue;
            }
            if (hasScreen(room.outro)) {
                this.plugin.messages().intro(
                        member,
                        room.outro.title,
                        room.outro.subtitle,
                        room.outro.actionbar,
                        room.outro.fadeInTicks,
                        room.outro.stayTicks,
                        room.outro.fadeOutTicks,
                        placeholders
                );
            }
            this.plugin.console().run(member, room.commands, placeholders);
        }
    }

    private boolean hasScreen(DungeonIntro intro) {
        return intro != null
                && (!intro.title.isBlank() || !intro.subtitle.isBlank() || !intro.actionbar.isBlank());
    }

    private void showIntro(DungeonInstance instance, DungeonDefinition definition, DungeonRoom room) {
        if (room.intro == null) {
            return;
        }
        Map<String, String> placeholders = this.service.placeholders(instance, definition);
        for (Player member : this.plugin.parties().onlineMembers(instance.party())) {
            if (!instance.players().contains(member.getUniqueId())) {
                continue;
            }
            this.plugin.messages().intro(
                    member,
                    room.intro.title,
                    room.intro.subtitle,
                    room.intro.actionbar,
                    room.intro.fadeInTicks,
                    room.intro.stayTicks,
                    room.intro.fadeOutTicks,
                    placeholders
            );
        }
    }

    private void fill(DungeonWall wall, World world) {
        Material material = Material.matchMaterial(wall.setBlock.toUpperCase(Locale.ROOT));
        if (material == null) {
            material = Material.AIR;
        }
        for (int x = wall.minX; x <= wall.maxX; x++) {
            for (int y = wall.minY; y <= wall.maxY; y++) {
                for (int z = wall.minZ; z <= wall.maxZ; z++) {
                    world.getBlockAt(x, y, z).setType(material, false);
                }
            }
        }
    }

    private int alive(DungeonRoom room, World world) {
        return this.mythic.countInWorld(world, room::matchesMob);
    }
}
