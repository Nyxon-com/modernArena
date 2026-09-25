package com.valaryrpg.arena.dungeon;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.List;

/** Cubo porta: `open-block` all’apertura, `close-block` quando il party ha superato il piano. */
public final class DungeonWall {

    public final int minX;
    public final int minY;
    public final int minZ;
    public final int maxX;
    public final int maxY;
    public final int maxZ;
    public final String setBlock;
    public final String closeBlock;
    public final boolean openOnStart;
    public final boolean closeOnPass;
    public final int closeDelayTicks;
    public final double passMargin;
    public final boolean physics;
    public final List<String> closeFaces;
    public final double passDetect;
    public final int passAxis;
    public final boolean spawnOnClose;

    public DungeonWall(
            int x1,
            int y1,
            int z1,
            int x2,
            int y2,
            int z2,
            String setBlock,
            String closeBlock,
            boolean openOnStart,
            boolean closeOnPass,
            int closeDelayTicks,
            double passMargin,
            boolean physics,
            List<String> closeFaces,
            double passDetect,
            int passAxis,
            boolean spawnOnClose
    ) {
        this.minX = Math.min(x1, x2);
        this.minY = Math.min(y1, y2);
        this.minZ = Math.min(z1, z2);
        this.maxX = Math.max(x1, x2);
        this.maxY = Math.max(y1, y2);
        this.maxZ = Math.max(z1, z2);
        this.setBlock = setBlock == null || setBlock.isBlank() ? "air" : setBlock;
        this.closeBlock = closeBlock == null ? "" : closeBlock;
        this.openOnStart = openOnStart;
        this.closeOnPass = closeOnPass;
        this.closeDelayTicks = Math.max(0, closeDelayTicks);
        this.passMargin = Math.max(0D, passMargin);
        this.physics = physics;
        this.closeFaces = closeFaces == null ? List.of() : List.copyOf(closeFaces);
        this.passDetect = Math.max(1D, passDetect);
        this.passAxis = passAxis;
        this.spawnOnClose = spawnOnClose;
    }

    public boolean closes() {
        return this.closeOnPass && !this.closeBlock.isBlank();
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
