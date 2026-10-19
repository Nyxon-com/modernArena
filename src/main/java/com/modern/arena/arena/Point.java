package com.modern.arena.arena;

import org.bukkit.Location;
import org.bukkit.World;

/** Coordinate spawn/spawner senza mondo (il mondo è l'istanza). */
public final class Point {

    public final double x;
    public final double y;
    public final double z;
    public final float yaw;
    public final float pitch;

    public Point(double x, double y, double z, float yaw, float pitch) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public Location to(World world) {
        return new Location(world, this.x, this.y, this.z, this.yaw, this.pitch);
    }
}
