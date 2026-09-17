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
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.logging.Logger;

/** Crea e toglie spawners Mythic solo nel mondo istanza. */
public final class MythicSpawners {

    private final Logger logger;

    public MythicSpawners(JavaPlugin plugin) {
        this.logger = plugin.getLogger();
    }

    public int spawn(String mob, Location location, int amount, double scatter, int level) {
        return spawnLiving(mob, location, amount, scatter, level).size();
    }

    public List<LivingEntity> spawnLiving(String mob, Location location, int amount, double scatter, int level) {
        List<LivingEntity> spawned = new ArrayList<>();
        if (mob == null || mob.isBlank() || location == null || amount <= 0) {
            return spawned;
        }
        var api = MythicBukkit.inst().getAPIHelper();
        for (int index = 0; index < amount; index++) {
            Location at = location.clone();
            if (scatter > 0D) {
                at.add((Math.random() - 0.5D) * 2D * scatter, 0D, (Math.random() - 0.5D) * 2D * scatter);
            }
            try {
                Entity entity = api.spawnMythicMob(mob, at, Math.max(1, level));
                if (entity instanceof LivingEntity living) {
                    spawned.add(living);
                }
            } catch (Exception exception) {
                this.logger.warning("Spawn Mythic fallito: " + mob + " — " + exception.getMessage());
                break;
            }
        }
        return spawned;
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

    public int waitSeconds(List<String> names) {
        SpawnerManager manager = MythicBukkit.inst().getSpawnerManager();
        int wait = 0;
        for (String name : names) {
            MythicSpawner spawner = manager.getSpawnerByName(name);
            if (spawner == null) {
                continue;
            }
            wait = Math.max(wait, spawner.getRemainingWarmupSeconds());
            wait = Math.max(wait, spawner.getRemainingCooldownSeconds());
        }
        return wait;
    }

    public void remove(String name) {
        SpawnerManager manager = MythicBukkit.inst().getSpawnerManager();
        MythicSpawner match = manager.getSpawnerByName(name);
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
        for (Entity entity : world.getEntities()) {
            if (entity instanceof Player) {
                continue;
            }
            ActiveMob mob = activeOf(entity);
            if (mob != null) {
                mob.remove();
            }
            entity.remove();
        }
    }

    public ActiveMob activeOf(Entity entity) {
        if (entity == null) {
            return null;
        }
        return MythicBukkit.inst().getMobManager().getActiveMob(entity.getUniqueId()).orElse(null);
    }

    public int countInWorld(World world, Predicate<String> mythicName) {
        if (world == null || mythicName == null) {
            return 0;
        }
        int alive = 0;
        for (LivingEntity entity : world.getLivingEntities()) {
            if (entity.isDead()) {
                continue;
            }
            ActiveMob mob = activeOf(entity);
            if (mob == null || mob.getType() == null) {
                continue;
            }
            if (mythicName.test(mob.getType().getInternalName())) {
                alive++;
            }
        }
        return alive;
    }
}
