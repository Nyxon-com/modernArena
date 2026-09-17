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
    private int proximityCheckTicks = 10;

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
        this.proximityCheckTicks = Math.max(5, yaml.getInt("settings.proximity-check-ticks", 10));

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
            rooms.add(new DungeonRoom(
                    id,
                    objective,
                    node.isConfigurationSection("spawn") ? point(node.getConfigurationSection("spawn"), 0.5, 64, 0.5) : null,
                    intro(node.getConfigurationSection("intro")),
                    optionalIntro(node.getConfigurationSection("outro")),
                    wall(node.getConfigurationSection("wall")),
                    bossMob,
                    packs,
                    commands(node)
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
                    node.getInt("level", 1)
            ));
        }
        return packs;
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
        return new DungeonWall(
                from.getInt("x", (int) from.getDouble("x")),
                from.getInt("y", (int) from.getDouble("y")),
                from.getInt("z", (int) from.getDouble("z")),
                to.getInt("x", (int) to.getDouble("x")),
                to.getInt("y", (int) to.getDouble("y")),
                to.getInt("z", (int) to.getDouble("z")),
                node.getString("set-block", "air")
        );
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
        return new HologramSettings(enabled, ticks, offsetY, point);
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
