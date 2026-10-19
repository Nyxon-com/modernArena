package com.modern.arena.dungeon;

import com.modern.arena.ModernArenaPlugin;
import com.modern.arena.arena.ArenaCooldowns;
import com.modern.arena.arena.ArenaQueue;
import com.modern.arena.arena.MythicSpawners;
import com.modern.arena.arena.SlimeInstances;
import com.modern.arena.arena.SlotState;
import com.modern.arena.arena.WorldCopier;
import com.modern.arena.party.Party;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Orchestra slot dungeon, clone slime in RAM, stanze, unload e dequeue. */
public final class DungeonService {

    private final ModernArenaPlugin plugin;
    private final DungeonCatalog catalog;
    private final ArenaQueue queue;
    private final WorldCopier copier;
    private final SlimeInstances slime;
    private final MythicSpawners mythic;
    private final DungeonRooms rooms;
    private final DungeonHolograms holograms;
    private final ArenaCooldowns joinCooldowns;
    private final Map<String, DungeonInstance> byWorld = new HashMap<>();
    private final Map<Path, DungeonInstance> byFolder = new HashMap<>();
    private final Map<String, DungeonInstance> byLoadedName = new HashMap<>();
    private final Map<UUID, DungeonInstance> byPlayer = new HashMap<>();
    private final Map<UUID, DungeonInstance> byParty = new HashMap<>();
    private final Map<UUID, Party> reservedByPlayer = new HashMap<>();
    private boolean cleaning;
    private boolean shuttingDown;

    public DungeonService(
            ModernArenaPlugin plugin,
            DungeonCatalog catalog,
            ArenaQueue queue,
            WorldCopier copier,
            SlimeInstances slime,
            MythicSpawners mythic
    ) {
        this.plugin = plugin;
        this.catalog = catalog;
        this.queue = queue;
        this.copier = copier;
        this.slime = slime;
        this.mythic = mythic;
        this.rooms = new DungeonRooms(plugin, catalog, mythic, this);
        this.holograms = new DungeonHolograms(plugin, this.rooms);
        this.joinCooldowns = new ArenaCooldowns();
    }

    public ModernArenaPlugin plugin() {
        return this.plugin;
    }

    public DungeonCatalog catalog() {
        return this.catalog;
    }

    public DungeonRooms rooms() {
        return this.rooms;
    }

    public Location entryWorldSpawn(DungeonInstance instance, World world) {
        DungeonDefinition definition = this.catalog.dungeon(instance.dungeonId());
        if (definition == null || world == null) {
            return null;
        }
        return definition.entrySpawn().to(world);
    }

    public boolean cleaning() {
        return this.cleaning;
    }

    public DungeonInstance instanceOf(Player player) {
        return this.byPlayer.get(player.getUniqueId());
    }

    public void teleportSpawn(Player player) {
        DungeonInstance instance = instanceOf(player);
        if (instance == null || !instance.players().contains(player.getUniqueId())) {
            this.plugin.messages().send(player, "errors.not-in-dungeon");
            return;
        }
        World world = loadedWorld(instance);
        Location spawn = entryWorldSpawn(instance, world);
        if (world == null || spawn == null) {
            this.plugin.messages().send(player, "errors.not-in-dungeon");
            return;
        }
        player.teleport(spawn);
        this.plugin.messages().send(player, "dungeon.spawn");
    }

    public DungeonInstance instanceOf(World world) {
        if (world == null) {
            return null;
        }
        DungeonInstance loaded = this.byLoadedName.get(world.getName());
        if (loaded != null) {
            return loaded;
        }
        DungeonInstance named = this.byWorld.get(world.getName());
        if (named != null) {
            return named;
        }
        return this.byFolder.get(world.getWorldFolder().toPath().toAbsolutePath().normalize());
    }

    public boolean occupies(UUID uuid) {
        return this.byPlayer.containsKey(uuid) || this.reservedByPlayer.containsKey(uuid);
    }

    public boolean busy(Party party) {
        return party != null && (this.byParty.containsKey(party.id()) || this.queue.contains(party));
    }

    public int loadedCount() {
        return this.byWorld.size();
    }

