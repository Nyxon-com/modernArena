package com.valaryrpg.arena.dungeon;

import com.valaryrpg.arena.ValaryArenaPlugin;
import com.valaryrpg.arena.arena.MythicSpawners;
import com.valaryrpg.arena.arena.SlotState;
import com.valaryrpg.arena.arena.SpawnerDefinition;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.mobs.ActiveMob;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;

/** Stanza corrente: title, spawners, remaining, fill cubo. */
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
        clearSpawners(instance);
        showIntro(instance, definition, room);
        createSpawners(instance, definition, room, world);
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
        return Math.max(alive, Math.max(0, room.expectedMobs() - instance.deathsInRoom()));
    }

    public void refreshSpawners(DungeonInstance instance, DungeonDefinition definition, World world) {
        DungeonRoom room = definition.room(instance.roomIndex());
        if (room == null || instance.completing()) {
            return;
        }
        clearSpawners(instance);
        createSpawners(instance, definition, room, world);
    }

    private boolean cleared(DungeonInstance instance, DungeonRoom room, World world) {
        return instance.deathsInRoom() >= room.expectedMobs() && alive(room, world) == 0;
    }

    private void clear(DungeonInstance instance, DungeonDefinition definition, World world, DungeonRoom room) {
        clearSpawners(instance);
        if (room.wall != null) {
            fill(room.wall, world);
        }
        Map<String, String> placeholders = this.service.placeholders(instance, definition);
        this.plugin.parties().broadcast(instance.party(), "dungeon.room-clear", placeholders);
        int next = instance.roomIndex() + 1;
        if (definition.room(next) == null) {
            this.service.complete(instance, definition);
            return;
        }
        start(instance, definition, world, next);
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

    private void createSpawners(
            DungeonInstance instance,
            DungeonDefinition definition,
            DungeonRoom room,
            World world
    ) {
        for (SpawnerDefinition spawner : room.spawners) {
            String name = this.catalog.spawnerName(definition.id, instance.slot(), room.id, spawner.id);
            String created = this.mythic.create(name, spawner.point.to(world), spawner);
            if (created != null) {
                instance.spawnerNames().add(created);
            }
        }
    }

    private void clearSpawners(DungeonInstance instance) {
        for (String name : instance.spawnerNames()) {
            this.mythic.remove(name);
        }
        instance.spawnerNames().clear();
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
        int alive = 0;
        for (ActiveMob mob : new ArrayList<>(MythicBukkit.inst().getMobManager().getActiveMobs())) {
            if (mob.getEntity() == null) {
                continue;
            }
            Entity entity = mob.getEntity().getBukkitEntity();
            if (entity == null || !entity.getWorld().equals(world) || entity.isDead()) {
                continue;
            }
            String name = mob.getType() == null ? "" : mob.getType().getInternalName();
            if (room.matchesMob(name)) {
                alive++;
            }
        }
        return alive;
    }
}
