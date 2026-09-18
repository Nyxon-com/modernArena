package com.valaryrpg.arena.dungeon;

import com.valaryrpg.arena.ValaryArenaPlugin;
import com.valaryrpg.arena.arena.HologramSettings;
import com.valaryrpg.arena.arena.Point;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Cache in RAM di settings, dungeon e stanze. */
public final class DungeonCatalog {

    private final ValaryArenaPlugin plugin;
    private final Map<String, DungeonDefinition> dungeons = new LinkedHashMap<>();
    private String worldPrefix = "dungeon_";
    private String worldNameFormat = "dungeon_{dungeon}_{slot}";
    private String spawnerNameFormat = "dungeon_{dungeon}_{slot}_{room}_{spawner}";
    private String instancesDirectory = "instances";
    private int maxPerDungeon = 4;
    private int maxGlobal;
    private int queueMax = 20;
    private List<String> copyExclude = List.of("uid.dat", "session.lock");
    private String leaveCommand = "spawn";
    private String leaveWorld = "world";
    private Point leavePoint = new Point(0.5, 64, 0.5, 0, 0);
    private int completeLeaveTicks = 40;
    private int entryCooldownSeconds;
    private int betweenCooldownSeconds;
    private boolean hologramEnabled = true;
    private int hologramUpdateTicks = 40;
    private double hologramOffsetY = 2.5;
    private boolean hologramFollowDoor = true;
    private int hologramFlyTicks = 18;
    private int proximityCheckTicks = 10;
    private double leashRadius;
    private boolean healOnLeash = true;
    private double spawnSafeRadius = 6;
    private double hologramSpawnForward = 2.5;
    private int waveDelaySeconds;
    private boolean waveRequireClear = true;
    private boolean waveHighlightLast = true;
    private int waveAnnounceFadeInTicks = 10;
    private int waveAnnounceStayTicks = 40;
    private int waveAnnounceFadeOutTicks = 10;
    private String wallOpenBlock = "air";
    private String wallCloseBlock = "iron_bars";
    private boolean wallOpenOnStart;
    private boolean wallCloseOnPass = true;
    private int wallCloseDelayTicks;
    private double wallPassMargin = 0.4;
    private boolean wallPhysics;
    private List<String> wallCloseFaces = List.of();
    private double wallPassDetect = 8;