    public List<DungeonInstance> activeInstances() {
        return this.byWorld.values().stream()
                .filter(instance -> instance.state() == SlotState.ACTIVE)
                .sorted(Comparator.comparing(DungeonInstance::dungeonId)
                        .thenComparingInt(DungeonInstance::slot))
                .toList();
    }

    public Map<String, String> placeholders(DungeonInstance instance, DungeonDefinition definition) {
        DungeonRoom room = definition.room(instance.roomIndex());
        int waves = room == null ? 0 : room.waves.size();
        return Map.of(
                "dungeon", display(definition),
                "display", display(definition),
                "slot", Integer.toString(instance.slot()),
                "world", instance.worldName(),
                "room", room == null ? "" : room.displayName,
                "remaining", Integer.toString(this.rooms.remaining(instance, definition, loadedWorld(instance))),
                "players", Integer.toString(instance.players().size()),
                "wave", Integer.toString(instance.waveIndex()),
                "waves", Integer.toString(waves)
        );
    }

    public void refreshMythicSpawners() {
        if (this.shuttingDown) {
            return;
        }
        this.plugin.tasks().sync(() -> {
            for (DungeonInstance instance : activeInstances()) {
                DungeonDefinition definition = this.catalog.dungeon(instance.dungeonId());
                World world = loadedWorld(instance);
                if (definition == null || world == null || instance.completing()) {
                    continue;
                }
                this.rooms.refreshSpawners(instance, definition, world);
            }
        });
    }

    public void start(Player leader, String dungeonId) {
        start(leader, dungeonId, leader);
    }

    public void start(Player leader, String dungeonId, CommandSender reporter) {
        if (reporter == null) {
            reporter = leader;
        }
        if (this.cleaning || this.shuttingDown) {
            tell(reporter, leader, "errors.dungeon-cleaning");
            return;
        }
        if (this.plugin.arenas() != null && this.plugin.arenas().cleaning()) {
            tell(reporter, leader, "errors.arena-cleaning");
            return;
        }
        DungeonDefinition definition = this.catalog.dungeon(dungeonId);
        if (definition == null) {
            tell(reporter, leader, "errors.unknown-dungeon", Map.of("dungeon", dungeonId));
            return;
        }
        if (definition.permission != null && !definition.permission.isEmpty() && !leader.hasPermission(definition.permission)) {
            tell(reporter, leader, "errors.no-permission");
            this.plugin.sounds().play(leader, "command-denied");
            return;
        }
        if (occupies(leader.getUniqueId())) {
            tell(reporter, leader, "errors.already-in-dungeon");
            return;
        }
        if (this.plugin.arenas() != null && this.plugin.arenas().occupies(leader.getUniqueId())) {
            tell(reporter, leader, "errors.already-in-arena");
            return;
        }
        Party party = this.plugin.parties().partyOf(leader);
        if (party == null) {
            party = new Party(leader.getUniqueId());
        }
        if (!party.isLeader(leader.getUniqueId())) {
            tell(reporter, leader, "errors.not-leader");
            return;
        }
        for (UUID member : party.members()) {
            if (occupies(member)) {
                tell(reporter, leader, "errors.already-in-dungeon");
                return;
            }
            if (this.plugin.arenas() != null && this.plugin.arenas().occupies(member)) {
                tell(reporter, leader, "errors.already-in-arena");
                return;
            }
        }
        if (busy(party)) {
            tell(reporter, leader, this.queue.contains(party) ? "errors.already-in-queue" : "errors.already-in-dungeon");
            return;
        }
        if (this.plugin.arenas() != null && this.plugin.arenas().busy(party)) {
            tell(reporter, leader, "errors.already-in-arena");
            return;
        }
        if (party.size() > definition.maxPartySize) {
            tell(reporter, leader, "errors.party-too-big", Map.of("max", Integer.toString(definition.maxPartySize)));
            return;
        }
        if (blockedByCooldown(party, definition, leader)) {
            if (reporter != leader) {
                this.plugin.messages().send(reporter, "errors.cooldown");
            }
            return;
        }
        Path template = worldContainer().resolve(definition.template);
        if (!Files.isDirectory(template)) {
            tell(reporter, leader, "errors.template-missing", Map.of("world", definition.template));
            return;
        }
        if (!this.slime.available()) {
            tell(reporter, leader, "errors.slime-missing");
            return;
        }
        int slot = freeSlot(definition.id);
        if (slot < 0) {
            int position = this.queue.enqueue(definition.id, party, this.catalog.queueMax());
            if (position < 0) {
                tell(reporter, leader, "errors.queue-full", Map.of(
                        "boss", definition.id,
                        "dungeon", display(definition)
                ));
                return;
            }
            reserve(party);
            this.plugin.parties().broadcast(party, "dungeon.queued", Map.of(
                    "dungeon", display(definition),
                    "position", Integer.toString(position)
            ));
            if (reporter != leader) {
                this.plugin.messages().send(reporter, "dungeon.queued", Map.of(
                        "dungeon", display(definition),
                        "position", Integer.toString(position)
                ));
            }
            for (Player member : this.plugin.parties().onlineMembers(party)) {
                this.plugin.sounds().play(member, "dungeon-queue");
            }
            return;
        }
        begin(party, definition, slot);
        if (reporter != leader) {
            this.plugin.messages().send(reporter, "dungeon.started-for", Map.of(
                    "player", leader.getName(),
                    "dungeon", display(definition)
            ));
        }
    }

