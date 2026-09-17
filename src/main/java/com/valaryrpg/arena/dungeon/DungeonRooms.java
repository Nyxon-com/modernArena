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
import java.util.UUID;

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
        stopPacks(instance);
        instance.roomIndex(index);
        instance.deathsInRoom(0);
        instance.spawnedInRoom(0);
        instance.waveIndex(0);
        instance.waveAt(0L);
        instance.waveArmed(false);
        instance.lastMinionAnnounced(false);
        instance.packFires().clear();
        showIntro(instance, definition, room);
        for (DungeonPack pack : room.packs) {
            if (pack.trigger == DungeonPackTrigger.IMMEDIATE) {
                fire(instance, pack, world, pack.amount);
            }
        }
        if (room.hasWaves() && room.objective == DungeonObjective.CLEAR_MOBS) {
            startWaves(instance, definition, room, world);
        }
        if (room.hasRadiusPacks() || room.hasLeash()
                || (room.hasWaves() && room.objective == DungeonObjective.CLEAR_MOBS)) {
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

    public void onMobDeath(DungeonInstance instance, DungeonDefinition definition, World world, String mythicName, UUID deadId) {
        if (instance.state() != SlotState.ACTIVE || instance.completing()) {
            return;
        }
        DungeonRoom room = definition.room(instance.roomIndex());
        if (room == null) {
            return;
        }
        if (deadId != null) {
            boolean glow = deadId.equals(instance.glowing());
            forget(instance, deadId);
            if (glow) {
                unglow(instance);
            }
        }
        if (room.matchesMob(mythicName)) {
            instance.deathsInRoom(instance.deathsInRoom() + 1);
            if (room.objective == DungeonObjective.BOSS && room.isBoss(mythicName)) {
                this.service.complete(instance, definition);
                return;
            }
        }
        int roomIndex = instance.roomIndex();
        highlightLast(instance, definition, world);
        tryAdvance(instance, definition, world);
        remaining(instance, definition, world);
        this.plugin.tasks().later(() -> {
            if (instance.roomIndex() != roomIndex) {
                return;
            }
            highlightLast(instance, definition, world);
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
        if (room.objective != DungeonObjective.BOSS && !room.hasWaves()) {
            for (DungeonPack pack : room.packs) {
                int fired = instance.packFires().getOrDefault(pack.id, 0);
                pending += Math.max(0, pack.times - fired) * pack.amount;
            }
        }
        int alive = world == null ? 0 : pruneLiving(instance, world);
        int value;
        if (room.objective == DungeonObjective.BOSS) {
            value = alive;
        } else if (room.hasWaves()) {
            value = instance.waveLiving().size();
        } else {
            value = pending + alive;
        }
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
        unglow(instance);
        for (UUID id : List.copyOf(instance.roomLiving().keySet())) {
            forget(instance, id);
        }
        instance.leashed().clear();
        instance.roomLiving().clear();
        instance.livingAt().clear();
        instance.waveLiving().clear();
        instance.lastMinionAnnounced(false);
        instance.waveArmed(false);
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
        tickWaves(instance, definition, room, world);
        tickLeash(instance, room, world);
        highlightLast(instance, definition, world);
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
            if (livingOf(instance, world, pack.id) > 0) {
                continue;
            }
            if (!near(instance, pack.point.to(world), pack.radius)) {
                continue;
            }
            fire(instance, pack, world, pack.amount);
        }
    }

    private void tickWaves(DungeonInstance instance, DungeonDefinition definition, DungeonRoom room, World world) {
        if (room.objective != DungeonObjective.CLEAR_MOBS || !room.hasWaves()) {
            return;
        }
        if (instance.waveIndex() >= room.waves.size()) {
            return;
        }
        pruneLiving(instance, world);
        if (this.catalog.waveRequireClear() && !instance.waveLiving().isEmpty()) {
            return;
        }
        if (this.catalog.waveRequireClear() && !instance.waveArmed()) {
            long retryMs = Math.max(1000L, this.catalog.waveDelaySeconds() * 1000L);
            if (instance.waveAt() > 0L && System.currentTimeMillis() - instance.waveAt() < retryMs) {
                return;
            }
            fireWave(instance, definition, room, world);
            return;
        }
        long delayMs = this.catalog.waveDelaySeconds() * 1000L;
        if (delayMs > 0L && System.currentTimeMillis() - instance.waveAt() < delayMs) {
            return;
        }
        fireWave(instance, definition, room, world);
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
            pin(instance, living.getUniqueId(), living.getLocation().clone());
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

    private void fire(DungeonInstance instance, DungeonPack pack, World world, int amount) {
        int fired = instance.packFires().getOrDefault(pack.id, 0);
        if (pack.trigger != DungeonPackTrigger.WAVE && fired >= pack.times) {
            return;
        }
        List<LivingEntity> spawned = this.mythic.spawnLiving(
                pack.mob,
                pack.point.to(world),
                amount,
                pack.scatter,
                pack.level,
                pack.trigger == DungeonPackTrigger.WAVE || instance.roomIndex() != 0
                        ? null
                        : this.service.entryWorldSpawn(instance, world),
                pack.trigger == DungeonPackTrigger.WAVE || instance.roomIndex() != 0
                        ? 0D
                        : this.catalog.spawnSafeRadius()
        );
        if (pack.trigger != DungeonPackTrigger.WAVE) {
            instance.packFires().put(pack.id, fired + 1);
        }
        instance.spawnedInRoom(instance.spawnedInRoom() + spawned.size());
        for (LivingEntity living : spawned) {
            instance.roomLiving().put(living.getUniqueId(), pack.id);
            pin(instance, living.getUniqueId(), living.getLocation().clone());
            if (pack.trigger == DungeonPackTrigger.WAVE) {
                instance.waveLiving().add(living.getUniqueId());
            }
            if (pack.leashed()) {
                instance.leashed().put(living.getUniqueId(), pack.id);
            }
        }
    }

    private void startWaves(DungeonInstance instance, DungeonDefinition definition, DungeonRoom room, World world) {
        int delay = introHoldTicks(room);
        if (delay <= 1) {
            fireWave(instance, definition, room, world);
            return;
        }
        instance.waveArmed(true);
        instance.waveAt(System.currentTimeMillis() + delay * 50L + 2000L);
        int roomIndex = instance.roomIndex();
        this.plugin.tasks().later(() -> {
            if (instance.state() != SlotState.ACTIVE || instance.completing() || instance.roomIndex() != roomIndex) {
                return;
            }
            instance.waveArmed(false);
            instance.waveAt(0L);
            fireWave(instance, definition, room, world);
        }, delay);
    }

    private int introHoldTicks(DungeonRoom room) {
        if (room.intro == null || (room.intro.title.isBlank() && room.intro.subtitle.isBlank())) {
            return 1;
        }
        return Math.max(1, room.intro.fadeInTicks + room.intro.stayTicks);
    }

    private void fireWave(DungeonInstance instance, DungeonDefinition definition, DungeonRoom room, World world) {
        if (instance.waveIndex() >= room.waves.size()) {
            return;
        }
        instance.lastMinionAnnounced(false);
        DungeonWave wave = room.waves.get(instance.waveIndex());
        instance.waveIndex(instance.waveIndex() + 1);
        announceWave(instance, definition);
        instance.waveLiving().clear();
        for (Map.Entry<String, Integer> spawn : wave.spawns.entrySet()) {
            DungeonPack pack = room.pack(spawn.getKey());
            if (pack == null) {
                continue;
            }
            fire(instance, pack, world, spawn.getValue());
        }
        instance.waveAt(System.currentTimeMillis());
        if (instance.waveLiving().isEmpty()) {
            instance.waveIndex(Math.max(0, instance.waveIndex() - 1));
            instance.waveArmed(false);
            return;
        }
        instance.waveArmed(true);
        remaining(instance, definition, world);
    }

    private boolean cleared(DungeonInstance instance, DungeonRoom room, World world) {
        if (instance.waveIndex() < room.waves.size()) {
            return false;
        }
        for (DungeonPack pack : room.packs) {
            if (pack.trigger == DungeonPackTrigger.WAVE) {
                continue;
            }
            if (instance.packFires().getOrDefault(pack.id, 0) < pack.times) {
                return false;
            }
        }
        return pruneLiving(instance, world) == 0;
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

    private int pruneLiving(DungeonInstance instance, World world) {
        var iterator = instance.roomLiving().entrySet().iterator();
        int alive = 0;
        while (iterator.hasNext()) {
            var entry = iterator.next();
            Entity entity = resolve(world, instance, entry.getKey());
            if (!(entity instanceof LivingEntity living)
                    || living.isDead()
                    || (world != null && !living.getWorld().equals(world))) {
                UUID gone = entry.getKey();
                iterator.remove();
                unpin(instance, gone);
                continue;
            }
            pin(instance, entry.getKey(), living.getLocation().clone());
            alive++;
        }
        instance.waveLiving().removeIf(uuid -> !instance.roomLiving().containsKey(uuid));
        return alive;
    }

    private Entity resolve(World world, DungeonInstance instance, UUID uuid) {
        Location at = instance.livingAt().get(uuid);
        if (at != null && at.getWorld() != null) {
            at.getWorld().getChunkAt(at);
        }
        Entity entity = world == null ? null : world.getEntity(uuid);
        return entity != null ? entity : Bukkit.getEntity(uuid);
    }

    private void pin(DungeonInstance instance, UUID id, Location now) {
        Location old = instance.livingAt().put(id, now);
        if (now != null && now.getWorld() != null) {
            now.getChunk().addPluginChunkTicket(this.plugin);
        }
        if (old != null && old.getWorld() != null && (now == null || !sameChunk(old, now))) {
            old.getChunk().removePluginChunkTicket(this.plugin);
        }
    }

    private void forget(DungeonInstance instance, UUID id) {
        instance.roomLiving().remove(id);
        unpin(instance, id);
    }

    private void unpin(DungeonInstance instance, UUID id) {
        Location at = instance.livingAt().remove(id);
        if (at != null && at.getWorld() != null) {
            at.getChunk().removePluginChunkTicket(this.plugin);
        }
        instance.waveLiving().remove(id);
        instance.leashed().remove(id);
    }

    private boolean sameChunk(Location a, Location b) {
        return a.getWorld() != null
                && a.getWorld().equals(b.getWorld())
                && (a.getBlockX() >> 4) == (b.getBlockX() >> 4)
                && (a.getBlockZ() >> 4) == (b.getBlockZ() >> 4);
    }

    private int livingOf(DungeonInstance instance, World world, String packId) {
        pruneLiving(instance, world);
        int count = 0;
        for (String id : instance.roomLiving().values()) {
            if (id.equals(packId)) {
                count++;
            }
        }
        return count;
    }

    private void highlightLast(DungeonInstance instance, DungeonDefinition definition, World world) {
        pruneLiving(instance, world);
        if (!this.catalog.waveHighlightLast()) {
            return;
        }
        if (instance.waveLiving().size() != 1) {
            if (instance.waveLiving().size() > 1) {
                unglow(instance);
                instance.lastMinionAnnounced(false);
            }
            return;
        }
        UUID uuid = instance.waveLiving().iterator().next();
        Entity entity = Bukkit.getEntity(uuid);
        if (entity == null) {
            return;
        }
        if (!(entity instanceof LivingEntity living) || living.isDead()) {
            unglow(instance);
            return;
        }
        glow(living);
        instance.glowing(uuid);
        if (instance.lastMinionAnnounced()) {
            return;
        }
        instance.lastMinionAnnounced(true);
        Map<String, String> placeholders = this.service.placeholders(instance, definition);
        for (Player member : this.plugin.parties().onlineMembers(instance.party())) {
            if (!instance.players().contains(member.getUniqueId())) {
                continue;
            }
            this.plugin.messages().intro(
                    member,
                    "",
                    "",
                    this.plugin.messages().raw("dungeon.last-minion"),
                    0,
                    50,
                    0,
                    placeholders
            );
            this.plugin.sounds().play(member, "dungeon-last-minion");
        }
    }

    private void glow(LivingEntity living) {
        living.setGlowing(true);
        living.addPotionEffect(new org.bukkit.potion.PotionEffect(
                org.bukkit.potion.PotionEffectType.GLOWING,
                20 * 60 * 30,
                0,
                false,
                false,
                false
        ));
    }

    private void unglow(DungeonInstance instance) {
        UUID uuid = instance.glowing();
        if (uuid == null) {
            return;
        }
        Entity entity = Bukkit.getEntity(uuid);
        if (entity instanceof LivingEntity living) {
            living.setGlowing(false);
            living.removePotionEffect(org.bukkit.potion.PotionEffectType.GLOWING);
        }
        instance.glowing(null);
    }

    private void announceWave(DungeonInstance instance, DungeonDefinition definition) {
        Map<String, String> placeholders = this.service.placeholders(instance, definition);
        String title = screen("dungeon.wave-title");
        String subtitle = screen("dungeon.wave-subtitle");
        String actionbar = screen("dungeon.wave");
        for (Player member : this.plugin.parties().onlineMembers(instance.party())) {
            if (!instance.players().contains(member.getUniqueId())) {
                continue;
            }
            this.plugin.messages().intro(
                    member,
                    title,
                    subtitle,
                    actionbar,
                    this.catalog.waveAnnounceFadeInTicks(),
                    this.catalog.waveAnnounceStayTicks(),
                    this.catalog.waveAnnounceFadeOutTicks(),
                    placeholders
            );
            this.plugin.sounds().play(member, "dungeon-wave");
        }
    }

    private String screen(String path) {
        String value = this.plugin.messages().raw(path);
        return value == null || value.isBlank() || value.equals(path) ? "" : value;
    }
}
