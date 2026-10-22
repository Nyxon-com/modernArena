//made with love by nyxon

package com.modern.arena.dungeon;

import com.modern.arena.arena.Point;

import java.util.List;

public final class DungeonRoom {

  public final String id;
  public final String displayName;
  public final DungeonObjective objective;
  public final Point spawn;
  public final DungeonIntro intro;
  public final DungeonIntro outro;
  public final DungeonWall wall;
  public final List<DungeonWall> walls;
  public final String bossMob;
  public final List<DungeonPack> packs;
  public final List<DungeonWave> waves;
  public final List<String> commands;
  public final boolean startOnRadius;
  public final double startRadius;
  public final Point startPoint;

  public DungeonRoom(
      String id,
      String displayName,
      DungeonObjective objective,
      Point spawn,
      DungeonIntro intro,
      DungeonIntro outro,
      List<DungeonWall> walls,
      String bossMob,
      List<DungeonPack> packs,
      List<DungeonWave> waves,
      List<String> commands,
      boolean startOnRadius,
      double startRadius,
      Point startPoint
  ) {
    this.id = id;
    this.displayName = displayName == null || displayName.isBlank() ? id : displayName;
    this.objective = objective == null ? DungeonObjective.CLEAR_MOBS : objective;
    this.spawn = spawn;
    this.intro = intro;
    this.outro = outro;
    this.walls = walls == null || walls.isEmpty() ? List.of() : List.copyOf(walls);
    this.wall = this.walls.isEmpty() ? null : this.walls.getFirst();
    this.bossMob = bossMob == null ? "" : bossMob;
    this.packs = List.copyOf(packs);
    this.waves = waves == null ? List.of() : List.copyOf(waves);
    this.commands = commands == null ? List.of() : List.copyOf(commands);
    this.startOnRadius = startOnRadius;
    this.startRadius = Math.max(1D, startRadius);
    this.startPoint = startPoint;
  }

  public boolean matchesMob(String mythicName) {
    if (mythicName == null || mythicName.isBlank()) {
      return false;
    }
    for (DungeonPack pack : this.packs) {
      if (pack.matches(mythicName)) {
        return true;
      }
    }
    return this.bossMob != null && !this.bossMob.isBlank() && this.bossMob.equalsIgnoreCase(mythicName);
  }

  public boolean isBoss(String mythicName) {
    if (this.objective != DungeonObjective.BOSS || mythicName == null || mythicName.isBlank()) {
      return false;
    }
    if (!this.bossMob.isBlank()) {
      return this.bossMob.equalsIgnoreCase(mythicName);
    }
    return matchesMob(mythicName);
  }

  public int expectedMobs() {
    int total = 0;
    if (!this.waves.isEmpty()) {
      for (DungeonWave wave : this.waves) {
        total += wave.total();
      }
      for (DungeonPack pack : this.packs) {
        if (pack.trigger != DungeonPackTrigger.WAVE) {
          total += pack.expected();
        }
      }
      return total;
    }
    for (DungeonPack pack : this.packs) {
      total += pack.expected();
    }
    return total;
  }

  public boolean hasRadiusPacks() {
    for (DungeonPack pack : this.packs) {
      if (pack.trigger == DungeonPackTrigger.RADIUS) {
        return true;
      }
    }
    return false;
  }

  public boolean hasLeash() {
    for (DungeonPack pack : this.packs) {
      if (pack.leashed()) {
        return true;
      }
    }
    return false;
  }

  public boolean hasWaves() {
    return !this.waves.isEmpty();
  }

  public boolean hasStartDoors() {
    for (DungeonWall wall : this.walls) {
      if (wall.openOnStart) {
        return true;
      }
    }
    return false;
  }

  public boolean hasClosingDoors() {
    for (DungeonWall wall : this.walls) {
      if (wall.closes()) {
        return true;
      }
    }
    return false;
  }

  public DungeonPack pack(String id) {
    if (id == null) {
      return null;
    }
    for (DungeonPack pack : this.packs) {
      if (pack.id.equals(id)) {
        return pack;
      }
    }
    return null;
  }
}
