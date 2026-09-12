package com.valaryrpg.arena.arena;

import com.valaryrpg.arena.ValaryArenaPlugin;
import com.valaryrpg.arena.party.Party;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Orchestra slot, copy async, load/TP/Mythic sync, unload e dequeue. */
public final class ArenaService {

    private final ValaryArenaPlugin plugin;
    private final ArenaCatalog catalog;
    private final ArenaQueue queue;
    private final WorldCopier copier;
    private final MythicSpawners mythic;
    private final Map<String, ArenaInstance> byWorld = new ConcurrentHashMap<>();
    private final Map<UUID, ArenaInstance> byPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, ArenaInstance> byParty = new ConcurrentHashMap<>();
    private final Map<UUID, Party> reservedByPlayer = new ConcurrentHashMap<>();

    public ArenaService(
            ValaryArenaPlugin plugin,
            ArenaCatalog catalog,
            ArenaQueue queue,
            WorldCopier copier,
            MythicSpawners mythic
    ) {
        this.plugin = plugin;
        this.catalog = catalog;
        this.queue = queue;
        this.copier = copier;
        this.mythic = mythic;
    }

    public ArenaCatalog catalog() {
        return this.catalog;
    }

    public ArenaQueue queue() {
        return this.queue;
    }

    public ArenaInstance instanceOf(Player player) {
        return this.byPlayer.get(player.getUniqueId());
    }

    public ArenaInstance instanceOfWorld(String worldName) {
        return this.byWorld.get(worldName);
    }

    public List<ArenaInstance> activeInstances() {
        return this.byWorld.values().stream()
                .filter(instance -> instance.state() == SlotState.ACTIVE)
                .sorted(Comparator.comparing(ArenaInstance::bossId)
                        .thenComparingInt(ArenaInstance::slot))
                .toList();
    }

    public void refreshMythicSpawners() {
        this.plugin.tasks().sync(() -> {
            for (ArenaInstance instance : activeInstances()) {
                ArenaDefinition definition = this.catalog.boss(instance.bossId());
                World world = Bukkit.getWorld(instance.worldName());
                if (definition == null || world == null) {
                    continue;
                }
                for (String name : instance.spawnerNames()) {
                    this.mythic.remove(name);
                }
                instance.spawnerNames().clear();
                createSpawners(instance, definition, world);
            }
        });
    }

    public boolean busy(Party party) {
        return party != null && (this.byParty.containsKey(party.id()) || this.queue.contains(party));
    }

    public void start(Player leader, String bossId) {
        ArenaDefinition definition = this.catalog.boss(bossId);
        if (definition == null) {
            this.plugin.messages().send(leader, "errors.unknown-boss", Map.of("boss", bossId));
            return;
        }
        if (definition.permission != null && !definition.permission.isEmpty() && !leader.hasPermission(definition.permission)) {
            this.plugin.messages().send(leader, "errors.no-permission");
            this.plugin.sounds().play(leader, "command-denied");
            return;
        }
        if (this.byPlayer.containsKey(leader.getUniqueId())
                || this.reservedByPlayer.containsKey(leader.getUniqueId())) {
            this.plugin.messages().send(leader, "errors.already-in-arena");
            return;
        }
        Party party = this.plugin.parties().partyOf(leader);
        if (party == null) {
            party = new Party(leader.getUniqueId());
        }
        if (!party.isLeader(leader.getUniqueId())) {
            this.plugin.messages().send(leader, "errors.not-leader");
            return;
        }
        for (UUID member : party.members()) {
            if (this.byPlayer.containsKey(member) || this.reservedByPlayer.containsKey(member)) {
                this.plugin.messages().send(leader, "errors.already-in-arena");
                return;
            }
        }
        if (busy(party)) {
            this.plugin.messages().send(leader, this.queue.contains(party) ? "errors.already-in-queue" : "errors.already-in-arena");
            return;
        }
        if (party.size() > definition.maxPartySize) {
            this.plugin.messages().send(leader, "errors.party-too-big", Map.of("max", Integer.toString(definition.maxPartySize)));
            return;
        }
        Path template = worldContainer().resolve(definition.template);
        if (!Files.isDirectory(template)) {
            this.plugin.messages().send(leader, "errors.template-missing", Map.of("world", definition.template));
            return;
        }
        int slot = freeSlot(bossId);
        if (slot < 0) {
            int position = this.queue.enqueue(bossId, party, this.catalog.queueMax());
            if (position < 0) {
                this.plugin.messages().send(leader, "errors.queue-full", Map.of("boss", bossId));
                return;
            }
            reserve(party);
            this.plugin.parties().broadcast(party, "arena.queued", Map.of(
                    "boss", display(definition),
                    "position", Integer.toString(position)
            ));
            for (Player member : this.plugin.parties().onlineMembers(party)) {
                this.plugin.sounds().play(member, "arena-queue");
            }
            return;
        }
        begin(party, definition, slot);
    }

