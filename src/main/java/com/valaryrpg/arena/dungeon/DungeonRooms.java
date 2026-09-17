package com.valaryrpg.arena.dungeon;

import com.valaryrpg.arena.ValaryArenaPlugin;
import com.valaryrpg.arena.arena.MythicSpawners;
import com.valaryrpg.arena.arena.SlotState;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.List;
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
        instance.leashed().clear();
        instance.packWaveAt().clear();
        stopPacks(instance);
        showIntro(instance, definition, room);
        for (DungeonPack pack : room.packs) {
            if (pack.trigger == DungeonPackTrigger.IMMEDIATE
                    || (pack.trigger == DungeonPackTrigger.WAVE && room.objective == DungeonObjective.CLEAR_MOBS)) {
                fire(instance, pack, world);
            }
        }
        if (room.hasRadiusPacks() || room.hasLeash()
                || (room.hasWavePacks() && room.objective == DungeonObjective.CLEAR_MOBS)) {
            int ticks = this.catalog.proximityCheckTicks();
            instance.packTask(this.plugin.tasks().repeat(
                    () -> tickRoom(instance, definition, world),
                    ticks,
                    ticks
            ));
        }
        for (Player member : this.plugin.parties().onlineMembers(instance.party())) {
            if (instance.players().contains(member.getUniqueId())) {
                this.plugin.sounds().play(member, "dungeon-room");
            }
        }
        remaining(instance, definition, world);
        if (room.objective == DungeonObjective.CLEAR_MOBS && room.expectedMobs() == 0) {
            clear(instance, definition, world, room);
        }
    }

    public void onMobDeath(DungeonInstance instance, DungeonDefinition definition, World world, String mythicName) {
        if (instance.state() != SlotState.ACTIVE || instance.completing()) {
            return;
        }
        DungeonRoom room = definition.room(instance.roomIndex());
        if (room == null) {
            return;
        }
        if (room.matchesMob(mythicName)) {
            instance.deathsInRoom(instance.deathsInRoom() + 1);
            if (room.objective == DungeonObjective.BOSS && room.isBoss(mythicName)) {
                this.service.complete(instance, definition);
                return;
            }
        }
        int roomIndex = instance.roomIndex();
        tryAdvance(instance, definition, world);
        remaining(instance, definition, world);
        this.plugin.tasks().later(() -> {
            if (instance.roomIndex() != roomIndex) {
                return;
            }
            tryAdvance(instance, definition, world);
            remaining(instance, definition, world);
        }, 2);
    }

    /** Avanza se i pack sono esausti e nel mondo non resta nessun mob della stanza. */
    public void tryAdvance(DungeonInstance instance, DungeonDefinition definition, World world) {
        if (instance.state() != SlotState.ACTIVE || instance.completing()) {
            return;
        }
        DungeonRoom room = definition.room(instance.roomIndex());
        if (room == null || room.objective != DungeonObjective.CLEAR_MOBS) {
            return;
        }
        if (cleared(instance, room, world)) {
            clear(instance, definition, world, room);
        }
    }

    public int remaining(DungeonInstance instance, DungeonDefinition definition, World world) {
        if (!Bukkit.isPrimaryThread()) {
            return instance.cachedRemaining();
        }
        DungeonRoom room = definition == null ? null : definition.room(instance.roomIndex());
        if (room == null) {
            instance.cachedRemaining(0);
            return 0;
        }
        int pending = 0;
        if (room.objective != DungeonObjective.BOSS) {
            for (DungeonPack pack : room.packs) {
                int fired = instance.packFires().getOrDefault(pack.id, 0);
                pending += Math.max(0, pack.times - fired) * pack.amount;
            }
        }
        int alive = world == null ? 0 : alive(room, world);
        int value = room.objective == DungeonObjective.BOSS ? alive : pending + alive;
        instance.cachedRemaining(value);
        return value;
    }

    public int remainingAt(DungeonInstance instance, DungeonDefinition definition, World world, int index) {
        DungeonRoom room = definition.room(index);
        if (room == null) {
            return 0;
        }
        if (index < instance.roomIndex()) {
            return 0;
        }
        if (index > instance.roomIndex()) {
            return room.expectedMobs();
        }
        return remaining(instance, definition, world);
    }

    public void refreshSpawners(DungeonInstance instance, DungeonDefinition definition, World world) {
        // i pack non sono spawner Mythic: un reload non li riduplica
    }

    public void stopPacks(DungeonInstance instance) {
        if (instance.packTask() != null) {
            instance.packTask().cancel();
            instance.packTask(null);
        }
        instance.leashed().clear();
    }

    private void tickRoom(DungeonInstance instance, DungeonDefinition definition, World world) {
        if (instance.state() != SlotState.ACTIVE || instance.completing()) {
            return;
        }
        DungeonRoom room = definition.room(instance.roomIndex());
        if (room == null) {
            return;
        }
        tickRadius(instance, room, world);
        tickWaves(instance, room, world);
        tickLeash(instance, room, world);
        tryAdvance(instance, definition, world);
        remaining(instance, definition, world);
    }

    private void tickRadius(DungeonInstance instance, DungeonRoom room, World world) {
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

    private void tickWaves(DungeonInstance instance, DungeonRoom room, World world) {
        if (room.objective != DungeonObjective.CLEAR_MOBS) {
            return;
        }
        for (DungeonPack pack : room.packs) {
            if (pack.trigger != DungeonPackTrigger.WAVE) {
                continue;
            }
            int fired = instance.packFires().getOrDefault(pack.id, 0);
            if (fired >= pack.times) {
                continue;
            }
            if (pack.waveRequireClear && this.mythic.countInWorld(world, pack::matches) > 0) {
                continue;
            }
            long delayMs = pack.waveDelaySeconds * 1000L;
            long last = instance.packWaveAt().getOrDefault(pack.id, 0L);
            if (delayMs > 0L && System.currentTimeMillis() - last < delayMs) {
                continue;
            }
            fire(instance, pack, world);
        }
    }

    private void tickLeash(DungeonInstance instance, DungeonRoom room, World world) {
        var iterator = instance.leashed().entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            Entity entity = Bukkit.getEntity(entry.getKey());
            if (!(entity instanceof LivingEntity living)
                    || living.isDead()
                    || !living.getWorld().equals(world)) {
                iterator.remove();
                continue;
            }
            DungeonPack pack = room.pack(entry.getValue());
            if (pack == null || !pack.leashed()) {
                iterator.remove();
                continue;
            }
            Location home = pack.point.to(world);
            double max = pack.leashRadius * pack.leashRadius;
            if (living.getLocation().distanceSquared(home) <= max) {
                continue;
            }
            living.teleport(home);
            if (pack.healOnLeash) {
                heal(living);
            }
        }
    }

    private void heal(LivingEntity living) {
        AttributeInstance max = living.getAttribute(Attribute.MAX_HEALTH);
        if (max != null) {
            living.setHealth(max.getValue());
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
        List<LivingEntity> spawned = this.mythic.spawnLiving(
                pack.mob,
                pack.point.to(world),
                pack.amount,
                pack.scatter,
                pack.level,
                instance.roomIndex() == 0 ? this.service.entryWorldSpawn(instance, world) : null,
                instance.roomIndex() == 0 ? this.catalog.spawnSafeRadius() : 0D
        );
        instance.packFires().put(pack.id, fired + 1);
        instance.packWaveAt().put(pack.id, System.currentTimeMillis());
        instance.spawnedInRoom(instance.spawnedInRoom() + spawned.size());
        if (pack.leashed()) {
            for (LivingEntity living : spawned) {
                instance.leashed().put(living.getUniqueId(), pack.id);
            }
        }
        if (spawned.isEmpty()) {
            this.plugin.getLogger().warning("Pack dungeon senza spawn: " + pack.mob + " (" + pack.id + ")");
        }
    }

    private boolean cleared(DungeonInstance instance, DungeonRoom room, World world) {
        for (DungeonPack pack : room.packs) {
            if (instance.packFires().getOrDefault(pack.id, 0) < pack.times) {
                return false;
            }
        }
        return alive(room, world) == 0;
    }

    private void clear(DungeonInstance instance, DungeonDefinition definition, World world, DungeonRoom room) {
        stopPacks(instance);
        if (room.wall != null) {
            fill(room.wall, world);
            this.service.moveHologramToDoor(instance, definition, room);
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
