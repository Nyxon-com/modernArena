package com.valaryrpg.arena.sound;

import com.valaryrpg.arena.ValaryArenaPlugin;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.Locale;

/** Suoni da sounds/sounds.yml (resource key Minecraft, es. block.note_block.chime). */
public final class SoundService {

    private final ValaryArenaPlugin plugin;

    public SoundService(ValaryArenaPlugin plugin) {
        this.plugin = plugin;
    }

    public void play(Player player, String key) {
        ConfigurationSection section = this.plugin.configs().sounds().getConfigurationSection(key);
        if (section == null || !section.getBoolean("enabled", true)) {
            return;
        }
        String name = section.getString("sound");
        if (name == null || name.isEmpty()) {
            return;
        }
        float volume = (float) section.getDouble("volume", 1.0);
        float pitch = (float) section.getDouble("pitch", 1.0);
        String resourceKey = toResourceKey(name);
        Sound sound = Registry.SOUNDS.get(NamespacedKey.minecraft(resourceKey));
        if (sound != null) {
            player.playSound(player.getLocation(), sound, volume, pitch);
            return;
        }
        try {
            player.playSound(player.getLocation(), resourceKey, volume, pitch);
        } catch (Exception exception) {
            this.plugin.getLogger().warning("Suono sconosciuto '" + name + "' in " + key);
        }
    }

    private static String toResourceKey(String raw) {
        String keyName = raw.trim().toLowerCase(Locale.ROOT);
        if (keyName.startsWith("minecraft:")) {
            return keyName.substring("minecraft:".length());
        }
        if (keyName.startsWith("minecraft.")) {
            return keyName.substring("minecraft.".length());
        }
        // Resource key già corretto: non convertire underscore (note_block, cast_spell, …)
        if (keyName.indexOf('.') >= 0) {
            return keyName;
        }
        // Legacy ENUM_STYLE → conversione naive (meglio usare resource key nel yml)
        return keyName.replace('-', '_').replace('_', '.');
    }
}