    private void tell(CommandSender reporter, Player leader, String path) {
        tell(reporter, leader, path, Map.of());
    }

    private void tell(CommandSender reporter, Player leader, String path, Map<String, String> placeholders) {
        this.plugin.messages().send(leader, path, placeholders);
        if (reporter != null && reporter != leader) {
            this.plugin.messages().send(reporter, path, placeholders);
        }
    }

    public void leave(Player player, boolean teleport) {
        if (this.shuttingDown) {
            return;
        }
        DungeonInstance instance = this.byPlayer.remove(player.getUniqueId());
        if (instance == null) {
            return;
        }
        instance.players().remove(player.getUniqueId());
        instance.eliminated().remove(player.getUniqueId());
        this.plugin.hud().hide(player);
        this.reservedByPlayer.remove(player.getUniqueId());
        this.joinCooldowns.mark(player.getUniqueId(), instance.dungeonId());
        if (teleport) {
            sendOut(player);
            this.plugin.messages().send(player, "dungeon.leave");
            this.plugin.sounds().play(player, "dungeon-leave");
        }
        if (!instance.players().isEmpty()) {
            this.plugin.parties().broadcast(instance.party(), "dungeon.member-left", Map.of("player", player.getName()));
            return;
        }
        if (!instance.eliminated().isEmpty()) {
            markTeamWipe(instance);
            return;
        }
        unload(instance, "dungeon.empty-unload");
    }

    public void eliminate(Player player) {
        DungeonInstance instance = this.byPlayer.get(player.getUniqueId());
        if (instance == null || instance.state() != SlotState.ACTIVE
                || !instance.players().remove(player.getUniqueId())) {
            return;
        }
        instance.eliminated().add(player.getUniqueId());
        this.plugin.hud().hide(player);
        if (instance.players().isEmpty()) {
            markTeamWipe(instance);
        }
    }

    public Location eliminatedRespawn(Player player) {
        DungeonInstance instance = this.byPlayer.get(player.getUniqueId());
        if (instance == null || !instance.eliminated().remove(player.getUniqueId())) {
            return null;
        }
        this.byPlayer.remove(player.getUniqueId(), instance);
        this.reservedByPlayer.remove(player.getUniqueId(), instance.party());
        this.joinCooldowns.mark(player.getUniqueId(), instance.dungeonId());
        boolean close = instance.closingAfterWipe() && instance.eliminated().isEmpty();
        this.plugin.tasks().later(() -> {
            if (player.isOnline()) {
                sendOut(player);
                this.plugin.messages().send(player, "dungeon.eliminated");
                this.plugin.sounds().play(player, "dungeon-leave");
            }
            if (close) {
                unload(instance, null);
            }
        }, 1L);
        return this.catalog.leaveLocation();
    }

