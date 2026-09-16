package com.valaryrpg.arena.dungeon;

import com.valaryrpg.arena.arena.Point;

/** Punto spawn Mythic: N mob, fino a `times` volte, trigger immediate o raggio. */
public final class DungeonPack {

    public final String id;
    public final String mob;
    public final Point point;
    public final DungeonPackTrigger trigger;
    public final double radius;
    public final int amount;
    public final int times;
    public final double scatter;
    public final int level;

    public DungeonPack(
            String id,
            String mob,
            Point point,
            DungeonPackTrigger trigger,
            double radius,
            int amount,
            int times,
            double scatter,
            int level
    ) {
        this.id = id;
        this.mob = mob == null ? "" : mob;
        this.point = point;
        this.trigger = trigger == null ? DungeonPackTrigger.IMMEDIATE : trigger;
        this.radius = Math.max(0D, radius);
        this.amount = Math.max(1, amount);
        this.times = Math.max(1, times);
        this.scatter = Math.max(0D, scatter);
        this.level = Math.max(1, level);
    }

    public int expected() {
        return this.amount * this.times;
    }

    public boolean matches(String mythicName) {
        return !this.mob.isBlank() && this.mob.equalsIgnoreCase(mythicName);
    }
}
