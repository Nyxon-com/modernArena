//made with love by nyxon

package com.modern.arena.dungeon;

public enum DungeonObjective {
  CLEAR_MOBS,
  BOSS;

  public static DungeonObjective parse(String raw) {
    if (raw != null && raw.equalsIgnoreCase("boss")) {
      return BOSS;
    }
    return CLEAR_MOBS;
  }
}
