package com.modern.arena.arena;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Cooldown RAM: rientro stessa arena e cambio arena. */
public final class ArenaCooldowns {

    private final Map<UUID, Stamp> lastLeave = new ConcurrentHashMap<>();

    public void mark(UUID uuid, String bossId) {
        if (uuid == null || bossId == null || bossId.isBlank()) {
            return;
        }
        this.lastLeave.put(uuid, new Stamp(bossId, System.currentTimeMillis()));
    }

    public Remaining remaining(UUID uuid, String nextBoss, int entrySeconds, int betweenSeconds) {
        Stamp last = this.lastLeave.get(uuid);
        if (last == null) {
            return Remaining.none();
        }
        boolean same = last.bossId.equalsIgnoreCase(nextBoss);
        int need = same ? entrySeconds : (betweenSeconds > 0 ? betweenSeconds : entrySeconds);
        if (need <= 0) {
            return Remaining.none();
        }
        long waitMillis = need * 1000L - (System.currentTimeMillis() - last.at);
        if (waitMillis <= 0L) {
            return Remaining.none();
        }
        long seconds = (waitMillis + 999L) / 1000L;
        return new Remaining(
                same ? "errors.entry-cooldown" : "errors.between-cooldown",
                seconds
        );
    }

    public void clear() {
        this.lastLeave.clear();
    }

    public record Remaining(String messagePath, long seconds) {
        public boolean blocked() {
            return this.messagePath != null && this.seconds > 0L;
        }

        private static Remaining none() {
            return new Remaining(null, 0L);
        }
    }

    private record Stamp(String bossId, long at) {
    }
}
