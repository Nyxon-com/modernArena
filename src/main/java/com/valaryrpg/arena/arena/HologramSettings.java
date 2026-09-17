package com.valaryrpg.arena.arena;

/** Impostazioni hologram cachate da YAML. */
public final class HologramSettings {

    public final boolean enabled;
    public final int updateTicks;
    public final double offsetY;
    public final Point point;
    public final boolean followDoor;
    public final int flyTicks;
    public final double spawnForward;

    public HologramSettings(boolean enabled, int updateTicks, double offsetY, Point point) {
        this(enabled, updateTicks, offsetY, point, false, 18, 0);
    }

    public HologramSettings(
            boolean enabled,
            int updateTicks,
            double offsetY,
            Point point,
            boolean followDoor,
            int flyTicks
    ) {
        this(enabled, updateTicks, offsetY, point, followDoor, flyTicks, 0);
    }

    public HologramSettings(
            boolean enabled,
            int updateTicks,
            double offsetY,
            Point point,
            boolean followDoor,
            int flyTicks,
            double spawnForward
    ) {
        this.enabled = enabled;
        this.updateTicks = Math.max(1, updateTicks);
        this.offsetY = offsetY;
        this.point = point;
        this.followDoor = followDoor;
        this.flyTicks = Math.max(1, Math.min(59, flyTicks));
        this.spawnForward = Math.max(0D, spawnForward);
    }
}
