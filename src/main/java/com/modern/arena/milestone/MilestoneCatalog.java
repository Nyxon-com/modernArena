package com.modern.arena.milestone;

import com.modern.arena.ModernArenaPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class MilestoneCatalog {

  private final ModernArenaPlugin plugin;
  private final Map<String, List<MilestoneDefinition>> byMob = new HashMap<>();
  private final Map<String, String> canonical = new HashMap<>();
  private int claimRevision;

  public MilestoneCatalog(ModernArenaPlugin plugin) {
    this.plugin = plugin;
  }

  public void load() {
    this.byMob.clear();
    this.canonical.clear();
    FileConfiguration yaml = this.plugin.configs().milestones();
    this.claimRevision = Math.max(1, yaml.getInt("settings.claim-revision", 1));
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
            node.getStringList("commands"),
            node.getStringList("random-commands")
        ));
      }
      list.sort(Comparator.comparingInt(definition -> definition.threshold));
      String key = mobId.toLowerCase(Locale.ROOT);
      this.byMob.put(key, List.copyOf(list));
      this.canonical.put(key, mobId);
    }
  }

  public int claimRevision() {
    return this.claimRevision;
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
