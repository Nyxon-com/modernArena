package com.valaryrpg.arena.arena;

/** Impostazioni hologram cachate da modules/arenas.yml. */
public final class HologramSettings {

    public final boolean enabled;
    public final int updateTicks;
    public final double offsetY;
    public final Point point;

    public HologramSettings(boolean enabled, int updateTicks, double offsetY, Point point) {
        this.enabled = enabled;
        this.updateTicks = Math.max(1, updateTicks);
        this.offsetY = offsetY;
        this.point = point;
    }
}
