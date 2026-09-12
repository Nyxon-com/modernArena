package com.valaryrpg.arena.arena;

import io.lumine.mythic.api.skills.placeholders.PlaceholderInt;
import io.lumine.mythic.bukkit.BukkitAdapter;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.mobs.ActiveMob;
import io.lumine.mythic.core.spawning.spawners.MythicSpawner;
import io.lumine.mythic.core.spawning.spawners.SpawnerManager;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

/** Crea e toglie spawners Mythic solo nel mondo istanza. */
public final class MythicSpawners {

    private final Logger logger;

    public MythicSpawners(JavaPlugin plugin) {
        this.logger = plugin.getLogger();
    }

    public String create(String name, Location location, SpawnerDefinition definition) {
        SpawnerManager manager = MythicBukkit.inst().getSpawnerManager();
        remove(name);
        if (definition.sourceSpawner != null && !definition.sourceSpawner.isBlank()) {
            boolean copied = manager.copySpawner(
                    definition.sourceSpawner,
                    name,
                    BukkitAdapter.adapt(location)
            );
            if (!copied) {
                this.logger.warning("Spawner Mythic sorgente non trovato: " + definition.sourceSpawner);
                return null;
            }
            return name;
        }
        MythicSpawner spawner = manager.createSpawner(name, location, definition.mob);
        if (spawner == null) {
            this.logger.warning("Spawner Mythic non creato: " + name + " (" + definition.mob + ")");
            return null;
        }
        spawner.setCooldownSeconds(definition.cooldownSeconds);
        spawner.setMaxMobs(PlaceholderInt.of(Integer.toString(definition.maxMobs)));
        spawner.setSpawnRadius(definition.spawnRadius);
        return name;
    }

    public void remove(String name) {
        SpawnerManager manager = MythicBukkit.inst().getSpawnerManager();
        MythicSpawner match = null;
        for (MythicSpawner spawner : manager.getSpawners()) {
            if (name.equalsIgnoreCase(spawner.getName())) {
                match = spawner;
                break;
            }
        }
        if (match != null) {
            manager.removeSpawner(match);
        }
    }

    public void removePrefix(String prefix) {
        SpawnerManager manager = MythicBukkit.inst().getSpawnerManager();
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<MythicSpawner> snapshot = new ArrayList<>(manager.getSpawners());
        for (MythicSpawner spawner : snapshot) {
            String name = spawner.getName();
            if (name != null && name.toLowerCase(Locale.ROOT).startsWith(lower)) {
                manager.removeSpawner(spawner);
            }
        }
    }

    public void despawnWorld(World world) {
        for (ActiveMob mob : new ArrayList<>(MythicBukkit.inst().getMobManager().getActiveMobs())) {
            Entity entity = bukkitEntity(mob);
            if (entity == null || !entity.getWorld().equals(world)) {
                continue;
            }
            mob.remove();
            entity.remove();
        }
        for (Entity entity : world.getEntities()) {
            if (!(entity instanceof Player)) {
                entity.remove();
            }
        }
    }

    private Entity bukkitEntity(ActiveMob mob) {
        if (mob.getEntity() == null) {
            return null;
        }
        return mob.getEntity().getBukkitEntity();
    }
}
