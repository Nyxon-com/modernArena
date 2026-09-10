package com.valaryrpg.arena.milestone;

import com.valaryrpg.arena.ValaryArenaPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Cache soglie per mob id. */
public final class MilestoneCatalog {

    private final ValaryArenaPlugin plugin;
    private final Map<String, List<MilestoneDefinition>> byMob = new HashMap<>();

    public MilestoneCatalog(ValaryArenaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        this.byMob.clear();
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
            this.byMob.put(mobId, List.copyOf(list));
        }
    }

    public List<MilestoneDefinition> of(String mobId) {
        return this.byMob.getOrDefault(mobId, List.of());
    }
}
