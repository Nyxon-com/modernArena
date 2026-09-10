package com.valaryrpg.arena.sound;

import com.valaryrpg.arena.ValaryArenaPlugin;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.Locale;

/** Suoni da sounds/sounds.yml. */
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
        String keyName = name.toLowerCase(Locale.ROOT).replace('_', '.');
        if (keyName.startsWith("minecraft.")) {
            keyName = keyName.substring("minecraft.".length());
        }
        Sound sound = Registry.SOUNDS.get(NamespacedKey.minecraft(keyName));
        if (sound == null) {
            this.plugin.getLogger().warning("Suono sconosciuto '" + name + "' in " + key);
            return;
        }
        float volume = (float) section.getDouble("volume", 1.0);
        float pitch = (float) section.getDouble("pitch", 1.0);
        player.playSound(player.getLocation(), sound, volume, pitch);
    }
}
