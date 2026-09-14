package com.valaryrpg.arena.milestone;

import com.valaryrpg.arena.ValaryArenaPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Cache soglie per mob id. */
public final class MilestoneCatalog {

    private final ValaryArenaPlugin plugin;
    private final Map<String, List<MilestoneDefinition>> byMob = new HashMap<>();
    private final Map<String, String> canonical = new HashMap<>();

    public MilestoneCatalog(ValaryArenaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        this.byMob.clear();
        this.canonical.clear();
        FileConfiguration yaml = this.plugin.configs().milestones();
        ConfigurationSection mobs = yaml.getConfigurationSection("mobs");
        if (mobs == null) {
            return;
        }
        for (String mobId : mobs.getKeys(false)) {
            ConfigurationSection thresholds = mobs.getConfigurationSection(mobId);
            if (thresholds == null) {
                continue;
            }
            List<MilestoneDefinition> list = new ArrayList<>();
            for (String key : thresholds.getKeys(false)) {
                ConfigurationSection node = thresholds.getConfigurationSection(key);
                if (node == null) {
                    continue;
                }
                int threshold;
                try {
                    threshold = Integer.parseInt(key);
                } catch (NumberFormatException exception) {
                    continue;
                }
                list.add(new MilestoneDefinition(
                        threshold,
                        node.getString("message", ""),
                        node.getString("sound", "milestone"),
                        node.getStringList("commands")
                ));
            }
            list.sort(Comparator.comparingInt(definition -> definition.threshold));
            String key = mobId.toLowerCase(Locale.ROOT);
            this.byMob.put(key, List.copyOf(list));
            this.canonical.put(key, mobId);
        }
    }

    public List<MilestoneDefinition> of(String mobId) {
        if (mobId == null || mobId.isBlank()) {
            return List.of();
        }
        return this.byMob.getOrDefault(mobId.toLowerCase(Locale.ROOT), List.of());
    }

    public String canonical(String mobId) {
        if (mobId == null || mobId.isBlank()) {
            return "";
        }
        return this.canonical.getOrDefault(mobId.toLowerCase(Locale.ROOT), mobId);
    }

    public Collection<String> mobIds() {
        return List.copyOf(this.canonical.values());
    }
}
