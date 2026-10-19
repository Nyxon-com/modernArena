package com.modern.arena.config;

import com.modern.arena.ModernArenaPlugin;
import com.modern.arena.arena.InstanceWorlds;
import org.bukkit.configuration.file.FileConfiguration;

/** Carica i YAML di prodotto in memoria. */
public final class ConfigManager {

    private final ModernArenaPlugin plugin;
    private YamlFile root;
    private YamlFile messages;
    private YamlFile storage;
    private YamlFile sounds;
    private YamlFile arenas;
    private YamlFile party;
    private YamlFile milestones;
    private YamlFile dungeons;
    private InstanceWorlds instances;

    public ConfigManager(ModernArenaPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadAll() {
        this.root = load("config.yml");
        this.storage = load("storage.yml");
        this.messages = load("lang/messages.yml");
        this.sounds = load("sounds/sounds.yml");
        this.arenas = load("modules/arenas.yml");
        this.party = load("modules/party.yml");
        this.milestones = load("modules/milestones.yml");
        this.dungeons = load("modules/dungeons.yml");
        this.instances = InstanceWorlds.load(this.root.config());
    }

    public FileConfiguration root() {
        return this.root.config();
    }

    public FileConfiguration messages() {
        return this.messages.config();
    }

    public FileConfiguration storage() {
        return this.storage.config();
    }

    public FileConfiguration sounds() {
        return this.sounds.config();
    }

    public FileConfiguration arenas() {
        return this.arenas.config();
    }

    public FileConfiguration party() {
        return this.party.config();
    }

    public FileConfiguration milestones() {
        return this.milestones.config();
    }

    public FileConfiguration dungeons() {
        return this.dungeons.config();
    }

    public String command(String key, String fallback) {
        String value = this.root.config().getString("commands." + key);
        return value == null || value.isBlank() ? fallback : value;
    }

    public String permission(String key, String fallback) {
        String value = this.root.config().getString("permissions." + key);
        return value == null || value.isBlank() ? fallback : value;
    }

    public InstanceWorlds instances() {
        return this.instances;
    }

    private YamlFile load(String fileName) {
        YamlFile yaml = new YamlFile(this.plugin, fileName);
        yaml.load();
        return yaml;
    }
}