    public void leave(Player player, boolean teleport) {
        ArenaInstance instance = this.byPlayer.remove(player.getUniqueId());
        if (instance == null) {
            return;
        }
        instance.players().remove(player.getUniqueId());
        instance.eliminated().remove(player.getUniqueId());
        this.reservedByPlayer.remove(player.getUniqueId());
        if (teleport) {
            sendOut(player);
            this.plugin.messages().send(player, "arena.leave");
            this.plugin.sounds().play(player, "arena-leave");
        }
        if (!instance.players().isEmpty()) {
            this.plugin.parties().broadcast(instance.party(), "arena.member-left", Map.of("player", player.getName()));
            return;
        }
        if (!instance.eliminated().isEmpty()) {
            markTeamWipe(instance);
            return;
        }
        unload(instance, "arena.empty-unload");
    }

    public void eliminate(Player player) {
        ArenaInstance instance = this.byPlayer.get(player.getUniqueId());
        if (instance == null || instance.state() != SlotState.ACTIVE
                || !instance.players().remove(player.getUniqueId())) {
            return;
        }
        instance.eliminated().add(player.getUniqueId());
        if (instance.players().isEmpty()) {
            markTeamWipe(instance);
        }
    }

    public Location eliminatedRespawn(Player player) {
        ArenaInstance instance = this.byPlayer.get(player.getUniqueId());
        if (instance == null || !instance.eliminated().remove(player.getUniqueId())) {
            return null;
        }
        this.byPlayer.remove(player.getUniqueId(), instance);
        this.reservedByPlayer.remove(player.getUniqueId(), instance.party());
        boolean close = instance.closingAfterWipe() && instance.eliminated().isEmpty();
        this.plugin.tasks().later(() -> {
            if (player.isOnline()) {
                sendOut(player);
                this.plugin.messages().send(player, "arena.eliminated");
                this.plugin.sounds().play(player, "arena-leave");
            }
            if (close) {
                unload(instance, null);
            }
        }, 1L);
        return this.catalog.leaveLocation();
    }

