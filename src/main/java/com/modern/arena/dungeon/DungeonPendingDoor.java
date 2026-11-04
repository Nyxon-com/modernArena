package com.modern.arena.dungeon;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class DungeonPendingDoor {

  public final DungeonWall wall;
  public long closeAtMillis;

  public final Map<UUID, Boolean> lastPositive = new HashMap<>();

  public final Map<UUID, Boolean> origin = new HashMap<>();
  public final Set<UUID> crossed = new HashSet<>();

  public DungeonPendingDoor(DungeonWall wall) {
    this.wall = wall;
  }
}
