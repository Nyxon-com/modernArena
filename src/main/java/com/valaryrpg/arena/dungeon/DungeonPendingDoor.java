package com.valaryrpg.arena.dungeon;

/** Porta aperta: si chiude quando tutto il party vivo è oltre il piano. */
public final class DungeonPendingDoor {

    public final DungeonWall wall;
    public final double fromX;
    public final double fromY;
    public final double fromZ;
    public long closeAtMillis;

    public DungeonPendingDoor(DungeonWall wall, double fromX, double fromY, double fromZ) {
        this.wall = wall;
        this.fromX = fromX;
        this.fromY = fromY;
        this.fromZ = fromZ;
    }
}