    public void partyGone(Party party) {
        String queued = this.queue.bossOf(party);
        if (queued != null) {
            this.queue.remove(party);
            clearReservations(party);
            this.plugin.parties().broadcast(party, "arena.queue-removed", Map.of("boss", queued));
        }
        ArenaInstance instance = this.byParty.get(party.id());
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
            unload(instance, "arena.empty-unload");
        }
    }

    public void cleanup() {
        this.queue.clear();
        this.mythic.removePrefix(this.catalog.worldPrefix());
        String prefix = this.catalog.worldPrefix();
        for (World world : new ArrayList<>(Bukkit.getWorlds())) {
            if (!world.getName().startsWith(prefix)) {
                continue;
            }
            for (Player player : new ArrayList<>(world.getPlayers())) {
                sendOut(player);
            }
            this.mythic.despawnWorld(world);
            Bukkit.unloadWorld(world, false);
        }
        Path container = worldContainer();
        try (var stream = java.nio.file.Files.list(container)) {
            stream.filter(path -> Files.isDirectory(path) && path.getFileName().toString().startsWith(prefix))
                    .forEach(path -> this.copier.delete(path, null));
        } catch (Exception exception) {
            this.plugin.getLogger().warning("Cleanup cartelle arena: " + exception.getMessage());
        }
        this.byWorld.clear();
        this.byPlayer.clear();
        this.byParty.clear();
        this.reservedByPlayer.clear();
    }

    public void shutdown() {
        for (ArenaInstance instance : new ArrayList<>(this.byWorld.values())) {
            unload(instance, null);
        }
    }

    private void begin(Party party, ArenaDefinition definition, int slot) {
        reserve(party);
        String worldName = this.catalog.worldName(definition.id, slot);
        ArenaInstance instance = new ArenaInstance(definition.id, slot, worldName, party);
        this.byWorld.put(worldName, instance);
        this.byParty.put(party.id(), instance);
        World loadedTemplate = Bukkit.getWorld(definition.template);
        if (loadedTemplate != null) {
            Bukkit.unloadWorld(loadedTemplate, true);
        }
        Path source = worldContainer().resolve(definition.template);
        Path destination = worldContainer().resolve(worldName);
        this.copier.copy(source, destination, this.catalog.copyExclude(), () -> activate(instance, definition), exception -> {
            this.plugin.getLogger().warning("Copia mondo fallita: " + exception.getMessage());
            this.byWorld.remove(worldName);
            this.byParty.remove(party.id());
            clearReservations(party);
            for (Player member : this.plugin.parties().onlineMembers(party)) {
                this.plugin.messages().send(member, "errors.copy-failed");
            }
        });
        this.plugin.parties().broadcast(party, "arena.started", Map.of(
                "boss", display(definition),
                "slot", Integer.toString(slot),
                "world", worldName
        ));
    }

    private void activate(ArenaInstance instance, ArenaDefinition definition) {
        if (instance.state() == SlotState.UNLOADING) {
            this.copier.delete(worldContainer().resolve(instance.worldName()), null);
            release(instance);
            return;
        }
        World world = new WorldCreator(instance.worldName()).createWorld();
        if (world == null) {
            this.plugin.getLogger().warning("Load mondo fallito: " + instance.worldName());
            this.copier.delete(worldContainer().resolve(instance.worldName()), null);
            release(instance);
            return;
        }
        world.setAutoSave(false);
        createSpawners(instance, definition, world);
        Location spawn = definition.spawn.to(world);
        world.setSpawnLocation(spawn);
        List<Player> online = this.plugin.parties().onlineMembers(instance.party());
        if (online.isEmpty()) {
            unload(instance, null);
            return;
        }
        for (Player member : online) {
            member.teleport(spawn);
            instance.players().add(member.getUniqueId());
            this.byPlayer.put(member.getUniqueId(), instance);
            this.plugin.sounds().play(member, "arena-start");
        }
        instance.state(SlotState.ACTIVE);
        if (definition.durationSeconds > 0) {
            instance.durationTask(this.plugin.tasks().later(
                    () -> timeout(instance),
                    definition.durationSeconds * 20L
            ));
        }
    }

    private void timeout(ArenaInstance instance) {
        if (instance.state() != SlotState.ACTIVE) {
            return;
        }
        for (UUID uuid : new ArrayList<>(instance.players())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                this.plugin.messages().send(player, "arena.timeout");
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

    private void unload(ArenaInstance instance, String messagePath) {
        if (instance.state() == SlotState.UNLOADING) {
            return;
        }
        instance.state(SlotState.UNLOADING);
        if (instance.durationTask() != null) {
            instance.durationTask().cancel();
            instance.durationTask(null);
        }
        if (messagePath != null) {
            for (UUID uuid : instance.players()) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null) {
                    this.plugin.messages().send(player, messagePath);
                }
            }
        }
        for (UUID uuid : new ArrayList<>(instance.players())) {
            this.byPlayer.remove(uuid);
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                sendOut(player);
                this.plugin.sounds().play(player, "arena-leave");
            }
        }
        instance.players().clear();
        World world = Bukkit.getWorld(instance.worldName());
        if (world != null) {
            this.mythic.despawnWorld(world);
        }
        for (String spawner : instance.spawnerNames()) {
            this.mythic.remove(spawner);
        }
        instance.spawnerNames().clear();
        if (world != null) {
            Bukkit.unloadWorld(world, false);
        }
        this.copier.delete(worldContainer().resolve(instance.worldName()), () -> {
            release(instance);
            dequeue(instance.bossId());
        });
    }

    private void markTeamWipe(ArenaInstance instance) {
        instance.closingAfterWipe(true);
        if (instance.durationTask() != null) {
            instance.durationTask().cancel();
            instance.durationTask(null);
        }
    }

    private void createSpawners(ArenaInstance instance, ArenaDefinition definition, World world) {
        for (SpawnerDefinition spawner : definition.spawners) {
            String name = this.catalog.spawnerName(definition.id, instance.slot(), spawner.id);
            String created = this.mythic.create(name, spawner.point.to(world), spawner);
            if (created != null) {
                instance.spawnerNames().add(created);
            }
        }
    }

    private void dequeue(String bossId) {
        Party party = this.queue.dequeue(bossId);
        while (party != null) {
            if (!this.plugin.parties().onlineMembers(party).isEmpty()) {
                ArenaDefinition definition = this.catalog.boss(bossId);
                int slot = definition == null ? -1 : freeSlot(bossId);
                if (definition != null && slot > 0) {
                    begin(party, definition, slot);
                }
                return;
            }
            party = this.queue.dequeue(bossId);
        }
    }

    private void release(ArenaInstance instance) {
        this.byWorld.remove(instance.worldName(), instance);
        this.byParty.remove(instance.party().id(), instance);
        clearReservations(instance.party());
    }

    public void quit(Player player) {
        ArenaInstance instance = instanceOf(player);
        if (instance != null) {
            leave(player, false);
            return;
        }
        Party reservation = this.reservedByPlayer.get(player.getUniqueId());
        if (reservation != null && reservation.size() == 1 && this.queue.remove(reservation)) {
            clearReservations(reservation);
        }
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

    private int freeSlot(String bossId) {
        if (this.catalog.maxGlobal() > 0 && busyCount() >= this.catalog.maxGlobal()) {
            return -1;
        }
        int max = this.catalog.maxPerBoss();
        for (int slot = 1; slot <= max; slot++) {
            String worldName = this.catalog.worldName(bossId, slot);
            ArenaInstance existing = this.byWorld.get(worldName);
            if (existing == null) {
                return slot;
            }
        }
        return -1;
    }

    private int busyCount() {
        return this.byWorld.size();
    }

    private void sendOut(Player player) {
        String command = this.catalog.leaveCommand();
        if (command != null && !command.isBlank()) {
            player.performCommand(command.startsWith("/") ? command.substring(1) : command);
            if (!isArenaWorld(player.getWorld().getName())) {
                return;
            }
        }
        Location location = this.catalog.leaveLocation();
        if (location != null) {
            player.teleport(location);
        }
    }

    public boolean isArenaWorld(String worldName) {
        return worldName.startsWith(this.catalog.worldPrefix());
    }

    private Path worldContainer() {
        return Bukkit.getWorldContainer().toPath();
    }

    private String display(ArenaDefinition definition) {
        return definition.displayName == null || definition.displayName.isEmpty() ? definition.id : definition.displayName;
    }
}