    public void partyGone(Party party) {
        if (this.shuttingDown) {
            return;
        }
        String queued = this.queue.bossOf(party);
        if (queued != null) {
            this.queue.remove(party);
            clearReservations(party);
            this.plugin.parties().broadcast(party, "dungeon.queue-removed", Map.of("dungeon", queued));
        }
        DungeonInstance instance = this.byParty.get(party.id());
        if (instance == null || instance.state() == SlotState.UNLOADING) {
            return;
        }
        for (UUID uuid : new ArrayList<>(instance.players())) {
            Player member = Bukkit.getPlayer(uuid);
            if (member != null) {
                leave(member, true);
            } else {
                instance.players().remove(uuid);
                this.byPlayer.remove(uuid);
            }
        }
        if (instance.empty() && instance.state() != SlotState.UNLOADING) {
            unload(instance, "dungeon.empty-unload");
        }
    }

    public void partyMemberGone(Party party, UUID member) {
        if (this.shuttingDown) {
            return;
        }
        this.reservedByPlayer.remove(member, party);
    }

    public void onMobDeath(DungeonInstance instance, String mythicName, UUID entityId) {
        onMobRemoved(instance, mythicName, entityId, true);
    }

    public void onMobRemoved(DungeonInstance instance, String mythicName, UUID entityId, boolean countDeath) {
        DungeonDefinition definition = this.catalog.dungeon(instance.dungeonId());
        World world = loadedWorld(instance);
        if (definition == null || world == null) {
            return;
        }
        this.rooms.onMobRemoved(instance, definition, world, mythicName, entityId, countDeath);
        this.holograms.refresh(instance, definition);
    }

    public void moveHologramToDoor(DungeonInstance instance, DungeonDefinition definition, DungeonRoom room) {
        this.holograms.flyToDoor(instance, definition, room);
    }

    public void complete(DungeonInstance instance, DungeonDefinition definition) {
        complete(instance, definition, false);
    }

    public void complete(DungeonInstance instance, DungeonDefinition definition, boolean roomAlreadyRewarded) {
        if (instance.state() != SlotState.ACTIVE || instance.completing()) {
            return;
        }
        instance.completing(true);
        this.rooms.stopPacks(instance);
        this.rooms.stopDoors(instance);
        if (instance.durationTask() != null) {
            instance.durationTask().cancel();
            instance.durationTask(null);
        }
        for (String name : instance.spawnerNames()) {
            this.mythic.remove(name);
        }
        instance.spawnerNames().clear();
        DungeonRoom room = definition.room(instance.roomIndex());
        if (!roomAlreadyRewarded) {
            this.rooms.celebrate(instance, definition, room);
        }
        Map<String, String> placeholders = placeholders(instance, definition);
        for (UUID uuid : instance.players()) {
            Player member = Bukkit.getPlayer(uuid);
            if (member == null) {
                continue;
            }
            this.plugin.messages().send(member, "dungeon.complete", placeholders);
            this.plugin.sounds().play(member, "dungeon-complete");
        }
        this.plugin.tasks().later(() -> closeAfterComplete(instance), this.catalog.completeLeaveTicks());
    }

    public void cleanup() {
        this.cleaning = true;
        this.queue.clear();
        this.mythic.removePrefix(this.catalog.worldPrefix());
        for (World world : new ArrayList<>(Bukkit.getWorlds())) {
            if (!isInstanceWorld(world) && !isLegacyInstanceWorld(world.getName())) {
                continue;
            }
            unloadLoadedWorld(world);
        }
        List<Path> legacyFolders = new ArrayList<>();
        for (DungeonDefinition definition : this.catalog.dungeons()) {
            for (int slot = 1; slot <= this.catalog.maxPerDungeon(); slot++) {
                legacyFolders.add(worldContainer().resolve(this.catalog.worldName(definition.id, slot)));
            }
        }
        this.byWorld.clear();
        this.byFolder.clear();
        this.byLoadedName.clear();
        this.byPlayer.clear();
        this.byParty.clear();
        this.reservedByPlayer.clear();
        if (this.plugin.tasks().later(() -> cleanupDisk(legacyFolders), 5L) == null) {
            cleanupDisk(legacyFolders);
        }
    }

