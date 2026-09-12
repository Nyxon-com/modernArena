package com.valaryrpg.arena.config;

import com.valaryrpg.arena.ValaryArenaPlugin;
import org.bukkit.configuration.file.FileConfiguration;

/** Carica i YAML di prodotto in memoria. */
public final class ConfigManager {

    private final ValaryArenaPlugin plugin;
    private YamlFile root;
    private YamlFile messages;
    private YamlFile storage;
    private YamlFile sounds;
    private YamlFile arenas;
    private YamlFile party;
    private YamlFile milestones;

    public ConfigManager(ValaryArenaPlugin plugin) {
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

    public String command(String key, String fallback) {
        String value = this.root.config().getString("commands." + key);
        return value == null || value.isBlank() ? fallback : value;
    }

    public String permission(String key, String fallback) {
        String value = this.root.config().getString("permissions." + key);
        return value == null || value.isBlank() ? fallback : value;
    }

    private YamlFile load(String fileName) {
        YamlFile yaml = new YamlFile(this.plugin, fileName);
        yaml.load();
        return yaml;
    }
}
