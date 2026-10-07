package com.valaryrpg.arena.dungeon;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Porta aperta: si chiude quando i vivi in istanza hanno attraversato il piano. */
public final class DungeonPendingDoor {

    public final DungeonWall wall;
    public long closeAtMillis;
    /** Ultimo lato "deciso" (fuori dal vano porta + pass-margin) per giocatore. */
    public final Map<UUID, Boolean> lastPositive = new HashMap<>();
    /** Lato da cui il giocatore e' arrivato (prima osservazione, rispetto al piano centrale). */
    public final Map<UUID, Boolean> origin = new HashMap<>();
    public final Set<UUID> crossed = new HashSet<>();

    public DungeonPendingDoor(DungeonWall wall) {
        this.wall = wall;
    }
}