    public DungeonCatalog(ValaryArenaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        this.dungeons.clear();
        FileConfiguration yaml = this.plugin.configs().dungeons();
        this.worldPrefix = yaml.getString("settings.world-prefix", "dungeon_");
        this.worldNameFormat = yaml.getString("settings.world-name-format", "dungeon_{dungeon}_{slot}");
        this.spawnerNameFormat = yaml.getString(
                "settings.spawner-name-format",
                "dungeon_{dungeon}_{slot}_{room}_{spawner}"
        );
        this.instancesDirectory = yaml.getString("settings.instances-directory", "instances");
        this.maxPerDungeon = yaml.getInt("settings.max-instances-per-dungeon", 4);
        this.maxGlobal = yaml.getInt("settings.max-instances-global", 0);
        this.copyExclude = yaml.getStringList("settings.copy-exclude");
        if (this.copyExclude.isEmpty()) {
            this.copyExclude = List.of("uid.dat", "session.lock");
        }
        this.completeLeaveTicks = Math.max(1, yaml.getInt("settings.complete-leave-ticks", 40));
        this.entryCooldownSeconds = yaml.getInt("settings.entry-cooldown-seconds", 0);
        this.betweenCooldownSeconds = yaml.getInt("settings.between-cooldown-seconds", 0);
        this.queueMax = yaml.getInt("queue.max-size", 20);
        this.leaveCommand = yaml.getString("leave.command", "spawn");
        this.leaveWorld = yaml.getString("leave.location.world", "world");
        this.leavePoint = point(yaml.getConfigurationSection("leave.location"), 0.5, 64, 0.5);
        this.hologramEnabled = yaml.getBoolean("settings.hologram.enabled", true);
        this.hologramUpdateTicks = yaml.getInt("settings.hologram.update-ticks", 40);
        this.hologramOffsetY = yaml.getDouble("settings.hologram.offset-y", 2.5);
        this.hologramFollowDoor = yaml.getBoolean("settings.hologram.follow-door", true);
        this.hologramFlyTicks = yaml.getInt("settings.hologram.fly-ticks", 18);
        this.proximityCheckTicks = Math.max(5, yaml.getInt("settings.proximity-check-ticks", 10));
        this.leashRadius = Math.max(0D, yaml.getDouble("settings.leash.radius", 0D));
        this.healOnLeash = yaml.getBoolean("settings.leash.heal", true);
        this.spawnSafeRadius = Math.max(0D, yaml.getDouble("settings.spawn-safe-radius", 6D));
        this.hologramSpawnForward = Math.max(0D, yaml.getDouble("settings.hologram.spawn-forward", 2.5));
        this.waveDelaySeconds = Math.max(0, yaml.getInt(
                "settings.waves.delay-seconds",
                yaml.getInt("settings.wave-delay-seconds", 0)
        ));
        this.waveRequireClear = yaml.getBoolean(
                "settings.waves.require-clear",
                yaml.getBoolean("settings.wave-require-clear", true)
        );
        this.waveHighlightLast = yaml.getBoolean("settings.waves.highlight-last", true);
        this.waveAnnounceFadeInTicks = Math.max(0, yaml.getInt("settings.waves.announce-fade-in-ticks", 10));
        this.waveAnnounceStayTicks = Math.max(1, yaml.getInt("settings.waves.announce-stay-ticks", 40));
        this.waveAnnounceFadeOutTicks = Math.max(0, yaml.getInt("settings.waves.announce-fade-out-ticks", 10));
        this.wallOpenBlock = firstNonBlank(
                yaml.getString("settings.wall.open-block"),
                yaml.getString("settings.wall.set-block"),
                "air"
        );
        this.wallCloseBlock = firstNonBlank(yaml.getString("settings.wall.close-block"), "iron_bars");
        this.wallOpenOnStart = "start".equalsIgnoreCase(yaml.getString("settings.wall.open-on", "clear"));
        this.wallCloseOnPass = !"never".equalsIgnoreCase(yaml.getString("settings.wall.close-on", "pass"));
        this.wallCloseDelayTicks = Math.max(0, yaml.getInt("settings.wall.close-delay-ticks", 0));
        this.wallPassMargin = Math.max(0D, yaml.getDouble("settings.wall.pass-margin", 0.4));
        this.wallPhysics = yaml.getBoolean("settings.wall.physics", false);
        this.wallCloseFaces = List.copyOf(yaml.getStringList("settings.wall.close-faces"));
        this.wallPassDetect = Math.max(1D, yaml.getDouble("settings.wall.pass-detect", 8D));

        ConfigurationSection section = yaml.getConfigurationSection("dungeons");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection node = section.getConfigurationSection(id);
            if (node == null) {
                continue;
            }
            List<DungeonRoom> rooms = rooms(node.getConfigurationSection("rooms"));
            if (rooms.isEmpty()) {
                continue;
            }
            this.dungeons.put(id, new DungeonDefinition(
                    id,
                    node.getString("display-name", id),
                    node.getString("template", ""),
                    node.getString("permission", ""),
                    node.getInt("max-party-size", 4),
                    node.getInt("duration-seconds", 0),
                    node.contains("entry-cooldown-seconds")
                            ? node.getInt("entry-cooldown-seconds")
                            : this.entryCooldownSeconds,
                    node.contains("between-cooldown-seconds")
                            ? node.getInt("between-cooldown-seconds")
                            : this.betweenCooldownSeconds,
                    hologram(node),
                    rooms
            ));
        }
    }

    public DungeonDefinition dungeon(String id) {
        return this.dungeons.get(id);
    }

    public Collection<DungeonDefinition> dungeons() {
        return this.dungeons.values();
    }

    public Collection<String> dungeonIds() {
        return this.dungeons.keySet();
    }

    public String worldPrefix() {
        return this.worldPrefix;
    }

    public String worldName(String dungeon, int slot) {
        return this.worldNameFormat
                .replace("{dungeon}", dungeon)
                .replace("{slot}", Integer.toString(slot));
    }

    public String spawnerName(String dungeon, int slot, String room, String spawner) {
        return this.spawnerNameFormat
                .replace("{dungeon}", dungeon)
                .replace("{slot}", Integer.toString(slot))
                .replace("{room}", room)
                .replace("{spawner}", spawner);
    }

    public String instancesDirectory() {
        return this.instancesDirectory;
    }

    public int maxPerDungeon() {
        return this.maxPerDungeon;
    }

    public int maxGlobal() {
        return this.maxGlobal;
    }

    public int queueMax() {
        return this.queueMax;
    }

    public List<String> copyExclude() {
        return this.copyExclude;
    }

    public int completeLeaveTicks() {
        return this.completeLeaveTicks;
    }

    public int proximityCheckTicks() {
        return this.proximityCheckTicks;
    }

    public int waveDelaySeconds() {
        return this.waveDelaySeconds;
    }

    public boolean waveRequireClear() {
        return this.waveRequireClear;
    }

    public boolean waveHighlightLast() {
        return this.waveHighlightLast;
    }

    public int waveAnnounceFadeInTicks() {
        return this.waveAnnounceFadeInTicks;
    }

    public int waveAnnounceStayTicks() {
        return this.waveAnnounceStayTicks;
    }

    public int waveAnnounceFadeOutTicks() {
        return this.waveAnnounceFadeOutTicks;
    }

    public double spawnSafeRadius() {
        return this.spawnSafeRadius;
    }

    public String leaveCommand() {
        return this.leaveCommand;
    }

    public Location leaveLocation() {
        World world = Bukkit.getWorld(this.leaveWorld);
        if (world == null) {
            world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().getFirst();
        }
        if (world == null) {
            return null;
        }
        return this.leavePoint.to(world);
    }

    private List<DungeonRoom> rooms(ConfigurationSection section) {
        List<DungeonRoom> rooms = new ArrayList<>();
        if (section == null) {
            return rooms;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection node = section.getConfigurationSection(id);
            if (node == null) {
                continue;
            }
            List<DungeonPack> packs = packs(node);
            DungeonObjective objective = DungeonObjective.parse(node.getString("objective", "clear-mobs"));
            String bossMob = node.getString("boss-mob", "");
            if (bossMob.isBlank() && objective == DungeonObjective.BOSS && !packs.isEmpty()) {
                bossMob = packs.getFirst().mob;
            }
            boolean startOnRadius = "radius".equalsIgnoreCase(node.getString("start-on", "immediate"))
                    || "enter".equalsIgnoreCase(node.getString("start-on", "immediate"));
            rooms.add(new DungeonRoom(
                    id,
                    node.getString("display-name", id),
                    objective,
                    node.isConfigurationSection("spawn") ? point(node.getConfigurationSection("spawn"), 0.5, 64, 0.5) : null,
                    intro(node.getConfigurationSection("intro")),
                    optionalIntro(node.getConfigurationSection("outro")),
                    wall(node.getConfigurationSection("wall")),
                    bossMob,
                    packs,
                    waves(node, packs),
                    commands(node),
                    startOnRadius,
                    node.getDouble("start-radius", 12D),
                    node.isConfigurationSection("start") ? point(node.getConfigurationSection("start"), 0.5, 64, 0.5) : null
            ));
        }
        return rooms;
    }

    private List<DungeonPack> packs(ConfigurationSection room) {
        ConfigurationSection section = room.getConfigurationSection("mobs");
        if (section == null) {
            section = room.getConfigurationSection("spawners");
        }
        List<DungeonPack> packs = new ArrayList<>();
        if (section == null) {
            return packs;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection node = section.getConfigurationSection(id);
            if (node == null) {
                continue;
            }
            int amount = node.contains("amount") ? node.getInt("amount") : node.getInt("max-mobs", 1);
            packs.add(new DungeonPack(
                    id,
                    node.getString("mob", id),
                    point(node, 0, 64, 0),
                    DungeonPackTrigger.parse(node.getString("trigger", "immediate")),
                    node.getDouble("radius", 8D),
                    amount,
                    node.getInt("times", 1),
                    node.getDouble("scatter", node.getDouble("spawn-radius", 0D)),
                    node.getInt("level", 1),
                    leashRadius(node),
                    healOnLeash(node)
            ));
        }
        return packs;
    }

    private List<DungeonWave> waves(ConfigurationSection room, List<DungeonPack> packs) {
        List<DungeonWave> waves = new ArrayList<>();
        List<Map<?, ?>> rows = room.getMapList("waves");
        if (rows.isEmpty()) {
            Map<String, Integer> fallback = new LinkedHashMap<>();
            for (DungeonPack pack : packs) {
                if (pack.trigger == DungeonPackTrigger.WAVE) {
                    fallback.put(pack.id, pack.amount);
                }
            }
            if (!fallback.isEmpty()) {
                waves.add(new DungeonWave(fallback));
            }
            return waves;
        }
        for (Map<?, ?> row : rows) {
            Map<String, Integer> spawns = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : row.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    continue;
                }
                int amount = 0;
                if (entry.getValue() instanceof Number number) {
                    amount = number.intValue();
                } else {
                    try {
                        amount = Integer.parseInt(String.valueOf(entry.getValue()));
                    } catch (NumberFormatException ignored) {
                        continue;
                    }
                }
                if (amount > 0) {
                    spawns.put(String.valueOf(entry.getKey()), amount);
                }
            }
            if (!spawns.isEmpty()) {
                waves.add(new DungeonWave(spawns));
            }
        }
        return waves;
    }

    private double leashRadius(ConfigurationSection node) {
        ConfigurationSection leash = node.getConfigurationSection("leash");
        if (leash != null) {
            return Math.max(0D, leash.getDouble("radius", this.leashRadius));
        }
        if (node.contains("leash")) {
            return Math.max(0D, node.getDouble("leash"));
        }
        return this.leashRadius;
    }

    private boolean healOnLeash(ConfigurationSection node) {
        if (node.contains("heal-on-leash")) {
            return node.getBoolean("heal-on-leash");
        }
        ConfigurationSection leash = node.getConfigurationSection("leash");
        if (leash != null && leash.contains("heal")) {
            return leash.getBoolean("heal");
        }
        return this.healOnLeash;
    }

    private DungeonIntro intro(ConfigurationSection node) {
        if (node == null) {
            return new DungeonIntro("", "", "", 10, 40, 10);
        }
        return new DungeonIntro(
                node.getString("title", ""),
                node.getString("subtitle", ""),
                node.getString("actionbar", ""),
                node.getInt("fade-in-ticks", 10),
                node.getInt("stay-ticks", 40),
                node.getInt("fade-out-ticks", 10)
        );
    }

    private DungeonIntro optionalIntro(ConfigurationSection node) {
        return node == null ? null : intro(node);
    }

    private List<String> commands(ConfigurationSection room) {
        List<String> lines = new ArrayList<>(room.getStringList("commands"));
        ConfigurationSection outro = room.getConfigurationSection("outro");
        if (outro != null) {
            lines.addAll(outro.getStringList("commands"));
        }
        return lines;
    }

    private DungeonWall wall(ConfigurationSection node) {
        if (node == null) {
            return null;
        }
        ConfigurationSection from = node.getConfigurationSection("from");
        ConfigurationSection to = node.getConfigurationSection("to");
        if (from == null || to == null) {
            return null;
        }
        List<String> faces = node.contains("close-faces")
                ? node.getStringList("close-faces")
                : node.contains("faces")
                        ? node.getStringList("faces")
                        : this.wallCloseFaces;
        return new DungeonWall(
                from.getInt("x", (int) from.getDouble("x")),
                from.getInt("y", (int) from.getDouble("y")),
                from.getInt("z", (int) from.getDouble("z")),
                to.getInt("x", (int) to.getDouble("x")),
                to.getInt("y", (int) to.getDouble("y")),
                to.getInt("z", (int) to.getDouble("z")),
                firstNonBlank(node.getString("open-block"), node.getString("set-block"), this.wallOpenBlock),
                firstNonBlank(node.getString("close-block"), this.wallCloseBlock),
                node.contains("open-on")
                        ? "start".equalsIgnoreCase(node.getString("open-on"))
                        : this.wallOpenOnStart,
                node.contains("close-on")
                        ? !"never".equalsIgnoreCase(node.getString("close-on"))
                        : this.wallCloseOnPass,
                node.contains("close-delay-ticks")
                        ? Math.max(0, node.getInt("close-delay-ticks"))
                        : this.wallCloseDelayTicks,
                node.contains("pass-margin")
                        ? Math.max(0D, node.getDouble("pass-margin"))
                        : this.wallPassMargin,
                node.contains("physics") ? node.getBoolean("physics") : this.wallPhysics,
                faces,
                node.contains("pass-detect")
                        ? Math.max(1D, node.getDouble("pass-detect"))
                        : this.wallPassDetect,
                parsePassAxis(node.getString("pass-axis", "auto"))
        );
    }

    private static int parsePassAxis(String raw) {
        if (raw == null) {
            return -1;
        }
        if (raw.equalsIgnoreCase("x")) {
            return 0;
        }
        if (raw.equalsIgnoreCase("y")) {
            return 1;
        }
        if (raw.equalsIgnoreCase("z")) {
            return 2;
        }
        return -1;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private HologramSettings hologram(ConfigurationSection dungeon) {
        ConfigurationSection node = dungeon.getConfigurationSection("hologram");
        boolean enabled = node == null
                ? this.hologramEnabled
                : node.getBoolean("enabled", this.hologramEnabled);
        int ticks = node == null
                ? this.hologramUpdateTicks
                : node.getInt("update-ticks", this.hologramUpdateTicks);
        double offsetY = node == null
                ? this.hologramOffsetY
                : node.getDouble("offset-y", this.hologramOffsetY);
        Point point = null;
        if (node != null && node.contains("x") && node.contains("y") && node.contains("z")) {
            point = point(node, 0.5, 64, 0.5);
        }
        boolean followDoor = node == null
                ? this.hologramFollowDoor
                : node.getBoolean("follow-door", this.hologramFollowDoor);
        int flyTicks = node == null
                ? this.hologramFlyTicks
                : node.getInt("fly-ticks", this.hologramFlyTicks);
        double spawnForward = node == null
                ? this.hologramSpawnForward
                : node.getDouble("spawn-forward", this.hologramSpawnForward);
        return new HologramSettings(enabled, ticks, offsetY, point, followDoor, flyTicks, spawnForward);
    }

    private Point point(ConfigurationSection section, double x, double y, double z) {
        if (section == null) {
            return new Point(x, y, z, 0, 0);
        }
        return new Point(
                section.getDouble("x", x),
                section.getDouble("y", y),
                section.getDouble("z", z),
                (float) section.getDouble("yaw", 0),
                (float) section.getDouble("pitch", 0)
        );
    }
}
