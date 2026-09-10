package com.valaryrpg.arena.arena;

import com.valaryrpg.arena.party.Party;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Coda RAM per boss: enqueue allo start, dequeue sull'unload. */
public final class ArenaQueue {

    private final Map<String, Deque<Party>> queues = new ConcurrentHashMap<>();
    private final Map<UUID, String> partyBoss = new ConcurrentHashMap<>();

    public int enqueue(String bossId, Party party, int max) {
        Deque<Party> queue = this.queues.computeIfAbsent(bossId, key -> new ArrayDeque<>());
        if (queue.size() >= max) {
            return -1;
        }
        if (this.partyBoss.containsKey(party.id())) {
            return -1;
        }
        queue.addLast(party);
        this.partyBoss.put(party.id(), bossId);
        return queue.size();
    }

    public Party dequeue(String bossId) {
        Deque<Party> queue = this.queues.get(bossId);
        if (queue == null) {
            return null;
        }
        Party party = queue.pollFirst();
        if (party != null) {
            this.partyBoss.remove(party.id());
        }
        return party;
    }

    public boolean remove(Party party) {
        String bossId = this.partyBoss.remove(party.id());
        if (bossId == null) {
            return false;
        }
        Deque<Party> queue = this.queues.get(bossId);
        if (queue == null) {
            return false;
        }
        return queue.remove(party);
    }

    public boolean contains(Party party) {
        return party != null && this.partyBoss.containsKey(party.id());
    }

    public String bossOf(Party party) {
        return party == null ? null : this.partyBoss.get(party.id());
    }

    public void clear() {
        this.queues.clear();
        this.partyBoss.clear();
    }
}
