package com.valaryrpg.arena.dungeon;

import org.bukkit.Location;
import org.bukkit.World;

/** Cubo da fillare al clear della stanza. */
public final class DungeonWall {

    public final int minX;
    public final int minY;
    public final int minZ;
    public final int maxX;
    public final int maxY;
    public final int maxZ;
    public final String setBlock;

    public DungeonWall(int x1, int y1, int z1, int x2, int y2, int z2, String setBlock) {
        this.minX = Math.min(x1, x2);
        this.minY = Math.min(y1, y2);
        this.minZ = Math.min(z1, z2);
        this.maxX = Math.max(x1, x2);
        this.maxY = Math.max(y1, y2);
        this.maxZ = Math.max(z1, z2);
        this.setBlock = setBlock == null || setBlock.isBlank() ? "air" : setBlock;
    }

    public Location center(World world) {
        return new Location(
                world,
                (this.minX + this.maxX) / 2.0 + 0.5,
                (this.minY + this.maxY) / 2.0 + 0.5,
                (this.minZ + this.maxZ) / 2.0 + 0.5
        );
    }
}
