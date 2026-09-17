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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Orchestra slot, copy async, load/TP/Mythic sync, unload e dequeue. */
public final class ArenaService {

    private final ValaryArenaPlugin plugin;
    private final ArenaCatalog catalog;
    private final ArenaQueue queue;
    private final WorldCopier copier;
    private final MythicSpawners mythic;
    private final ArenaHolograms holograms;
    private final ArenaCooldowns joinCooldowns;
    private final Map<String, ArenaInstance> byWorld = new HashMap<>();
    private final Map<Path, ArenaInstance> byFolder = new HashMap<>();
    private final Map<String, ArenaInstance> byLoadedName = new HashMap<>();
    private final Map<UUID, ArenaInstance> byPlayer = new HashMap<>();
    private final Map<UUID, ArenaInstance> byParty = new HashMap<>();
    private final Map<UUID, Party> reservedByPlayer = new HashMap<>();
    private boolean cleaning;
    private boolean shuttingDown;

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
        this.holograms = new ArenaHolograms(plugin, mythic, catalog);
        this.joinCooldowns = new ArenaCooldowns();
    }

    public ArenaCatalog catalog() {
        return this.catalog;
    }

    public boolean cleaning() {
        return this.cleaning;
    }

    public boolean occupies(UUID uuid) {
        return this.byPlayer.containsKey(uuid) || this.reservedByPlayer.containsKey(uuid);
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

    public ArenaInstance instanceOf(World world) {
        if (world == null) {
            return null;
        }
        ArenaInstance loaded = this.byLoadedName.get(world.getName());
        if (loaded != null) {
            return loaded;
        }
        ArenaInstance named = this.byWorld.get(world.getName());
        if (named != null) {
            return named;
        }
        return this.byFolder.get(world.getWorldFolder().toPath().toAbsolutePath().normalize());
    }

    public List<ArenaInstance> activeInstances() {
        return this.byWorld.values().stream()
                .filter(instance -> instance.state() == SlotState.ACTIVE)
                .sorted(Comparator.comparing(ArenaInstance::bossId)
                        .thenComparingInt(ArenaInstance::slot))
                .toList();
    }

    public void refreshMythicSpawners() {
        if (this.shuttingDown) {
            return;
        }
        this.plugin.tasks().sync(() -> {
            for (ArenaInstance instance : activeInstances()) {
                ArenaDefinition definition = this.catalog.boss(instance.bossId());
                World world = loadedWorld(instance);
                if (definition == null || world == null || instance.spawnersHalted()) {
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

    public int loadedCount() {
        return this.byWorld.size();
    }

    public void start(Player leader, String bossId) {
        if (this.cleaning || this.shuttingDown) {
            this.plugin.messages().send(leader, "errors.arena-cleaning");
            return;
        }
        if (this.plugin.dungeons() != null && this.plugin.dungeons().cleaning()) {
            this.plugin.messages().send(leader, "errors.dungeon-cleaning");
            return;
        }
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
        if (occupies(leader.getUniqueId())) {
            this.plugin.messages().send(leader, "errors.already-in-arena");
            return;
        }
        if (this.plugin.dungeons() != null && this.plugin.dungeons().occupies(leader.getUniqueId())) {
            this.plugin.messages().send(leader, "errors.already-in-dungeon");
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
            if (occupies(member)) {
                this.plugin.messages().send(leader, "errors.already-in-arena");
                return;
            }
            if (this.plugin.dungeons() != null && this.plugin.dungeons().occupies(member)) {
                this.plugin.messages().send(leader, "errors.already-in-dungeon");
                return;
            }
        }
        if (busy(party)) {
            this.plugin.messages().send(leader, this.queue.contains(party) ? "errors.already-in-queue" : "errors.already-in-arena");
            return;
        }
        if (this.plugin.dungeons() != null && this.plugin.dungeons().busy(party)) {
            this.plugin.messages().send(leader, "errors.already-in-dungeon");
            return;
        }
        if (party.size() > definition.maxPartySize) {
            this.plugin.messages().send(leader, "errors.party-too-big", Map.of("max", Integer.toString(definition.maxPartySize)));
            return;
        }
        if (blockedByCooldown(party, definition, leader)) {
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
        if (this.shuttingDown) {
            return;
        }
        ArenaInstance instance = this.byPlayer.remove(player.getUniqueId());
        if (instance == null) {
            return;
        }
        instance.players().remove(player.getUniqueId());
        instance.eliminated().remove(player.getUniqueId());
        this.plugin.hud().hide(player);
        this.reservedByPlayer.remove(player.getUniqueId());
        this.joinCooldowns.mark(player.getUniqueId(), instance.bossId());
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
        this.plugin.hud().hide(player);
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
        this.joinCooldowns.mark(player.getUniqueId(), instance.bossId());
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
        if (this.shuttingDown) {
            return;
        }
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

    public void partyMemberGone(Party party, UUID member) {
        if (this.shuttingDown) {
            return;
        }
        this.reservedByPlayer.remove(member, party);
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
        for (ArenaDefinition definition : this.catalog.bosses()) {
            for (int slot = 1; slot <= this.catalog.maxPerBoss(); slot++) {
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

    private void cleanupDisk(List<Path> legacyFolders) {
        List<Path> folders = new ArrayList<>();
        try {
            Files.createDirectories(instanceRoot());
            try (var stream = Files.list(instanceRoot())) {
                String prefix = this.catalog.worldPrefix();
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
            this.plugin.getLogger().warning("Cleanup cartelle arena: " + exception.getMessage());
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
                    this.plugin.getLogger().warning("Cleanup cartelle arena: " + exception.getMessage());
                }
        );
        if (!scheduled) {
            this.cleaning = false;
        }
    }

    public void shutdown(long timeoutMillis) {
        this.shuttingDown = true;
        this.cleaning = false;
        this.queue.clear();
        for (ArenaInstance instance : new ArrayList<>(this.byWorld.values())) {
            instance.state(SlotState.UNLOADING);
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

    private void begin(Party party, ArenaDefinition definition, int slot) {
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
        ArenaInstance instance = new ArenaInstance(definition.id, slot, worldName, destination, party);
        this.byWorld.put(worldName, instance);
        this.byFolder.put(destination.toAbsolutePath().normalize(), instance);
        this.byParty.put(party.id(), instance);
        World loadedTemplate = Bukkit.getWorld(definition.template);
        if (loadedTemplate != null) {
            Bukkit.unloadWorld(loadedTemplate, true);
        }
        Path source = worldContainer().resolve(definition.template);
        this.copier.copy(source, destination, this.catalog.copyExclude(), () -> activate(instance, definition), exception -> {
            this.plugin.getLogger().warning("Copia mondo fallita: " + exception.getMessage());
            this.byWorld.remove(worldName);
            this.byFolder.remove(destination.toAbsolutePath().normalize());
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
            return;
        }
        World world = new WorldCreator(worldLoadName(instance.folder())).createWorld();
        if (world == null) {
            this.plugin.getLogger().warning("Load mondo fallito: " + instance.worldName());
            deleteFolderWhenSafe(instance);
            return;
        }
        instance.loadedWorldName(world.getName());
        this.byLoadedName.put(world.getName(), instance);
        Location spawn = definition.spawn.to(world);
        world.setSpawnLocation(spawn);
        this.plugin.configs().instances().tune(world);
        createSpawners(instance, definition, world);
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
        this.holograms.spawn(instance, definition, world);
        this.plugin.hud().show(online);
        if (definition.durationSeconds > 0) {
            instance.durationDeadline(System.currentTimeMillis() + definition.durationSeconds * 1000L);
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
            this.joinCooldowns.mark(uuid, instance.bossId());
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                sendOut(player);
                this.plugin.sounds().play(player, "arena-leave");
            }
        }
        for (UUID uuid : new ArrayList<>(instance.eliminated())) {
            this.byPlayer.remove(uuid, instance);
            this.reservedByPlayer.remove(uuid, instance.party());
            this.joinCooldowns.mark(uuid, instance.bossId());
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

    private void markTeamWipe(ArenaInstance instance) {
        instance.closingAfterWipe(true);
        if (instance.durationTask() != null) {
            instance.durationTask().cancel();
            instance.durationTask(null);
        }
    }

    private void createSpawners(ArenaInstance instance, ArenaDefinition definition, World world) {
        if (instance.spawnersHalted()) {
            return;
        }
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
            if (this.plugin.parties().onlineMembers(party).isEmpty()) {
                clearReservations(party);
                party = this.queue.dequeue(bossId);
                continue;
            }
            ArenaDefinition definition = this.catalog.boss(bossId);
            int slot = definition == null ? -1 : freeSlot(bossId);
            if (definition == null || slot < 0) {
                return;
            }
            if (blockedByCooldown(party, definition, null)) {
                clearReservations(party);
                party = this.queue.dequeue(bossId);
                continue;
            }
            begin(party, definition, slot);
            return;
        }
    }

    public void recordDamage(ArenaInstance instance, UUID mobId, Player player, double amount) {
        if (instance.state() != SlotState.ACTIVE || amount <= 0D) {
            return;
        }
        instance.damageByMob()
                .computeIfAbsent(mobId, ignored -> new HashMap<>())
                .merge(player.getUniqueId(), amount, Double::sum);
    }

    public void trackedBossKilled(ArenaInstance instance, UUID mobId) {
        Map<UUID, Double> damage = instance.damageByMob().remove(mobId);
        if (damage != null && !damage.isEmpty()) {
            UUID top = null;
            double best = 0D;
            for (Map.Entry<UUID, Double> entry : damage.entrySet()) {
                if (entry.getValue() > best) {
                    best = entry.getValue();
                    top = entry.getKey();
                }
            }
            if (top != null) {
                Player player = Bukkit.getPlayer(top);
                instance.previousTopDamager(player != null ? player.getName() : top.toString().substring(0, 8));
                instance.previousTopDamage(best);
            }
        }
        instance.bossesKilled(instance.bossesKilled() + 1);
        ArenaDefinition definition = this.catalog.boss(instance.bossId());
        if (definition != null && definition.maxBosses > 0 && instance.bossesKilled() >= definition.maxBosses) {
            haltSpawners(instance, definition);
        }
        if (definition != null) {
            this.holograms.refresh(instance, definition);
        }
    }

    private void release(ArenaInstance instance) {
        this.byWorld.remove(instance.worldName(), instance);
        this.byFolder.remove(instance.folder().toAbsolutePath().normalize(), instance);
        this.byParty.remove(instance.party().id(), instance);
        clearReservations(instance.party());
    }

    public void quit(Player player) {
        if (this.shuttingDown) {
            return;
        }
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
        int cap = this.plugin.configs().instances().maxLoaded;
        if (cap > 0 && this.plugin.loadedInstances() >= cap) {
            return -1;
        }
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
            if (!isArenaWorld(player.getWorld())) {
                return;
            }
        }
        Location location = this.catalog.leaveLocation();
        if (location != null) {
            player.teleport(location);
        }
    }

    public boolean isArenaWorld(World world) {
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

    private String worldLoadName(Path folder) {
        Path container = worldContainer().toAbsolutePath().normalize();
        return container.relativize(folder.toAbsolutePath().normalize()).toString().replace('\\', '/');
    }

    private World loadedWorld(ArenaInstance instance) {
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
                        .filter(candidate -> candidate != world && !isArenaWorld(candidate)
                                && (this.plugin.dungeons() == null || !this.plugin.dungeons().isDungeonWorld(candidate)))
                        .findFirst()
                        .orElse(null);
                if (fallback != null) {
                    player.teleport(fallback.getSpawnLocation());
                }
            }
        }
        this.mythic.despawnWorld(world);
        if (!Bukkit.unloadWorld(world, false)) {
            this.plugin.getLogger().warning("Unload mondo fallito: " + world.getName());
        }
    }

    private void deleteFolderWhenSafe(ArenaInstance instance) {
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
            this.copier.delete(folder, () -> {
                release(instance);
                if (!this.shuttingDown) {
                    dequeue(instance.bossId());
                }
            });
        };
        if (this.plugin.tasks().later(delete, 5L) == null && !this.shuttingDown) {
            delete.run();
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
        for (ArenaDefinition definition : this.catalog.bosses()) {
            for (int slot = 1; slot <= this.catalog.maxPerBoss(); slot++) {
                if (this.catalog.worldName(definition.id, slot).equals(worldName)) {
                    return true;
                }
            }
        }
        return false;
    }

    private String display(ArenaDefinition definition) {
        return definition.displayName == null || definition.displayName.isEmpty() ? definition.id : definition.displayName;
    }

    private boolean blockedByCooldown(Party party, ArenaDefinition definition, Player notify) {
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
                        "boss", display(definition)
                ));
                this.plugin.sounds().play(target, "arena-cooldown");
            }
            return true;
        }
        return false;
    }

    private void haltSpawners(ArenaInstance instance, ArenaDefinition definition) {
        if (instance.spawnersHalted()) {
            return;
        }
        instance.spawnersHalted(true);
        for (String name : instance.spawnerNames()) {
            this.mythic.remove(name);
        }
        instance.spawnerNames().clear();
        for (UUID uuid : instance.players()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                this.plugin.messages().send(player, "arena.boss-cap", Map.of(
                        "kills", Integer.toString(instance.bossesKilled()),
                        "max", Integer.toString(definition.maxBosses),
                        "boss", display(definition)
                ));
                this.plugin.sounds().play(player, "arena-boss-cap");
            }
        }
        this.plugin.tasks().later(() -> closeAfterCap(instance), this.catalog.bossCapLeaveTicks());
    }

    private void closeAfterCap(ArenaInstance instance) {
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
}
