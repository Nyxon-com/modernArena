package com.modern.arena.arena;

import com.modern.arena.util.Tasks;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class InstanceWorlds {

  public final int maxLoaded;
  public final int copyMaxConcurrent;
  public final boolean keepSpawnInMemory;
  public final int viewDistance;
  public final int simulationDistance;
  public final int randomTickSpeed;
  public final boolean vanillaMobSpawning;

  public InstanceWorlds(
      int maxLoaded,
      int copyMaxConcurrent,
      boolean keepSpawnInMemory,
      int viewDistance,
      int simulationDistance,
      int randomTickSpeed,
      boolean vanillaMobSpawning
  ) {
    this.maxLoaded = Math.max(0, maxLoaded);
    this.copyMaxConcurrent = Math.max(1, copyMaxConcurrent);
    this.keepSpawnInMemory = keepSpawnInMemory;
    this.viewDistance = Math.max(2, Math.min(10, viewDistance));
    this.simulationDistance = Math.max(2, Math.min(this.viewDistance, simulationDistance));
    this.randomTickSpeed = Math.max(0, randomTickSpeed);
    this.vanillaMobSpawning = vanillaMobSpawning;
  }

  public static InstanceWorlds load(FileConfiguration yaml) {
    return new InstanceWorlds(
        yaml.getInt("instances.max-loaded", 16),
        yaml.getInt("instances.copy-max-concurrent", 1),
        yaml.getBoolean("instances.world.keep-spawn-in-memory", false),
        yaml.getInt("instances.world.view-distance", 4),
        yaml.getInt("instances.world.simulation-distance", 2),
        yaml.getInt("instances.world.random-tick-speed", 0),
        yaml.getBoolean("instances.world.vanilla-mob-spawning", false)
    );
  }

  public void tune(World world) {
    if (world == null) {
      return;
    }
    world.setAutoSave(false);
    world.setKeepSpawnInMemory(this.keepSpawnInMemory);
    world.setViewDistance(this.viewDistance);
    world.setSimulationDistance(this.simulationDistance);

    world.setPVP(false);
    world.setGameRule(GameRule.RANDOM_TICK_SPEED, this.randomTickSpeed);
    world.setGameRule(GameRule.DO_MOB_SPAWNING, this.vanillaMobSpawning);
    world.setGameRule(GameRule.DO_PATROL_SPAWNING, false);
    world.setGameRule(GameRule.DO_WARDEN_SPAWNING, false);
    world.setGameRule(GameRule.DO_TRADER_SPAWNING, false);
    world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
    world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
    world.setGameRule(GameRule.DO_FIRE_TICK, false);
    world.setSpawnFlags(this.vanillaMobSpawning, this.vanillaMobSpawning);
  }

  public void prepareSpawn(World world, Location spawn, Tasks tasks, Runnable onReady) {
    if (world == null || spawn == null || tasks == null || onReady == null) {
      return;
    }
    int cx = spawn.getBlockX() >> 4;
    int cz = spawn.getBlockZ() >> 4;
    List<CompletableFuture<?>> loads = new ArrayList<>();
    for (int dx = -1; dx <= 1; dx++) {
      for (int dz = -1; dz <= 1; dz++) {
        loads.add(world.getChunkAtAsync(cx + dx, cz + dz));
      }
    }
    CompletableFuture.allOf(loads.toArray(CompletableFuture[]::new))
        .whenComplete((ignored, error) -> tasks.sync(onReady));
  }
}