    public void shutdown(long timeoutMillis) {
        this.shuttingDown = true;
        this.cleaning = false;
        this.queue.clear();
        for (DungeonInstance instance : new ArrayList<>(this.byWorld.values())) {
            instance.state(SlotState.UNLOADING);
            this.rooms.stopPacks(instance);
            this.rooms.stopDoors(instance);
            if (instance.durationTask() != null) {
                instance.durationTask().cancel();
                instance.durationTask(null);
            }
            this.holograms.remove(instance);
            for (String spawner : instance.spawnerNames()) {
                this.mythic.remove(spawner);
            }
            instance.spawnerNames().clear();
            instance.players().clear();
            instance.eliminated().clear();
        }
        for (World world : new ArrayList<>(Bukkit.getWorlds())) {
            if (isInstanceWorld(world) || isLegacyInstanceWorld(world.getName())) {
                unloadLoadedWorld(world);
            }
        }
        this.byWorld.clear();
        this.byFolder.clear();
        this.byLoadedName.clear();
        this.byPlayer.clear();
        this.byParty.clear();
        this.reservedByPlayer.clear();
        this.joinCooldowns.clear();
        this.copier.shutdown(timeoutMillis);
    }

    public void quit(Player player) {
        if (this.shuttingDown) {
            return;
        }
        DungeonInstance instance = instanceOf(player);
        if (instance != null) {
            leave(player, false);
            return;
        }
        Party reservation = this.reservedByPlayer.get(player.getUniqueId());
        if (reservation != null && reservation.size() == 1 && this.queue.remove(reservation)) {
            clearReservations(reservation);
        }
    }

    private void begin(Party party, DungeonDefinition definition, int slot) {
        reserve(party);
        String worldName = this.catalog.worldName(definition.id, slot);
        Path destination = instancePath(worldName);
        World live = worldUsing(destination);
        if (live != null) {
            unloadLoadedWorld(live);
        }
        if (worldUsing(destination) != null) {
            clearReservations(party);
            for (Player member : this.plugin.parties().onlineMembers(party)) {
                this.plugin.messages().send(member, "errors.copy-failed");
            }
            this.plugin.getLogger().warning("Slot ancora caricato, copia saltata: " + destination.getFileName());
            return;
        }
        DungeonInstance instance = new DungeonInstance(definition.id, slot, worldName, destination, party);
        this.byWorld.put(worldName, instance);
        this.byFolder.put(destination.toAbsolutePath().normalize(), instance);
        this.byParty.put(party.id(), instance);
        Path source = worldContainer().resolve(definition.template);
        this.slime.open(definition.template, source.toFile(), worldName, world -> {
            if (instance.state() == SlotState.UNLOADING) {
                unloadLoadedWorld(world);
                return;
            }
            activate(instance, definition, world);
        }, exception -> {
            this.plugin.getLogger().warning("Istanza slime dungeon fallita: " + exception.getMessage());
            this.byWorld.remove(worldName);
            this.byFolder.remove(destination.toAbsolutePath().normalize());
            this.byParty.remove(party.id());
            clearReservations(party);
            for (Player member : this.plugin.parties().onlineMembers(party)) {
                this.plugin.messages().send(member, "errors.copy-failed");
            }
        });
        this.plugin.parties().broadcast(party, "dungeon.started", Map.of(
                "dungeon", display(definition),
                "slot", Integer.toString(slot),
                "world", worldName
        ));
    }

    private void activate(DungeonInstance instance, DungeonDefinition definition, World world) {
        if (instance.state() == SlotState.UNLOADING) {
            return;
        }
        if (world == null) {
            this.plugin.getLogger().warning("Load mondo dungeon fallito: " + instance.worldName());
            deleteFolderWhenSafe(instance);
            return;
        }
        instance.loadedWorldName(world.getName());
        this.byLoadedName.put(world.getName(), instance);
        Location spawn = definition.entrySpawn().to(world);
        world.setSpawnLocation(spawn);
        this.plugin.configs().instances().tune(world);
        List<Player> online = this.plugin.parties().onlineMembers(instance.party());
        if (online.isEmpty()) {
            unload(instance, null);
            return;
        }
        this.plugin.configs().instances().prepareSpawn(
                world,
                spawn,
                this.plugin.tasks(),
                () -> enter(instance, definition, world, spawn)
        );
    }

