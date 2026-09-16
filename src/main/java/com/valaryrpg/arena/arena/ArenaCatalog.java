package com.valaryrpg.arena.arena;

import com.valaryrpg.arena.ValaryArenaPlugin;
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

/** Cache in RAM di settings, boss e spawners. */
public final class ArenaCatalog {

    private final ValaryArenaPlugin plugin;
    private final Map<String, ArenaDefinition> bosses = new LinkedHashMap<>();
    private String worldPrefix = "arena_";
    private String worldNameFormat = "arena_{boss}_{slot}";
    private String spawnerNameFormat = "arena_{boss}_{slot}_{spawner}";
    private String instancesDirectory = "instances";
    private int maxPerBoss = 4;
    private int maxGlobal;
    private int queueMax = 20;
    private List<String> copyExclude = List.of("uid.dat", "session.lock");
    private String leaveCommand = "spawn";
    private String leaveWorld = "world";
    private Point leavePoint = new Point(0.5, 64, 0.5, 0, 0);
    private int maxBosses;
    private int entryCooldownSeconds;
    private int betweenCooldownSeconds;
    private int bossCapLeaveTicks = 20;
    private boolean hologramEnabled = true;
    private int hologramUpdateTicks = 40;
    private double hologramOffsetY = 2.5;

    public ArenaCatalog(ValaryArenaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        this.bosses.clear();
        FileConfiguration yaml = this.plugin.configs().arenas();
        this.worldPrefix = yaml.getString("settings.world-prefix", "arena_");
        this.worldNameFormat = yaml.getString("settings.world-name-format", "arena_{boss}_{slot}");
        this.spawnerNameFormat = yaml.getString("settings.spawner-name-format", "arena_{boss}_{slot}_{spawner}");
        this.instancesDirectory = yaml.getString("settings.instances-directory", "instances");
        this.maxPerBoss = yaml.getInt("settings.max-instances-per-boss", 4);
        this.maxGlobal = yaml.getInt("settings.max-instances-global", 0);
        this.copyExclude = yaml.getStringList("settings.copy-exclude");
        if (this.copyExclude.isEmpty()) {
            this.copyExclude = List.of("uid.dat", "session.lock");
        }
        this.queueMax = yaml.getInt("queue.max-size", 20);
        this.leaveCommand = yaml.getString("leave.command", "spawn");
        this.leaveWorld = yaml.getString("leave.location.world", "world");
        this.leavePoint = point(yaml.getConfigurationSection("leave.location"), 0.5, 64, 0.5);
        this.maxBosses = yaml.getInt("settings.max-bosses", 0);
        this.entryCooldownSeconds = yaml.getInt("settings.entry-cooldown-seconds", 0);
        this.betweenCooldownSeconds = yaml.getInt("settings.between-cooldown-seconds", 0);
        this.bossCapLeaveTicks = Math.max(1, yaml.getInt("settings.boss-cap-leave-ticks", 20));
        this.hologramEnabled = yaml.getBoolean("settings.hologram.enabled", true);
        this.hologramUpdateTicks = yaml.getInt("settings.hologram.update-ticks", 40);
        this.hologramOffsetY = yaml.getDouble("settings.hologram.offset-y", 2.5);

        ConfigurationSection section = yaml.getConfigurationSection("bosses");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection boss = section.getConfigurationSection(id);
            if (boss == null) {
                continue;
            }
            List<SpawnerDefinition> spawners = new ArrayList<>();
            ConfigurationSection spawnerSection = boss.getConfigurationSection("spawners");
            if (spawnerSection != null) {
                for (String spawnerId : spawnerSection.getKeys(false)) {
                    ConfigurationSection node = spawnerSection.getConfigurationSection(spawnerId);
                    if (node == null) {
                        continue;
                    }
                    spawners.add(new SpawnerDefinition(
                            spawnerId,
                            node.getString("source-spawner", ""),
                            node.getString("mob", spawnerId),
                            point(node, 0, 64, 0),
                            node.getInt("cooldown-seconds", 30),
                            node.getInt("max-mobs", 1),
                            node.getInt("spawn-radius", 0)
                    ));
                }
            }
            this.bosses.put(id, new ArenaDefinition(
                    id,
                    boss.getString("display-name", id),
                    boss.getString("template", ""),
                    boss.getString("permission", ""),
                    boss.getInt("max-party-size", 4),
                    boss.getInt("duration-seconds", 0),
                    boss.contains("max-bosses") ? boss.getInt("max-bosses") : this.maxBosses,
                    boss.contains("entry-cooldown-seconds")
                            ? boss.getInt("entry-cooldown-seconds")
                            : this.entryCooldownSeconds,
                    boss.contains("between-cooldown-seconds")
                            ? boss.getInt("between-cooldown-seconds")
                            : this.betweenCooldownSeconds,
                    hologram(boss),
                    point(boss.getConfigurationSection("spawn"), 0.5, 64, 0.5),
                    spawners
            ));
        }
    }

    public ArenaDefinition boss(String id) {
        return this.bosses.get(id);
    }

    public Collection<ArenaDefinition> bosses() {
        return this.bosses.values();
    }

    public Collection<String> bossIds() {
        return this.bosses.keySet();
    }

    public boolean tracksMob(ArenaDefinition definition, String mythicName) {
        if (definition == null || mythicName == null || mythicName.isBlank()) {
            return false;
        }
        if (definition.id.equalsIgnoreCase(mythicName)) {
            return true;
        }
        for (SpawnerDefinition spawner : definition.spawners) {
            if (spawner.mob != null && !spawner.mob.isBlank() && spawner.mob.equalsIgnoreCase(mythicName)) {
                return true;
            }
        }
        return false;
    }

    public String worldPrefix() {
        return this.worldPrefix;
    }

    public String worldName(String boss, int slot) {
        return this.worldNameFormat
                .replace("{boss}", boss)
                .replace("{slot}", Integer.toString(slot));
    }

    public String spawnerName(String boss, int slot, String spawner) {
        return this.spawnerNameFormat
                .replace("{boss}", boss)
                .replace("{slot}", Integer.toString(slot))
                .replace("{spawner}", spawner);
    }

    public String instancesDirectory() {
        return this.instancesDirectory;
    }

    public int maxPerBoss() {
        return this.maxPerBoss;
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

    public int bossCapLeaveTicks() {
        return this.bossCapLeaveTicks;
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

    private HologramSettings hologram(ConfigurationSection boss) {
        ConfigurationSection node = boss.getConfigurationSection("hologram");
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
