package com.valaryrpg.arena.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** YAML su disco, creato dal jar se manca. */
public final class YamlFile {

    private final JavaPlugin plugin;
    private final String path;
    private final File file;
    private YamlConfiguration config;

    public YamlFile(JavaPlugin plugin, String path) {
        this.plugin = plugin;
        this.path = path;
        this.file = new File(plugin.getDataFolder(), path);
    }

    public void load() {
        if (!this.file.exists()) {
            this.file.getParentFile().mkdirs();
            this.plugin.saveResource(this.path, false);
        }
        this.config = YamlConfiguration.loadConfiguration(this.file);
        try (InputStream input = this.plugin.getResource(this.path)) {
            if (input != null) {
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(input, StandardCharsets.UTF_8));
                this.config.setDefaults(defaults);
            }
        } catch (IOException ignored) {
            // defaults dal jar non disponibili
        }
    }

    public FileConfiguration config() {
        return this.config;
    }

    public void save() {
        try {
            this.config.save(this.file);
        } catch (IOException exception) {
            this.plugin.getLogger().severe("Salvataggio fallito: " + this.path + " — " + exception.getMessage());
        }
    }
}