    private void enter(DungeonInstance instance, DungeonDefinition definition, World world, Location spawn) {
        if (instance.state() == SlotState.UNLOADING) {
            return;
        }
        List<Player> online = this.plugin.parties().onlineMembers(instance.party());
        if (online.isEmpty()) {
            unload(instance, null);
            return;
        }
        for (Player member : online) {
            member.teleport(spawn);
            instance.players().add(member.getUniqueId());
            this.byPlayer.put(member.getUniqueId(), instance);
            this.plugin.sounds().play(member, "dungeon-start");
        }
        instance.state(SlotState.ACTIVE);
        this.holograms.spawn(instance, definition, world);
        this.rooms.start(instance, definition, world, 0);
        this.plugin.hud().showDungeon(online);
        if (definition.durationSeconds > 0) {
            instance.durationDeadline(System.currentTimeMillis() + definition.durationSeconds * 1000L);
            instance.durationTask(this.plugin.tasks().later(
                    () -> timeout(instance),
                    definition.durationSeconds * 20L
            ));
        }
    }

    private void timeout(DungeonInstance instance) {
        if (instance.state() != SlotState.ACTIVE || instance.completing()) {
            return;
        }
        for (UUID uuid : new ArrayList<>(instance.players())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                this.plugin.messages().send(player, "dungeon.timeout");
                leave(player, true);
            } else {
                instance.players().remove(uuid);
                this.byPlayer.remove(uuid);
            }
        }
        if (!instance.empty() || instance.state() == SlotState.ACTIVE) {
            unload(instance, null);
        }
    }

    private void closeAfterComplete(DungeonInstance instance) {
        if (instance.state() != SlotState.ACTIVE) {
            return;
        }
        for (UUID uuid : new ArrayList<>(instance.players())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                leave(player, true);
            } else {
                instance.players().remove(uuid);
                this.byPlayer.remove(uuid);
            }
        }
        if (instance.state() == SlotState.ACTIVE) {
            unload(instance, null);
        }
    }

    private void unload(DungeonInstance instance, String messagePath) {
        if (instance.state() == SlotState.UNLOADING) {
            return;
        }
        instance.state(SlotState.UNLOADING);
        this.rooms.stopPacks(instance);
        this.rooms.stopDoors(instance);
        if (instance.durationTask() != null) {
            instance.durationTask().cancel();
            instance.durationTask(null);
        }
        this.holograms.remove(instance);
        if (messagePath != null) {
            for (UUID uuid : instance.players()) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null) {
                    this.plugin.messages().send(player, messagePath);
                }
            }
        }
        for (UUID uuid : new ArrayList<>(instance.players())) {
            this.byPlayer.remove(uuid, instance);
            this.reservedByPlayer.remove(uuid, instance.party());
            this.joinCooldowns.mark(uuid, instance.dungeonId());
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                sendOut(player);
                this.plugin.sounds().play(player, "dungeon-leave");
            }
        }
        for (UUID uuid : new ArrayList<>(instance.eliminated())) {
            this.byPlayer.remove(uuid, instance);
            this.reservedByPlayer.remove(uuid, instance.party());
            this.joinCooldowns.mark(uuid, instance.dungeonId());
        }
        instance.players().clear();
        instance.eliminated().clear();
        World world = worldUsing(instance.folder());
        if (world == null) {
            world = loadedWorld(instance);
        }
        for (String spawner : instance.spawnerNames()) {
            this.mythic.remove(spawner);
        }
        instance.spawnerNames().clear();
        if (world != null) {
            unloadLoadedWorld(world);
        }
        String loaded = instance.loadedWorldName();
        if (loaded != null) {
            this.byLoadedName.remove(loaded, instance);
        }
        instance.loadedWorldName(null);
        deleteFolderWhenSafe(instance);
    }

    private void markTeamWipe(DungeonInstance instance) {
        instance.closingAfterWipe(true);
        if (instance.durationTask() != null) {
            instance.durationTask().cancel();
            instance.durationTask(null);
        }
    }

    private void dequeue(String dungeonId) {
        Party party = this.queue.dequeue(dungeonId);
        while (party != null) {
            if (this.plugin.parties().onlineMembers(party).isEmpty()) {
                clearReservations(party);
                party = this.queue.dequeue(dungeonId);
                continue;
            }
            DungeonDefinition definition = this.catalog.dungeon(dungeonId);
            int slot = definition == null ? -1 : freeSlot(dungeonId);
            if (definition == null || slot < 0) {
                return;
            }
            if (blockedByCooldown(party, definition, null)) {
                clearReservations(party);
                party = this.queue.dequeue(dungeonId);
                continue;
            }
            begin(party, definition, slot);
            return;
        }
    }

    private void release(DungeonInstance instance) {
        this.byWorld.remove(instance.worldName(), instance);
        this.byFolder.remove(instance.folder().toAbsolutePath().normalize(), instance);
        this.byParty.remove(instance.party().id(), instance);
        clearReservations(instance.party());
    }

    private void reserve(Party party) {
        for (UUID member : party.members()) {
            this.reservedByPlayer.put(member, party);
        }
    }

    private void clearReservations(Party party) {
        for (UUID member : party.members()) {
            this.reservedByPlayer.remove(member, party);
        }
    }

    private int freeSlot(String dungeonId) {
        int cap = this.plugin.configs().instances().maxLoaded;
        if (cap > 0 && this.plugin.loadedInstances() >= cap) {
            return -1;
        }
        if (this.catalog.maxGlobal() > 0 && this.byWorld.size() >= this.catalog.maxGlobal()) {
            return -1;
        }
        int max = this.catalog.maxPerDungeon();
        for (int slot = 1; slot <= max; slot++) {
            String worldName = this.catalog.worldName(dungeonId, slot);
            DungeonInstance existing = this.byWorld.get(worldName);
            if (existing == null) {
                return slot;
            }
        }
        return -1;
    }

    private void sendOut(Player player) {
        String command = this.catalog.leaveCommand();
        if (command != null && !command.isBlank()) {
            player.performCommand(command.startsWith("/") ? command.substring(1) : command);
            if (!isDungeonWorld(player.getWorld())) {
                return;
            }
        }
        Location location = this.catalog.leaveLocation();
        if (location != null) {
            player.teleport(location);
        }
    }

    public boolean isDungeonWorld(World world) {
        return isInstanceWorld(world) || isLegacyInstanceWorld(world.getName());
    }

    private Path worldContainer() {
        return Bukkit.getWorldContainer().toPath();
    }

    private Path instanceRoot() {
        return this.plugin.getDataFolder().toPath().resolve(this.catalog.instancesDirectory());
    }

    private Path instancePath(String worldName) {
        return instanceRoot().resolve(worldName);
    }

    private World loadedWorld(DungeonInstance instance) {
        World byFolder = worldUsing(instance.folder());
        if (byFolder != null) {
            return byFolder;
        }
        String name = instance.loadedWorldName();
        return name == null ? null : Bukkit.getWorld(name);
    }

    private World worldUsing(Path folder) {
        Path expected = folder.toAbsolutePath().normalize();
        for (World world : Bukkit.getWorlds()) {
            if (world.getWorldFolder().toPath().toAbsolutePath().normalize().equals(expected)) {
                return world;
            }
        }
        return null;
    }

    private void unloadLoadedWorld(World world) {
        world.setAutoSave(false);
        for (Player player : new ArrayList<>(world.getPlayers())) {
            sendOut(player);
            if (player.getWorld() == world) {
                World fallback = Bukkit.getWorlds().stream()
                        .filter(candidate -> candidate != world && !isDungeonWorld(candidate)
                                && (this.plugin.arenas() == null || !this.plugin.arenas().isArenaWorld(candidate)))
                        .findFirst()
                        .orElse(null);
                if (fallback != null) {
                    player.teleport(fallback.getSpawnLocation());
                }
            }
        }
        this.mythic.despawnWorld(world);
        if (!Bukkit.unloadWorld(world, false)) {
            this.plugin.getLogger().warning("Unload mondo dungeon fallito: " + world.getName());
        }
    }

    private void deleteFolderWhenSafe(DungeonInstance instance) {
        Path folder = instance.folder();
        Runnable delete = () -> {
            if (this.shuttingDown) {
                return;
            }
            World leftover = worldUsing(folder);
            if (leftover != null) {
                unloadLoadedWorld(leftover);
            }
            if (worldUsing(folder) != null) {
                this.plugin.getLogger().warning("Unload mondo incompleto, cartella lasciata: " + folder.getFileName());
                return;
            }
            this.copier.delete(folder, () -> discardStray(instance));
        };
        if (this.plugin.tasks().later(delete, 5L) == null && !this.shuttingDown) {
            delete.run();
        }
    }

    private void discardStray(DungeonInstance instance) {
        Path stray = worldContainer().resolve(instance.worldName());
        Path folder = instance.folder().toAbsolutePath().normalize();
        if (Files.isDirectory(stray) && !stray.toAbsolutePath().normalize().equals(folder) && worldUsing(stray) == null) {
            this.copier.delete(stray, () -> releaseAndContinue(instance));
            return;
        }
        releaseAndContinue(instance);
    }

    private void releaseAndContinue(DungeonInstance instance) {
        release(instance);
        if (!this.shuttingDown) {
            dequeue(instance.dungeonId());
        }
    }

    private void cleanupDisk(List<Path> legacyFolders) {
        List<Path> folders = new ArrayList<>();
        String prefix = this.catalog.worldPrefix();
        try {
            Files.createDirectories(instanceRoot());
            try (var stream = Files.list(instanceRoot())) {
                for (Path folder : stream.filter(Files::isDirectory).toList()) {
                    if (!folder.getFileName().toString().startsWith(prefix)) {
                        continue;
                    }
                    if (worldUsing(folder) == null) {
                        folders.add(folder);
                    } else {
                        this.plugin.getLogger().warning("Cleanup: mondo ancora caricato, cartella lasciata: " + folder.getFileName());
                    }
                }
            }
        } catch (Exception exception) {
            this.cleaning = false;
            this.plugin.getLogger().warning("Cleanup cartelle dungeon: " + exception.getMessage());
            return;
        }
        for (Path folder : legacyFolders) {
            if (Files.isDirectory(folder) && worldUsing(folder) == null) {
                folders.add(folder);
            }
        }
        boolean scheduled = this.copier.cleanup(
                folders,
                () -> this.cleaning = false,
                exception -> {
                    this.cleaning = false;
                    this.plugin.getLogger().warning("Cleanup cartelle dungeon: " + exception.getMessage());
                }
        );
        if (!scheduled) {
            this.cleaning = false;
        }
    }

    private boolean isInstanceWorld(World world) {
        Path folder = world.getWorldFolder().toPath().toAbsolutePath().normalize();
        Path root = instanceRoot().toAbsolutePath().normalize();
        if (!folder.startsWith(root)) {
            return false;
        }
        return folder.getFileName().toString().startsWith(this.catalog.worldPrefix());
    }

    private boolean isLegacyInstanceWorld(String worldName) {
        for (DungeonDefinition definition : this.catalog.dungeons()) {
            for (int slot = 1; slot <= this.catalog.maxPerDungeon(); slot++) {
                if (this.catalog.worldName(definition.id, slot).equals(worldName)) {
                    return true;
                }
            }
        }
        return false;
    }

    private String display(DungeonDefinition definition) {
        return definition.displayName == null || definition.displayName.isEmpty() ? definition.id : definition.displayName;
    }

    private boolean blockedByCooldown(Party party, DungeonDefinition definition, Player notify) {
        String bypass = this.plugin.configs().permission("arena-cooldown-bypass", "valary.arena.cooldown.bypass");
        for (UUID uuid : party.members()) {
            Player member = Bukkit.getPlayer(uuid);
            if (member != null && member.hasPermission(bypass)) {
                continue;
            }
            ArenaCooldowns.Remaining remaining = this.joinCooldowns.remaining(
                    uuid,
                    definition.id,
                    definition.entryCooldownSeconds,
                    definition.betweenCooldownSeconds
            );
            if (!remaining.blocked()) {
                continue;
            }
            String name = member != null ? member.getName() : uuid.toString().substring(0, 8);
            Player target = notify != null ? notify : member;
            if (target != null) {
                this.plugin.messages().send(target, remaining.messagePath(), Map.of(
                        "player", name,
                        "seconds", Long.toString(remaining.seconds()),
                        "dungeon", display(definition),
                        "boss", display(definition)
                ));
                this.plugin.sounds().play(target, "arena-cooldown");
            }
            return true;
        }
        return false;
    }
}
